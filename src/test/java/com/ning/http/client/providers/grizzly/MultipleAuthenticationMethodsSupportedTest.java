/*
 * Copyright (c) 2012-2015 Sonatype, Inc. All rights reserved.
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

package com.ning.http.client.providers.grizzly;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static com.ning.http.client.Realm.AuthScheme.NTLM;
import static com.ning.http.client.Realm.AuthScheme.DIGEST;
import static org.eclipse.jetty.http.HttpHeader.WWW_AUTHENTICATE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Realm;
import com.ning.http.client.Response;
import com.ning.http.client.async.AbstractBasicTest;
import com.ning.http.client.async.ProviderUtil;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.security.Constraint;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MultipleAuthenticationMethodsSupportedTest extends AbstractBasicTest
{
    private final static String user = "user";
    private final static String admin = "admin";
    private final static String TEST_REALM = "MyRealm";


    @Override
    public AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config)
    {
        return ProviderUtil.grizzlyProvider(config);
    }


    private class SimpleHandler extends HandlerWrapper
    {
        public void handle(String s, Request r, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
        {
            response.addHeader("X-Auth", request.getHeader("Authorization"));
            response.setStatus(200);
            response.getOutputStream().flush();
            response.getOutputStream().close();
        }
    }

    @Override
    public AbstractHandler configureHandler() throws Exception
    {
        return new MultipleAuthenticationMethodsSupportedTest.SimpleHandler();
    }

    @BeforeClass(alwaysRun = true)
    @Override
    public void setUpGlobal() throws Exception
    {
        server = new Server();
        Logger root = Logger.getRootLogger();
        root.setLevel(Level.DEBUG);
        root.addAppender(new ConsoleAppender(new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN)));

        port1 = findFreePort();
        ServerConnector listener = new ServerConnector(server);

        listener.setHost("127.0.0.1");
        listener.setPort(port1);

        server.addConnector(listener);

        LoginService loginService = new HashLoginService("MyRealm", "src/test/resources/realm.properties");
        server.addBean(loginService);

        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[] { user, admin });
        constraint.setAuthenticate(true);

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setConstraint(constraint);
        mapping.setPathSpec("/*");

        List<ConstraintMapping> cm = new ArrayList<>();
        cm.add(mapping);

        Set<String> knownRoles = new HashSet<>();
        knownRoles.add(user);
        knownRoles.add(admin);

        ConstraintSecurityHandler security = new ConstraintSecurityHandler();
        security.setConstraintMappings(cm, knownRoles);
        security.setAuthenticator(new DigestAuthenticator());
        security.setLoginService(loginService);

        security.setHandler(configureHandler());
        server.setHandler(security);
        server.start();
        log.info("Local HTTP server started successfully");
    }


    @Test(groups = { "standalone", "default_provider" })
    public void EventFilterMatchesHttpHeaderWithSetSchemeForAuthorization() throws ExecutionException, TimeoutException, InterruptedException
    {
        try (AsyncHttpClient client = getAsyncHttpClient(null))
        {
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet("http://127.0.0.1:" + port1 + "/").setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).setRealmName("MyRealm").setScheme(DIGEST).build());

            r.addHeader(WWW_AUTHENTICATE.asString(), "Digest realm=\"" + TEST_REALM + "\", domain=\"/digest\", nonce=\"+Upgraded+v1a574e295ff1f41c52582b82815bb734c5c50331c97c4d301bc97f789c5e9e73ca9564b24cbd898ce5f1c13598999faa2ab013ee5b1597087&quot;,charset=utf-8\", algorithm=MD5, qop=\"auth\", stale=false");
            r.addHeader(WWW_AUTHENTICATE.asString(), "Basic realm=\"" + TEST_REALM + "\"");
            r.addHeader(WWW_AUTHENTICATE.asString(), NTLM.toString());

            Future<Response> f = r.execute();
            Response resp = f.get(60, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), HttpServletResponse.SC_OK);
            assertNotNull(resp.getHeader("X-Auth"));
        }
    }


    @Test(groups = { "standalone", "default_provider" })
    public void EventFilterMatchesHttpHeaderWithoutSetSchemeForAuthorization() throws ExecutionException, TimeoutException, InterruptedException
    {
        try (AsyncHttpClient client = getAsyncHttpClient(null))
        {
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet("http://127.0.0.1:" + port1 + "/").setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).setRealmName("MyRealm").build());

            r.addHeader(WWW_AUTHENTICATE.asString(), "Digest realm=\"" + TEST_REALM + "\", domain=\"/digest\", nonce=\"+Upgraded+v1a574e295ff1f41c52582b82815bb734c5c50331c97c4d301bc97f789c5e9e73ca9564b24cbd898ce5f1c13598999faa2ab013ee5b1597087&quot;,charset=utf-8\", algorithm=MD5, qop=\"auth\", stale=false");
            r.addHeader(WWW_AUTHENTICATE.asString(), "Basic realm=\"" + TEST_REALM + "\"");
            r.addHeader(WWW_AUTHENTICATE.asString(), NTLM.toString());

            Future<Response> f = r.execute();
            Response resp = f.get(60, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), HttpServletResponse.SC_OK);
            assertNotNull(resp.getHeader("X-Auth"));
        }
    }
}
