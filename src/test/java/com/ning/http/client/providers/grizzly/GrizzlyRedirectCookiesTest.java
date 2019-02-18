package com.ning.http.client.providers.grizzly;

import static java.util.Collections.list;
import static java.util.concurrent.TimeUnit.SECONDS;
import static com.ning.http.client.Realm.AuthScheme.BASIC;
import static org.eclipse.jetty.util.security.Constraint.__BASIC_AUTH;
import static org.hamcrest.core.Is.is;
import static org.eclipse.jetty.http.HttpHeader.AUTHORIZATION;
import static org.eclipse.jetty.http.HttpHeader.WWW_AUTHENTICATE;
import static org.eclipse.jetty.http.HttpHeader.LOCATION;
import static org.eclipse.jetty.http.HttpHeader.COOKIE;

import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.http.HttpStatus.FOUND_302;


import java.util.HashSet;
import com.ning.http.client.Realm;
import com.ning.http.client.Response;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.async.AbstractBasicTest;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.security.Constraint;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

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
import java.util.concurrent.Future;


public class GrizzlyRedirectCookiesTest extends AbstractBasicTest {
    protected static final String REDIRECT_PATH = "/redirect";
    protected static final String AUTH_PATH = "/auth";
    protected static final String BASE_PATH = "/foo/test";
    protected static final String FINAL_PATH = "/final";
    protected static final String user = "user";
    protected static final String admin = "admin";
    protected static final String TEST_REALM = "MyRealm";

    protected static final String GLOBAL_REQUEST_TIMEOUT = "50000";

    private static Set<String> cookiesReceived = new HashSet<>();
    private static List<Cookie> cookieJar = new ArrayList<>();

    public enum TestCookie {
        VALID_COOKIE("MyWorkingCookie", "workingvalue", FINAL_PATH, 10),
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
        assertFilteredCookies(config, REDIRECT_PATH);
    }

    @Test
    public void grizzlyClientOnUnauthorizedFilterExpiredCookies() throws Exception {
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
                .setRequestTimeout(Integer.valueOf(GLOBAL_REQUEST_TIMEOUT))
                .setFollowRedirect(true)
                .setRealm(new Realm.RealmBuilder().setPrincipal(user).setPassword(admin).setRealmName(TEST_REALM).setScheme(BASIC).build())
                .build();
        assertFilteredCookies(config, AUTH_PATH);
    }

    private void assertFilteredCookies(AsyncHttpClientConfig config, String path) throws Exception {
        fillCookieJar();

        Set<String> expectedCookies = new HashSet<>();
        expectedCookies.add(TestCookie.VALID_COOKIE.toString());

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            AsyncHttpClient.BoundRequestBuilder r = client.prepareGet(getTargetUrl() + path);

            Future<Response> f = r.execute();
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
                //for (Cookie cookie: cookieJar) { httpResponse.addCookie(cookie); }
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
    public AbstractHandler configureHandler() throws Exception {
        return new MyResponseHandler();
    }

    @Override
    public AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config) {
        return new AsyncHttpClient(config);
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
            constraint.setName(__BASIC_AUTH);
            constraint.setRoles(new String[]{user, admin});
            constraint.setAuthenticate(true);

            ConstraintMapping mapping = new ConstraintMapping();
            mapping.setConstraint(constraint);
            mapping.setPathSpec(BASE_PATH + AUTH_PATH);

            List<ConstraintMapping> cm = new ArrayList<>();
            cm.add(mapping);

            Set<String> knownRoles = new HashSet<>();
            knownRoles.add(user);
            knownRoles.add(admin);

            ConstraintSecurityHandler security = new ConstraintSecurityHandler() {
                @Override
                public void handle(String arg0, org.eclipse.jetty.server.Request req, HttpServletRequest serReq, HttpServletResponse serRes) throws IOException, ServletException {
                    System.err.println("request in security handler");
                    System.err.println("Authorization: " + serReq.getHeader("Authorization"));
                    System.err.println("RequestUri: " + serReq.getRequestURI());
                    if (serReq.getHeader(WWW_AUTHENTICATE.asString()) == null && serReq.getHeader(AUTHORIZATION.asString()) == null){
                        for (Cookie cookie : cookieJar) {
                            serRes.addCookie(cookie);
                        }
                    }
                    super.handle(arg0, req, serReq, serRes);
                }
            };
            security.setConstraintMappings(cm, knownRoles);
            security.setAuthenticator(new BasicAuthenticator());
            security.setLoginService(loginService);

            security.setHandler(configureHandler());
            server.setHandler(security);
            server.start();
    }

}
