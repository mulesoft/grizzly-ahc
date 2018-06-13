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

import static com.ning.http.client.async.BasicHttpsTest.createSSLContext;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.Realm;
import com.ning.http.client.Realm.AuthScheme;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test that validates that when having an HTTP proxy and trying to access an HTTPS through the proxy the
 * proxy credentials should be passed during the CONNECT request.
 */
public abstract class BasicHttpProxyToHttpsTest extends AbstractBasicTest {

    private Server server2;

    @AfterClass(alwaysRun = true)
    public void tearDownGlobal() throws Exception {
        try {
            server.stop();
        } catch (Exception e) {
            // Nothing to do
        }
        try
        {
            server2.stop();
        } catch (Exception e) {
            // Nothing to do
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownProps() throws Exception {
        System.clearProperty("javax.net.ssl.keyStore");
    }

    @BeforeClass(alwaysRun = true)
    public void setUpGlobal() throws Exception {
        // HTTP Proxy Server
        server = new Server();
        // HTTPS Server
        server2 = new Server();

        port1 = findFreePort();
        port2 = findFreePort();

        // Proxy Server configuration
        ServerConnector listener = new ServerConnector(server);
        listener.setHost("127.0.0.1");
        listener.setPort(port1);
        server.addConnector(listener);
        server.setHandler(configureHandler());
        server.start();

        // HTTPS Server
        HttpConfiguration https_config = new HttpConfiguration();
        https_config.setSecureScheme("https");
        https_config.setSecurePort(port2);
        https_config.setOutputBufferSize(32768);
        SecureRequestCustomizer src = new SecureRequestCustomizer();
        src.setStsMaxAge(2000);
        src.setStsIncludeSubDomains(true);
        https_config.addCustomizer(src);

        SslContextFactory sslContextFactory = new SslContextFactory();
        ClassLoader cl = getClass().getClassLoader();
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        String trustStoreFile = new File(cacertsUrl.toURI()).getAbsolutePath();
        sslContextFactory.setTrustStorePath(trustStoreFile);
        sslContextFactory.setTrustStorePassword("changeit");
        sslContextFactory.setTrustStoreType("JKS");

        log.info("SSL certs path: {}", trustStoreFile);

        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        String keyStoreFile = new File(keystoreUrl.toURI()).getAbsolutePath();
        sslContextFactory.setKeyStorePath(keyStoreFile);
        sslContextFactory.setKeyStorePassword("changeit");
        sslContextFactory.setKeyStoreType("JKS");

        log.info("SSL keystore path: {}", keyStoreFile);

        ServerConnector connector = new ServerConnector(server2,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(https_config));
        connector.setHost("127.0.0.1");
        connector.setPort(port2);
        server2.addConnector(connector);
        server2.setHandler(new AuthenticateHandler(new EchoHandler()));
        server2.start();
        log.info("Local Proxy Server (" + port1 + "), HTTPS Server (" + port2 + ") started successfully");
    }

    @Override
    public AbstractHandler configureHandler() throws Exception {
        return new ProxyConnectHTTPHandler(new EchoHandler());
    }

    @Test
    public void httpProxyToHttpsUsePreemptiveTargetTest() throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException {
        doTest(true);
    }

    @Test
    public void httpProxyToHttpsTargetTest() throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException {
        doTest(false);
    }

    private void doTest(boolean usePreemptiveAuth) throws UnknownHostException, InterruptedException, ExecutionException
    {
        try (AsyncHttpClient client = getAsyncHttpClient(new AsyncHttpClientConfig.Builder().setSSLContext(createSSLContext(new AtomicBoolean(true))).build())) {
            Request request = new RequestBuilder("GET")
                .setProxyServer(basicProxy())
                .setUrl(getTargetUrl2())
                .setRealm(new Realm.RealmBuilder()
                              .setPrincipal("user")
                              .setPassword("passwd")
                              .setScheme(AuthScheme.BASIC)
                              .setUsePreemptiveAuth(usePreemptiveAuth)
                              .build())
                .build();
            Future<Response> responseFuture = client.executeRequest(request);
            Response response = responseFuture.get();
            Assert.assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            Assert.assertEquals("127.0.0.1:" + port2, response.getHeader("x-host"));
        }
    }

    private ProxyServer basicProxy() throws UnknownHostException {
        ProxyServer proxyServer = new ProxyServer("127.0.0.1", port1, "johndoe", "pass");
        proxyServer.setScheme(AuthScheme.BASIC);
        return proxyServer;
    }

    private static class ProxyConnectHTTPHandler extends ConnectHandler {

        public ProxyConnectHTTPHandler(Handler handler) {
            super(handler);
        }

        @Override
        protected boolean handleAuthentication(HttpServletRequest request, HttpServletResponse response, String address)
        {
            return true;
        }

        /**
         * Override this method do to the {@link ConnectHandler#handleConnect(org.eclipse.jetty.server.Request, HttpServletRequest, HttpServletResponse, String)} doesn't allow me to generate a response with
         * {@link HttpServletResponse#SC_PROXY_AUTHENTICATION_REQUIRED} neither {@link HttpServletResponse#SC_UNAUTHORIZED}.
         */
        @Override
        protected void handleConnect(org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response, String serverAddress)
        {
            try {
                if (!this.doHandleAuthentication(baseRequest, response)) {
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } catch (ServletException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            // Just call super class method to establish the tunnel and avoid copy/paste.
            super.handleConnect(baseRequest, request, response, serverAddress);
        }

        public boolean doHandleAuthentication(org.eclipse.jetty.server.Request request, HttpServletResponse httpResponse) throws IOException, ServletException {
            boolean result = false;
            if ("CONNECT".equals(request.getMethod())) {
                String authorization = request.getHeader("Proxy-Authorization");
                if (authorization == null) {
                    httpResponse.setStatus(HttpServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED);
                    httpResponse.setHeader("Proxy-Authenticate", "Basic realm=\"Fake Realm\"");
                    result = false;
                    httpResponse.getOutputStream().flush();
                    httpResponse.getOutputStream().close();
                } else if (authorization
                    .equals("Basic am9obmRvZTpwYXNz")) {
                    httpResponse.setStatus(HttpServletResponse.SC_OK);
                    result = true;
                } else {
                    httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    httpResponse.getOutputStream().flush();
                    httpResponse.getOutputStream().close();
                    result = false;
                }
                request.setHandled(true);
            }
            return result;
        }
    }

    private static class AuthenticateHandler extends HandlerWrapper {

        private Handler target;

        public AuthenticateHandler(Handler target) {
            this.target = target;
        }

        @Override
        public void handle(String pathInContext, org.eclipse.jetty.server.Request request, HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) throws IOException, ServletException {
            String authorization = httpRequest.getHeader("Authorization");
            if (authorization != null && authorization.equals("Basic dXNlcjpwYXNzd2Q="))
            {
                httpResponse.addHeader("target", request.getHttpURI().getPath());
                target.handle(pathInContext, request, httpRequest, httpResponse);
            }
            else
            {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setHeader("www-authenticate", "Basic realm=\"Fake Realm\"");
                httpResponse.getOutputStream().flush();
                httpResponse.getOutputStream().close();
                request.setHandled(true);
            }

        }
    }

}
