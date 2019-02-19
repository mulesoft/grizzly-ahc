package com.ning.http.client.providers.grizzly;

import static java.util.Collections.list;
import static org.testng.Assert.assertEquals;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.concurrent.TimeUnit.SECONDS;
import static com.ning.http.client.Realm.AuthScheme.BASIC;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED;
import static org.eclipse.jetty.util.security.Constraint.__BASIC_AUTH;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.http.HttpStatus.FOUND_302;
import static org.eclipse.jetty.http.HttpHeader.AUTHORIZATION;
import static org.eclipse.jetty.http.HttpHeader.PROXY_AUTHORIZATION;
import static org.eclipse.jetty.http.HttpHeader.PROXY_AUTHENTICATE;
import static org.eclipse.jetty.http.HttpHeader.WWW_AUTHENTICATE;
import static org.eclipse.jetty.http.HttpHeader.LOCATION;
import static org.eclipse.jetty.http.HttpHeader.COOKIE;

import java.util.HashSet;
import java.util.concurrent.Future;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Realm.RealmBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.async.AbstractBasicTest;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.security.Constraint;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.AsyncHttpClientConfig;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class GrizzlyRedirectCookiesTest extends AbstractBasicTest {
    protected static final String REDIRECT_PATH = "/redirect";
    protected static final String AUTH_PATH = "/auth";
    protected static final String BASE_PATH = "/foo/test";
    protected static final String FINAL_PATH = "/final";
    protected static final String TEST_USER = "user";
    protected static final String TEST_ADMIN = "admin";
    protected static final String TEST_PROXY_USER = "proxy_user";
    protected static final String TEST_PROXY_ADMIN = "proxy_pass";
    protected static final String TEST_REALM = "MyRealm";
    protected static final String HOST = "127.0.0.1";
    protected static final String GLOBAL_REQUEST_TIMEOUT = "50000";

    protected static Server proxyServer;
    private static Set<String> cookiesReceived = new HashSet<>();
    private static List<Cookie> cookieJar = new ArrayList<>();

    public enum TestCookie {
        VALID_COOKIE("MyWorkingCookie", "workingvalue", FINAL_PATH, 1000),
        EXPIRED_COOKIE("MyExpiredCookie", "deleted", FINAL_PATH, 0);

        private Cookie cookie;

        TestCookie(String name, String value, String path, int age) {
            this.cookie = new Cookie(name, value);
            this.cookie.setPath(path);
            this.cookie.setMaxAge(age);
        }

        public Cookie getCookie() {
            return this.cookie;
        }

        public String toString() {
            return this.cookie.getName() + "=" + this.cookie.getValue();
        }
    }

    @BeforeTest
    protected void clean() {
        cookiesReceived.clear();
        cookieJar.clear();
    }

    @Test
    public void grizzlyClientOnRedirectFilterExpiredCookies() throws Exception {
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
                .setRequestTimeout(Integer.valueOf(GLOBAL_REQUEST_TIMEOUT))
                .setFollowRedirect(true)
                .build();
        assertFilteredCookies(config, getTargetUrl() + REDIRECT_PATH);
    }

    @Test
    public void grizzlyClientOnAuthenticationFilterExpiredCookies() throws Exception {
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
                .setRequestTimeout(Integer.valueOf(GLOBAL_REQUEST_TIMEOUT))
                .setFollowRedirect(true)
                .setRealm(new RealmBuilder()
                        .setPrincipal(TEST_USER)
                        .setPassword(TEST_ADMIN)
                        .setRealmName(TEST_REALM)
                        .setScheme(BASIC).build())
                .build();
        assertFilteredCookies(config, getTargetUrl() + AUTH_PATH);
    }

    @Test
    public void grizzlyClientOnProxyAuthenticationFilterExpiredCookies() throws Exception {
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
                .setProxyServer(basicProxy())
                .setRealm(new RealmBuilder()
                        .setPrincipal(TEST_USER)
                        .setPassword(TEST_ADMIN)
                        .setScheme(BASIC)
                        .setUsePreemptiveAuth(false).build())
                .build();
        assertFilteredCookies(config, getTargetUrl() + AUTH_PATH);
    }

    private void assertFilteredCookies(AsyncHttpClientConfig config, String uri) throws Exception {
        fillCookieJar();
        Set<String> expectedCookies = new HashSet<>();
        expectedCookies.add(TestCookie.VALID_COOKIE.toString());
        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            BoundRequestBuilder request = client.prepareGet(uri);
            Future<Response> f = request.execute();
            f.get(60, SECONDS);
            assertEquals(cookiesReceived, expectedCookies);
        }
    }

    private void fillCookieJar() {
        Cookie validCookie = TestCookie.VALID_COOKIE.getCookie();
        Cookie expiredCookie = TestCookie.EXPIRED_COOKIE.getCookie();

        cookieJar.add(expiredCookie);
        cookieJar.add(validCookie);
    }

    interface MockRequestInterceptor {
        void interceptResponse(HttpServletResponse httpResponse, HttpServletRequest request, String path);
    }

    private class MyResponseHandler extends AbstractHandler {
        private Map<String, MockRequestInterceptor> REDIRECT_MAP = new HashMap<>();

        MockRequestInterceptor deflectRequest = (httpResponse, request, path) -> {
                httpResponse.setStatus(FOUND_302);
                httpResponse.setHeader(LOCATION.asString(), path);
            };

        MockRequestInterceptor acceptRequest = (httpResponse, request, path) -> {
                httpResponse.setStatus(OK_200);
                cookiesReceived.addAll(list(request.getHeaders(COOKIE.toString())));
            };

        public MyResponseHandler() {
            REDIRECT_MAP.put(BASE_PATH + REDIRECT_PATH, deflectRequest);
            REDIRECT_MAP.put(BASE_PATH + FINAL_PATH, acceptRequest);
            REDIRECT_MAP.put(BASE_PATH + AUTH_PATH, acceptRequest);
        }

        @Override
        public void handle(String pathInContext,
                           org.eclipse.jetty.server.Request request,
                           HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) throws IOException, ServletException {

            String requestUri = httpRequest.getRequestURI();
            REDIRECT_MAP.get(requestUri).interceptResponse(httpResponse, httpRequest, BASE_PATH + FINAL_PATH);
            httpResponse.getOutputStream().flush();
        }
    }

    @Override
    public AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config) {
        return new AsyncHttpClient(config);
    }

    @BeforeClass(alwaysRun = true)
    @Override
    public void setUpGlobal() throws Exception {
        server = new Server();
        proxyServer = new Server();

        port1 = findFreePort();
        port2 = findFreePort();

        ServerConnector listener = new ServerConnector(server);

        // Server configuration
        listener.setHost(HOST);
        listener.setPort(port1);
        server.addConnector(listener);
        setBasicAuthSecurityHandler();

        listener = new ServerConnector(proxyServer);

        // Proxy Server configuration
        listener.setHost(HOST);
        listener.setPort(port2);
        proxyServer.addConnector(listener);
        proxyServer.setHandler(new ProxyHTTPHandler());
        proxyServer.start();

        server.start();
    }

    private void setBasicAuthSecurityHandler() throws Exception {
        LoginService loginService = new HashLoginService(TEST_REALM, "src/test/resources/realm.properties");
        server.addBean(loginService);

        Constraint constraint = new Constraint();
        constraint.setName(__BASIC_AUTH);
        constraint.setRoles(new String[]{TEST_USER, TEST_ADMIN});
        constraint.setAuthenticate(true);

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setConstraint(constraint);
        mapping.setPathSpec(BASE_PATH + AUTH_PATH);

        List<ConstraintMapping> cm = new ArrayList<>();
        cm.add(mapping);

        Set<String> knownRoles = new HashSet<>();
        knownRoles.add(TEST_USER);
        knownRoles.add(TEST_ADMIN);

        ConstraintSecurityHandler security = new ConstraintSecurityHandler() {
            @Override
            public void handle(String pathInContext, org.eclipse.jetty.server.Request req, HttpServletRequest serReq,
                               HttpServletResponse serRes) throws IOException, ServletException {
                if (serReq.getHeader(WWW_AUTHENTICATE.asString()) == null && serReq.getHeader(AUTHORIZATION.asString()) == null){
                    for (Cookie cookie : cookieJar) {
                        serRes.addCookie(cookie);
                    }
                }
                super.handle(pathInContext, req, serReq, serRes);
            }
        };

        security.setConstraintMappings(cm, knownRoles);
        security.setAuthenticator(new BasicAuthenticator());
        security.setLoginService(loginService);
        security.setHandler(configureHandler());
        server.setHandler(security);
    }

    private static class ProxyHTTPHandler extends AbstractHandler {

        @Override
        public void handle(String pathInContext, org.eclipse.jetty.server.Request request, HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) throws IOException, ServletException {

            String authorization = httpRequest.getHeader(AUTHORIZATION.asString());
            String proxyAuthorization = httpRequest.getHeader(PROXY_AUTHORIZATION.asString());
            String authCred = B64Code.encode(TEST_USER + ":" + TEST_ADMIN, ISO_8859_1);
            String proxyAuthCred = B64Code.encode(TEST_PROXY_USER + ":" + TEST_PROXY_ADMIN, ISO_8859_1);

            if (proxyAuthorization == null)  {
                httpResponse.setStatus(SC_PROXY_AUTHENTICATION_REQUIRED);
                httpResponse.setHeader(PROXY_AUTHENTICATE.asString(), String.format("Basic realm=\"%s\"", TEST_REALM));
            } else if (proxyAuthorization.equals(String.format("Basic %s", proxyAuthCred))
                    && authorization != null && authorization.equals(String.format("Basic %s", authCred))) {
                httpResponse.addHeader("target", request.getHttpURI().getPath());
                cookiesReceived.addAll(list(request.getHeaders(COOKIE.toString())));
                httpResponse.setStatus(SC_OK);
            } else {
                httpResponse.setStatus(SC_UNAUTHORIZED);
                httpResponse.setHeader(WWW_AUTHENTICATE.asString(), String.format("Basic realm=\"%s\"", TEST_REALM));
                for (Cookie cookie : cookieJar) {
                    httpResponse.addCookie(cookie);
                }
            }
            httpResponse.getOutputStream().flush();
            httpResponse.getOutputStream().close();
            request.setHandled(true);
        }
    }

    @Override
    public AbstractHandler configureHandler() throws Exception {
        return new MyResponseHandler();
    }

    private ProxyServer basicProxy() {
        ProxyServer proxyServer = new ProxyServer(HOST, port2, TEST_PROXY_USER, TEST_PROXY_ADMIN);
        proxyServer.setScheme(BASIC);
        return proxyServer;
    }
}
