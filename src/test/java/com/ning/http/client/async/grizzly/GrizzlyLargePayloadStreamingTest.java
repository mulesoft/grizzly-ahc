package com.ning.http.client.async.grizzly;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.async.ProviderUtil;
import com.ning.http.client.async.LargePayloadStreamingTest;

public class GrizzlyLargePayloadStreamingTest extends LargePayloadStreamingTest
{

  @Override
  public AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config) {
    return ProviderUtil.grizzlyProvider(config);
  }
}
