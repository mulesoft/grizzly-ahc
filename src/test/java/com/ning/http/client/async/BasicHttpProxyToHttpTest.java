/*
 * Copyright (c) 2016 AsyncHttpClient Project. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 *     http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.ning.http.client.async;

import static com.ning.http.client.Realm.AuthScheme.BASIC;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.Realm;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test that validates that when having an HTTP proxy and trying to access an HTTP through the proxy the
 * proxy credentials should be passed after it gets a 407 response.
 */
public abstract class BasicHttpProxyToHttpTest extends AbstractBasicTest {

    private Server server2;

    public static class ProxyHTTPHandler extends AbstractHandler {

        @Override
        public void handle(String pathInContext, org.eclipse.jetty.server.Request request, HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) throws IOException, ServletException {

            String authorization = httpRequest.getHeader("Authorization");
            String proxyAuthorization = httpRequest.getHeader("Proxy-Authorization");
            if (proxyAuthorization == null) {
                httpResponse.setStatus(HttpServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED);
                httpResponse.setHeader("Proxy-Authenticate", "Basic realm=\"Fake Realm\"");
            } else if (proxyAuthorization
                .equals("Basic am9obmRvZTpwYXNz") && authorization != null && authorization.equals("Basic dXNlcjpwYXNzd2Q=")) {
                httpResponse.addHeader("target", request.getHttpURI().getPath());
                httpResponse.setStatus(HttpServletResponse.SC_OK);
            } else {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setHeader("www-authenticate", "Basic realm=\"Fake Realm\"");
            }
            httpResponse.getOutputStream().flush();
            httpResponse.getOutputStream().close();
            request.setHandled(true);
        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDownGlobal() throws Exception {
        server.stop();
        server2.stop();
    }

    @BeforeClass(alwaysRun = true)
    public void setUpGlobal() throws Exception {
        // HTTP Server
        server = new Server();
        // HTTP Proxy Server
        server2 = new Server();

        port1 = findFreePort();
        port2 = findFreePort();

        // HTTP Server
        ServerConnector listener = new ServerConnector(server);

        listener.setHost("127.0.0.1");
        listener.setPort(port1);
        server.addConnector(listener);
        server.setHandler(new EchoHandler());
        server.start();

        listener = new ServerConnector(server2);

        // Proxy Server configuration
        listener.setHost("127.0.0.1");
        listener.setPort(port2);
        server2.addConnector(listener);
        server2.setHandler(configureHandler());
        server2.start();
        log.info("Local HTTP Server (" + port1 + "), Proxy HTTP Server (" + port2 + ") started successfully");
    }


    @Override
    public AbstractHandler configureHandler() throws Exception {
        return new ProxyHTTPHandler();
    }

    @Test
    public void httpProxyToHttpTargetUsePreemptiveAuthTest() throws IOException, InterruptedException, ExecutionException {
        doTest(true);
    }

    @Test
    public void httpProxyToHttpTargetTest() throws IOException, InterruptedException, ExecutionException {
        doTest(false);
    }

    private void doTest(boolean usePreemptiveAuth) throws UnknownHostException, InterruptedException, ExecutionException
    {
        try (AsyncHttpClient client = getAsyncHttpClient(new AsyncHttpClientConfig.Builder().build())) {
            Request request = new RequestBuilder("GET").setProxyServer(basicProxy()).setUrl(getTargetUrl()).setRealm(
                new Realm.RealmBuilder().setPrincipal("user").setPassword("passwd").setScheme(BASIC).setUsePreemptiveAuth(usePreemptiveAuth).build()).build();
            Future<Response> responseFuture = client.executeRequest(request);
            Response response = responseFuture.get();
            Assert.assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            Assert.assertTrue(getTargetUrl().endsWith(response.getHeader("target")));
        }
    }

    private ProxyServer basicProxy() throws UnknownHostException {
        ProxyServer proxyServer = new ProxyServer("127.0.0.1", port2, "johndoe", "pass");
        proxyServer.setScheme(BASIC);
        return proxyServer;
    }
}
