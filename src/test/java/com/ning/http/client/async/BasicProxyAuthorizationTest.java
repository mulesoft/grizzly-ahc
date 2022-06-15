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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.Request;
import com.ning.http.client.Response;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class BasicProxyAuthorizationTest extends AbstractBasicTest {

    protected static final Logger log = LoggerFactory.getLogger(BasicProxyAuthorizationTest.class);
    private static final String LOCALHOST = "127.0.0.1";
    private static final String CONNECTION_HEADER = "X-Connection";
    private static final String KEEP_ALIVE = "keep-alive";
    private static final String PROXY_AUTHORIZATION_HEADER = "Proxy-Authorization";

    @Override
    public AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config) {
        return ProviderUtil.grizzlyProvider(config);
    }

    public HandlerWrapper configureHandler() throws Exception {
        return  new BasicProxyAuthorizationHandler();
    }

    public static class BasicProxyAuthorizationHandler extends ConnectHandler {

        @Override
        public void handle(String pathInContext, org.eclipse.jetty.server.Request request, HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) throws IOException, ServletException {

            assertNull(httpRequest.getHeader(PROXY_AUTHORIZATION_HEADER));
            super.handle(pathInContext, request, httpRequest, httpResponse);
        }
    }

    @BeforeClass(alwaysRun = true)
    public void setUpGlobal() throws Exception {
        server = new Server();
        Server server2 = new Server();

        port1 = findFreePort();
        port2 = findFreePort();

        ServerConnector listener = new ServerConnector(server);
        listener.setHost(LOCALHOST);
        listener.setPort(port1);
        server.addConnector(listener);

        HttpConfiguration https_config = new HttpConfiguration();
        https_config.setSecureScheme("https");
        https_config.setSecurePort(port2);
        https_config.setOutputBufferSize(32768);

        SslContextFactory sslContextFactory = new SslContextFactory();
        ClassLoader cl = getClass().getClassLoader();
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        String trustStoreFile = new File(cacertsUrl.toURI()).getAbsolutePath();
        sslContextFactory.setTrustStorePath(trustStoreFile);
        sslContextFactory.setTrustStorePassword("changeit");
        sslContextFactory.setTrustStoreType("JKS");

        log.info("SSL certs path: {}", trustStoreFile);

        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        String keystoreFile = new File(keystoreUrl.toURI()).getAbsolutePath();
        sslContextFactory.setKeyStorePath(keystoreFile);
        sslContextFactory.setKeyStorePassword("changeit");
        sslContextFactory.setKeyStoreType("JKS");

        log.info("SSL keystore path: {}", trustStoreFile);

        SecureRequestCustomizer src = new SecureRequestCustomizer();
        src.setStsMaxAge(2000);
        src.setStsIncludeSubDomains(true);
        https_config.addCustomizer(src);

        ServerConnector connector = new ServerConnector(server2,
            new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
            new HttpConnectionFactory(https_config));
        connector.setHost(LOCALHOST);
        connector.setPort(port2);

        server2.addConnector(connector);

        server.setHandler(configureHandler());
        server.start();

        server2.setHandler(new EchoHandler());
        server2.start();
        log.info("Local HTTP server started successfully");
    }

    @Test(description = "W-10863931: When the connection is not an NTLM one, the NTLM proxy authorization is not required.")
    public void connectionProxyAuthorizationHeaderProperlySetTest() throws Throwable {
        ProxyServer proxyServer = new ProxyServer(LOCALHOST, port1);

        AsyncHttpClientConfig asyncHttpClientConfig = new AsyncHttpClientConfig.Builder()
            .setAcceptAnyCertificate(true)
            .build();

        try (AsyncHttpClient client = getAsyncHttpClient(asyncHttpClientConfig)) {
            Request request = client.prepareGet(getTargetUrl2()).setProxyServer(proxyServer).build();
            Response response = client.executeRequest(request).get();

            assertEquals(response.getStatusCode(), 200);
            assertEquals(response.getHeader(CONNECTION_HEADER), KEEP_ALIVE);
        }
    }
}
