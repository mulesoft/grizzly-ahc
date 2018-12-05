package com.ning.http.client.async;

import static org.testng.Assert.assertEquals;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.providers.grizzly.FeedableBodyGenerator;
import com.ning.http.client.providers.grizzly.NonBlockingInputStreamFeeder;
import com.ning.http.client.uri.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public abstract class LargePayloadStreamingTest extends AbstractBasicTest
{

    private CountDownLatch requestEndedLatch;

    private AsyncHttpClientConfig configureClient() {
        return new AsyncHttpClientConfig.Builder()
                .setRequestTimeout(100 * 1000)
                .build();
    }

    @BeforeMethod
    public void setUp()
    {
        requestEndedLatch = new CountDownLatch(1);
    }

    // TODO: Make test fail when OOM exception is thrown
    @Test(groups = { "standalone", "default_provider" })
    public void testLargePayloadGetsStreamedSuccessfully() throws InterruptedException, ExecutionException
    {
        assertPayloadOfSizeGetsStreamedCorrectly("1GB");
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testLittlePayloadGetsStreamedSuccessfully() throws InterruptedException, ExecutionException
    {
        assertPayloadOfSizeGetsStreamedCorrectly("200B");
    }

    @Test(groups = { "standalone", "default_provider" })
    public void testIncreasingPayloadSizeWarmsUpClient() throws ExecutionException, InterruptedException
    {
        List<String> payloadSizes = Arrays.asList("100MB", "300MB", "600MB", "800MB", "1GB");

        for (String aSize : payloadSizes) {
            assertPayloadOfSizeGetsStreamedCorrectly(aSize);
            System.out.printf("Payload of size %s has been tested correctly. Sleeping 1 second to let GC cleaning.\n", aSize);
            Thread.sleep(1000);
        }
    }

    private void assertPayloadOfSizeGetsStreamedCorrectly(String payloadSize) throws InterruptedException, ExecutionException
    {

        try (AsyncHttpClient client = getAsyncHttpClient(configureClient())) {

            ListenableFuture<Response> response = executeAsyncRequest(client, payloadSize);

            requestEndedLatch.await();

            assertEquals(response.get().getStatusCode(), HttpServletResponse.SC_OK);
        }
    }

    private ListenableFuture<Response> executeAsyncRequest(AsyncHttpClient client, Long payloadSize)
    {
        RequestBuilder requestBuilder = new RequestBuilder();
        requestBuilder.setMethod("POST");
        FeedableBodyGenerator generator = new FeedableBodyGenerator();
        NonBlockingInputStreamFeeder feeder =
                new NonBlockingInputStreamFeeder(generator, new FixedSizeRandomInputStream(payloadSize));
        generator.setFeeder(feeder);
        requestBuilder.setBody(generator);
        requestBuilder.setUri(Uri.create(getTargetUrl()));

        return client.executeRequest(requestBuilder.build(), new ResponseAsyncHandler());
    }

    private ListenableFuture<Response> executeAsyncRequest(AsyncHttpClient client, String payloadSize)
    {
        Long actualPayloadSize = toBytes(payloadSize);
        System.out.printf("Generating request streaming a payload of size %s, which is %d Bytes.\n", payloadSize, actualPayloadSize);
        return executeAsyncRequest(client, actualPayloadSize);
    }

    private class FixedSizeRandomInputStream extends InputStream {

        private Random randomGenerator = new Random();

        private final Long size;

        private Long index;

        public FixedSizeRandomInputStream(Long streamSize) {
            super();
            size = streamSize;
            index = 0l;
        }

        @Override
        public int read() throws IOException {
            if (index.equals(size)) {
                return -1;
            }

            index++;
            return randomGenerator.nextInt(256);
        }
    }

    private Long toBytes(String aSize) {
        Matcher numberMatcher = Pattern.compile("([0-9]+)[A-Za-z]+").matcher(aSize);
        Matcher unitMatcher = Pattern.compile("^[0-9]+([A-Za-z]+)$").matcher(aSize);

        numberMatcher.find();
        unitMatcher.find();

        Long foundNumber = Long.parseLong(numberMatcher.group(1));
        String foundUnit = unitMatcher.group(1).toUpperCase();

        if (foundUnit.equals("GB")) {
            return foundNumber * (1l << 30);
        } else if (foundUnit.equals("MB")) {
            return foundNumber * (1l << 20);
        } else if (foundUnit.equals("KB")) {
            return foundNumber * (1l << 10);
        } else {
            return foundNumber;
        }
    }

    private class ResponseAsyncHandler extends AsyncCompletionHandler<Response> {

        @Override
        public Response onCompleted(Response response) throws Exception
        {
            requestEndedLatch.countDown();
            return response;
        }
    }

    private class StreamReceivingHandler extends AbstractHandler
    {

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
        {
            if ("POST".equals(request.getMethod())) {
                // Lock until whole payload has been consumed
                System.out.println("START: Started reading received stream");
                long totalReceivedSoFar = 0;
                 while (request.getInputStream().read() != -1)
                     totalReceivedSoFar++;
                System.out.printf("END: Finished reading received stream. Total received: %d\n", totalReceivedSoFar);
                // Whole payload has been consumed. Succeed.
                response.setStatus(HttpServletResponse.SC_OK);
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            }
            response.getOutputStream().flush();
            response.getOutputStream().close();

            baseRequest.setHandled(true);
        }
    }

    @Override
    public AbstractHandler configureHandler() throws Exception
    {
        return new StreamReceivingHandler();
    }
}
