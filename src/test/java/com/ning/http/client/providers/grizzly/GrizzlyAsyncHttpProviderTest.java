/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.ning.http.client.providers.grizzly;

import static java.lang.Thread.currentThread;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.async.AbstractBasicTest;
import com.ning.http.client.async.ProviderUtil;

import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.MDC;
import org.testng.Assert;
import org.testng.annotations.Test;

public class GrizzlyAsyncHttpProviderTest extends AbstractBasicTest {

  @Test(groups = { "standalone", "default_provider", "async" })
  public void asyncProviderPreservesClassLoader() throws Throwable {
    try (AsyncHttpClient client = getAsyncHttpClient(null)) {
      Request request = new RequestBuilder("GET").setUrl(getTargetUrl() + "?q=+%20x").build();
      AsyncCompletionHandler<String> asyncCompletionHandler = new AsyncCompletionHandler<String>() {
        @Override
        public String onCompleted(Response response) throws Exception {
          return response.getUri().toString();
        }

        @Override
        public void onThrowable(Throwable t) {
          t.printStackTrace();
          Assert.fail("Unexpected exception: " + t.getMessage(), t);
        }
      };

      ClassLoader currentClassLoader = currentThread().getContextClassLoader();
      ClassLoader mockClassLoader = mock(ClassLoader.class);
      currentThread().setContextClassLoader(mockClassLoader);
      // The executeRequest method is called using the mockClassLoader.
      Future<String> responseFuture = client.executeRequest(request, asyncCompletionHandler);
      currentThread().setContextClassLoader(currentClassLoader);

      responseFuture.get();
      GrizzlyResponseFuture grizzlyResponseFuture = (GrizzlyResponseFuture) responseFuture;
      HttpTransactionContext transactionContext = grizzlyResponseFuture.getHttpTransactionCtx();
      assertEquals(transactionContext.getConnection().getAttributes().getAttribute("classLoader"), mockClassLoader);
    }
  }

  @Test(groups = { "standalone", "default_provider", "async" })
  public void asyncProviderPreservesEntriesInTheMDC() throws Throwable {
    try (AsyncHttpClient client = getAsyncHttpClient(null)) {
      Request request = new RequestBuilder("GET").setUrl(getTargetUrl() + "?q=+%20x").build();
      AsyncCompletionHandler<String> asyncCompletionHandler = new AsyncCompletionHandler<String>() {
        @Override
        public String onCompleted(Response response) throws Exception {
          return response.getUri().toString();
        }

        @Override
        public void onThrowable(Throwable t) {
          t.printStackTrace();
          Assert.fail("Unexpected exception: " + t.getMessage(), t);
        }
      };

      MDC.put("theKey", "theValue");
      Future<String> responseFuture = client.executeRequest(request, asyncCompletionHandler);
      MDC.remove("theKey");

      responseFuture.get();
      GrizzlyResponseFuture grizzlyResponseFuture = (GrizzlyResponseFuture) responseFuture;
      HttpTransactionContext transactionContext = grizzlyResponseFuture.getHttpTransactionCtx();
      Map<String, String> mdc = (Map<String, String>) transactionContext.getConnection().getAttributes().getAttribute("mdc");
      assertEquals(mdc.get("theKey"), "theValue");
    }
  }

  @Override
  public AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config) {
    return ProviderUtil.grizzlyProvider(config);
  }
}
