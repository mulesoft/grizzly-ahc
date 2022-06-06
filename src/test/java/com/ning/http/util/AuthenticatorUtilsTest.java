package com.ning.http.util;

import static com.ning.http.client.async.RetryNonBlockingIssue.findFreePort;
import static com.ning.http.util.AuthenticatorUtils.perConnectionProxyAuthorizationHeader;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import static org.junit.Assert.assertEquals;

import com.ning.http.client.ProxyServer;
import com.ning.http.client.Realm.AuthScheme;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AuthenticatorUtilsTest {

    private final int port;
    static final private String METHOD = "GET";
    static final private String URL = "http://127.0.0.1:%d/foo/test";
    static final private String HOST = "127.0.0.1";
    static final private String PASSWORD = "Beeblebrox";
    static final private String NTLM_PRINCIPAL = "Zaphod";
    static final private String NTLM_DOMAIN = "Ursa-Minor";
    static final private String NTLM_MSG_TYPE_1 = "NTLM TlRMTVNTUAABAAAAAYIIogAAAAAoAAAAAAAAACgAAAAFASgKAAAADw==";
    static final private String NTLM_HEADER_KEY = "Proxy-Authorization";
    static final private String NTLM_HEADER_VALUE =
        "NTLM TlRMTVNTUAADAAAAGAAYAEgAAAAYABgAYAAAABQAFAB4AAAADAAMAIwAAAASABIAmAAAAAAAAACqAAAAAYIAAgUBKAoAAAAPrYfKbe/jRoW5xDxHeoxC1gBmfWiS5+iX4OAN4xBKG/IFPwfH3agtPEia6YnhsADTVQBSAFMAQQAtAE0ASQBOAE8AUgBaAGEAcABoAG8AZABMAGkAZwBoAHQAQwBpAHQAeQA=";

    private final boolean connect;
    private final AuthScheme authScheme;
    private final String principal;
    private final String ntlDomain;
    private final String headerKey;
    private final String headerValue;
    private final String proxyAuthorizationExpected;

    public AuthenticatorUtilsTest(boolean connect,
                                  AuthScheme authScheme,
                                  String principal,
                                  String ntlDomain,
                                  String headerKey,
                                  String headerValue,
                                  String proxyAuthorization) throws IOException {
        this.port = findFreePort();
        this.connect = connect;
        this.authScheme = authScheme;
        this.principal = principal;
        this.ntlDomain = ntlDomain;
        this.headerKey = headerKey;
        this.headerValue = headerValue;
        this.proxyAuthorizationExpected = proxyAuthorization;
    }

    @Parameterized.Parameters
    public static Collection<?> parameters() {
        return Arrays.asList(new Object[][] {
            { TRUE, AuthScheme.BASIC, null, null, null, null, null },
            { TRUE, AuthScheme.DIGEST, null, null, null, null, null },
            { TRUE, AuthScheme.KERBEROS, null, null, null, null, null },
            { TRUE, AuthScheme.NONE, null, null, null, null, null },
            { TRUE, AuthScheme.NTLM, NTLM_PRINCIPAL, NTLM_DOMAIN, null, null, NTLM_MSG_TYPE_1 },
            { FALSE, AuthScheme.NTLM, NTLM_PRINCIPAL, NTLM_DOMAIN, null, null, NTLM_MSG_TYPE_1 },
            { TRUE, AuthScheme.NTLM, NTLM_PRINCIPAL, NTLM_DOMAIN, NTLM_HEADER_KEY, NTLM_HEADER_VALUE, NTLM_HEADER_VALUE},
            { FALSE, AuthScheme.NTLM, NTLM_PRINCIPAL, NTLM_DOMAIN, NTLM_HEADER_KEY, NTLM_HEADER_VALUE, null},
            { TRUE, AuthScheme.SPNEGO, null, null, null, null, null }
        });
    }

    @Test
    public void perConnectionProxyAuthorizationHeaderReturnsTheProxyAuthorizationExpected() throws IOException {
        final ProxyServer proxyServer = new ProxyServer(HOST, port, principal, PASSWORD);
        proxyServer.setNtlmDomain(ntlDomain);
        proxyServer.setScheme(authScheme);
        final Request request = new RequestBuilder(METHOD)
            .setProxyServer(proxyServer)
            .setUrl(getUrl())
            .setHeader(headerKey, headerValue)
            .build();

        String proxyAuthorization = perConnectionProxyAuthorizationHeader(request, proxyServer, connect);

        assertEquals(proxyAuthorization, proxyAuthorizationExpected);
    }

    private String getUrl() {
        return String.format(URL, port);
    }
}
