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
import static com.ning.http.client.Realm.AuthScheme.DIGEST;

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
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Realm;
import com.ning.http.client.Response;
import com.ning.http.client.async.AbstractBasicTest;
import com.ning.http.client.async.ProviderUtil;
import sun.net.www.protocol.http.AuthScheme;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.security.Constraint;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MultipleAuthenticationMethodsSupportedTest extends AbstractBasicTest {
    private final static String user = "user";
    private final static String admin = "admin";
    private final static String TEST_REALM = "MyRealm";


    @Override
    public AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config) {
        return ProviderUtil.grizzlyProvider(config);
    }


    private class SimpleHandler extends HandlerWrapper {
        public void handle(String s, Request r, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            response.addHeader("X-Auth", request.getHeader("Authorization"));
            response.setStatus(200);
            response.getOutputStream().flush();
            response.getOutputStream().close();
        }
    }

    @Override
    public AbstractHandler configureHandler() throws Exception {
        return new MultipleAuthenticationMethodsSupportedTest.SimpleHandler();
    }

    @BeforeClass(alwaysRun = true)
    @Override
    public void setUpGlobal() throws Exception {
        server = new Server();
        port1 = findFreePort();

        ServerConnector listener = new ServerConnector(server);
        listener.setHost("127.0.0.1");
        listener.setPort(port1);

        server.addConnector(listener);

        LoginService loginService = new HashLoginService(TEST_REALM, "src/test/resources/realm.properties");
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
        security.setAuthenticator(new MyDigestAuthenticator());
        security.setLoginService(loginService);

        security.setHandler(configureHandler());
        server.setHandler(security);
        server.start();
        log.info("Local HTTP server started successfully");
    }


    @Test(groups = { "standalone", "default_provider" })
    public void EventFilterMatchesHttpHeaderWithSetSchemeForAuthorization() throws ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet("http://127.0.0.1:" + port1 + "/").setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).setRealmName(TEST_REALM).setScheme(DIGEST).build());

            Future<Response> f = r.execute();
            Response resp = f.get(60, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), HttpServletResponse.SC_OK);
            assertNotNull(resp.getHeader("X-Auth"));
        }
    }


    @Test(groups = { "standalone", "default_provider" })
    public void EventFilterMatchesHttpHeaderWithoutSetSchemeForAuthorization() throws ExecutionException, TimeoutException, InterruptedException {
        try (AsyncHttpClient client = getAsyncHttpClient(null)) {
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet("http://127.0.0.1:" + port1 + "/").setRealm((new Realm.RealmBuilder()).setPrincipal(user).setPassword(admin).setRealmName(TEST_REALM).build());

            Future<Response> f = r.execute();
            Response resp = f.get(60, TimeUnit.SECONDS);
            assertNotNull(resp);
            assertEquals(resp.getStatusCode(), HttpServletResponse.SC_OK);
            assertNotNull(resp.getHeader("X-Auth"));
        }
    }

    private class MyDigestAuthenticator extends DigestAuthenticator {
        public Authentication validateRequest(ServletRequest req, ServletResponse res, boolean mandatory) throws ServerAuthException {
            HttpServletRequest request = (HttpServletRequest)req;
            String wwwAuthHeader = ((HttpServletRequest) req).getHeader(HttpHeader.WWW_AUTHENTICATE.asString());
            String authHeader = ((HttpServletRequest) req).getHeader(HttpHeader.AUTHORIZATION.asString());
            if(wwwAuthHeader == null && authHeader == null) {
                try {
                    HttpServletResponse response = (HttpServletResponse)res;
                    response.setHeader(HttpHeader.WWW_AUTHENTICATE.asString(), "Digest realm=\"" + TEST_REALM + "\", domain=\"/\", nonce=\"" + this.newNonce((Request)request) + "\", algorithm=MD5, qop=\"auth\", stale=false");
                    response.addHeader(HttpHeader.WWW_AUTHENTICATE.asString(), "Basic realm=\"" + TEST_REALM + "\"");
                    response.addHeader(HttpHeader.WWW_AUTHENTICATE.asString(), AuthScheme.NTLM.toString());
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return Authentication.SEND_CONTINUE;
                } catch (IOException var14) {
                    throw new ServerAuthException(var14);
                }
            } else {
                return super.validateRequest(req, res, mandatory);
            }
        }
    }

}
