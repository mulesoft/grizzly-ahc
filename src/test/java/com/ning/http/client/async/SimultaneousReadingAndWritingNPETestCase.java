/*
 * Copyright (c) 2017-2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.http.client.async;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Response;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SimultaneousReadingAndWritingNPETestCase extends AbstractBasicTest {

  private Thread serverThread;
  private TestServerSocket serverSocket;
  private int port;
  private static int NUMBER_OF_REQUESTS = 100;

  @BeforeMethod
  public void setUp() throws Exception {
    port = findFreePort();
    serverSocket = new TestServerSocket(port);
    serverThread = new Thread(serverSocket);
    serverThread.start();
    serverSocket.getLatch().await();
  }

  @Test
  public void testNPE() throws Exception {
    AsyncHttpClient client = getAsyncHttpClient(new AsyncHttpClientConfig.Builder().setAllowPoolingConnections(false).build());
    for (int i = 0; i < NUMBER_OF_REQUESTS; i++) {
      Future<Response> responseFuture = client.prepareGet("http://localhost:" + port).execute();
      Response response = responseFuture.get();
    }

  }

  @Override
  public AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config) {
    return ProviderUtil.grizzlyProvider(config);
  }

  private class TestServerSocket implements Runnable {

    private final int port;
    private final CountDownLatch latch = new CountDownLatch(1);

    public TestServerSocket(int port) {
      this.port = port;
    }

    public void run() {
      ServerSocket serverSocket = null;

      try {
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        latch.countDown();
      } catch (Exception e) {
        // Ignore.
      }


      while (!Thread.interrupted()) {
        try {
          Socket clientSocket = serverSocket.accept();
          OutputStream os = clientSocket.getOutputStream();
          os.write("HTTP/1.1 200 Ok".getBytes());
          os.write("\n".getBytes());
          os.write("Date: Fri, 09 Mar 2018 09:59:26 GMT".getBytes());
          os.write("\n".getBytes());
          os.write("Content-Length: 4".getBytes());
          os.write("\n".getBytes());
          os.write("\n".getBytes());
          os.write("TEST".getBytes());
          os.flush();
          // If a delay is applied, the exception never occurs.
          // Thread.sleep(500);
          clientSocket.close();
        } catch (Exception e) {
          // Ignore.
        }
      }
    }

    public CountDownLatch getLatch() {
      return latch;
    }
  }
}