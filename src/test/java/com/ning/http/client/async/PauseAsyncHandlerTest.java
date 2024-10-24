/*
 * Copyright (c) 2010-2012 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.ning.http.client.async;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import com.ning.http.client.providers.grizzly.PauseHandler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.testng.Assert;
import org.testng.annotations.Test;

public abstract class PauseAsyncHandlerTest extends AbstractBasicTest {

    private static final int NUMBER_OF_CHUNKS = 5;
    private static final String CHUNK_CONTENT = "This is a chunk";

    public static class SlowChunkedHandler extends AbstractHandler {

        public void handle(String pathInContext, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException, ServletException {
            httpResponse.setStatus(200);
            httpResponse.setHeader("Transfer-encoding", "chunked");
            httpResponse.setContentType("application/text");

            httpResponse.flushBuffer();

            final boolean wantFailure = httpRequest.getHeader("X-FAIL-TRANSFER") != null;
            final boolean wantSlow = httpRequest.getHeader("X-SLOW") != null;

            OutputStream os = httpResponse.getOutputStream();
            for (int i = 0; i < NUMBER_OF_CHUNKS; i++) {
                os.write(CHUNK_CONTENT.getBytes(), 0, CHUNK_CONTENT.length());

                if (wantSlow) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ex) {
                        // ignored
                    }
                }

                if (wantFailure) {
                    if (i == NUMBER_OF_CHUNKS - 1) {
                        // Jetty will abort and drop the connection
                        httpResponse.sendError(500);
                        break;
                    }
                }

                httpResponse.getOutputStream().flush();
            }

            httpResponse.getOutputStream().flush();
            httpResponse.getOutputStream().close();
        }
    }

    // a /dev/null but counting how many bytes it received
    public static class CountingOutputStream extends OutputStream {
        private int byteCount = 0;

        @Override
        public void write(int b) throws IOException {
            byteCount++;
        }

        public int getByteCount() {
            return byteCount;
        }
    }

    public AbstractHandler configureHandler() throws Exception {
        return new SlowChunkedHandler();
    }

    public AsyncHttpClientConfig getAsyncHttpClientConfig() {
        return new AsyncHttpClientConfig.Builder().setMaxRequestRetry(0).setRequestTimeout(10000).build();
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testPauseInTheMiddleOfResponseAndThenResume() throws Exception {
        doTest(false, (requestBuilder, asyncHandler, countingOutputStream, totalResponseLength) -> {
            Future<Response> responseFuture = requestBuilder.execute(asyncHandler);

            // wait for the first pause
            PauseHandler pauseHandler = asyncHandler.pauseFuture().get();

            Thread.sleep(1000);
            assertEquals(countingOutputStream.getByteCount(), asyncHandler.bytesReceivedWhenPause(), "Handler shouldn't read anything while paused");
            pauseHandler.resume();

            // wait for the rest of the response to be parsed
            responseFuture.get();

            assertEquals(countingOutputStream.getByteCount(), totalResponseLength, "After resume, the whole response has to be received");
        });
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testPauseAndResumeBeforeActuallyPaused() throws Exception {
        doTest(false, (requestBuilder, asyncHandler, countingOutputStream, totalResponseLength) -> {
            Future<Response> responseFuture = requestBuilder.execute(asyncHandler);

            asyncHandler.pauseFuture().whenComplete((pauseHandler, throwable) -> {
                // This will be executed before the async handler returns CONTINUE to the AhcEventFilter
                pauseHandler.resume();
            }).get();

            // wait for the response to be parsed
            responseFuture.get();

            assertEquals(countingOutputStream.getByteCount(), totalResponseLength, "After resume, the whole response has to be received");
        });
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testPausedHandlerDoesntReceiveErrorUntilResume() throws Exception {
        doTest(true, (requestBuilder, asyncHandler, countingOutputStream, totalResponseLength) -> {
            Future<Response> responseFuture = requestBuilder.execute(asyncHandler);

            PauseHandler pauseHandler = asyncHandler.pauseFuture().get();

            Thread.sleep(1000);
            // not yet...
            verify(asyncHandler, times(0)).onThrowable(any(Throwable.class));

            pauseHandler.resume();

            try {
                responseFuture.get();
                Assert.fail("An exception is expected");
            } catch (ExecutionException remotelyClosed) {
                assertTrue(remotelyClosed.getMessage().contains("Remotely closed"), "A remotely closed exception is expected here");
            }

            assertTrue(countingOutputStream.getByteCount() < totalResponseLength, "We expect to receive less than the full response");
        });
    }


    private void doTest(boolean wantsFailure, TestCallback testCallback) throws Exception {
        try (AsyncHttpClient client = getAsyncHttpClient(getAsyncHttpClientConfig())) {
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet("http://127.0.0.1:" + port1 + "/");

            if (wantsFailure) {
                configureRequestToReceiveAFailure(r);
            }

            // If you want to debug the behavior with a slow responder, just uncomment the following line.
            // configureRequestToReceiveASlowResponse(r);

            int totalResponseLength = NUMBER_OF_CHUNKS * CHUNK_CONTENT.length();
            int pauseThreshold = totalResponseLength / 2;
            CountingOutputStream cos = new CountingOutputStream();
            TestPauseAsyncHandler asyncHandler = spy(new TestPauseAsyncHandler(cos, pauseThreshold));

            testCallback.apply(r, asyncHandler, cos, totalResponseLength);

            verify(asyncHandler, times(1)).onStatusReceived(any(HttpResponseStatus.class));
            verify(asyncHandler, times(1)).onHeadersReceived(any(HttpResponseHeaders.class));
            if (wantsFailure) {
                verify(asyncHandler, times(0)).onCompleted();
                verify(asyncHandler, times(1)).onThrowable(any(Throwable.class));
            } else {
                verify(asyncHandler, times(1)).onCompleted();
                verify(asyncHandler, times(0)).onThrowable(any(Throwable.class));
            }
        }
    }

    private interface TestCallback {
        void apply(AsyncHttpClient.BoundRequestBuilder r,
                   TestPauseAsyncHandler asyncHandler,
                   CountingOutputStream cos,
                   int totalResponseLength) throws ExecutionException, InterruptedException;
    }

    private static class TestPauseAsyncHandler implements AsyncHandler<Response> {

        private final CountingOutputStream cos;
        private final int countThresholdToPause;
        private final CompletableFuture<PauseHandler> pauseFuture = new CompletableFuture<>();
        private int bytesReceivedWhenPause = -1;

        public TestPauseAsyncHandler(CountingOutputStream cos, int countThresholdToPause) {
            this.cos = cos;
            this.countThresholdToPause = countThresholdToPause;
        }

        @Override
        public void onThrowable(Throwable t) {
            // ignored
        }

        @Override
        public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
            bodyPart.writeTo(cos);
            if (cos.getByteCount() >= countThresholdToPause) {
                if (!pauseFuture.isDone()) {
                    // it's the first time we reach the threshold (next time we don't want to pause)
                    PauseHandler pauseHandler = bodyPart.getPauseHandler();
                    pauseHandler.requestPause();
                    bytesReceivedWhenPause = cos.getByteCount();
                    pauseFuture.complete(pauseHandler);
                }
            }
            return STATE.CONTINUE;
        }

        @Override
        public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
            return STATE.CONTINUE;
        }

        @Override
        public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
            return STATE.CONTINUE;
        }

        @Override
        public Response onCompleted() throws Exception {
            return null;
        }

        public int bytesReceivedWhenPause() {
            return bytesReceivedWhenPause;
        }

        public CompletableFuture<PauseHandler> pauseFuture() {
            return pauseFuture;
        }
    }

    private static void configureRequestToReceiveASlowResponse(AsyncHttpClient.BoundRequestBuilder r) {
        r.setHeader("X-SLOW", "yup");
    }

    private static void configureRequestToReceiveAFailure(AsyncHttpClient.BoundRequestBuilder r) {
        r.setHeader("X-FAIL-TRANSFER", "please");
    }
}
