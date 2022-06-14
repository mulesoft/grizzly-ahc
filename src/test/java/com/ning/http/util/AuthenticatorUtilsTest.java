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
package com.ning.http.util;

import static com.ning.http.client.async.RetryNonBlockingIssue.findFreePort;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import static org.testng.Assert.assertEquals;

import com.ning.http.client.ProxyServer;
import com.ning.http.client.Realm.AuthScheme;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;

import java.io.IOException;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AuthenticatorUtilsTest {

    private final int port = findFreePort();
    private static final String METHOD = "GET";
    private static final String URL = "http://127.0.0.1:%d/foo/test";
    private static final String HOST = "127.0.0.1";
    private static final String PASSWORD = "Beeblebrox";
    private static final String NTLM_PRINCIPAL = "Zaphod";
    private static final String NTLM_DOMAIN = "Ursa-Minor";
    private static final String NTLM_MSG_TYPE_1 = "NTLM TlRMTVNTUAABAAAAAYIIogAAAAAoAAAAAAAAACgAAAAFASgKAAAADw==";
    private static final String NTLM_HEADER_KEY = "Proxy-Authorization";
    private static final String NTLM_HEADER_VALUE =
        "NTLM TlRMTVNTUAADAAAAGAAYAEgAAAAYABgAYAAAABQAFAB4AAAADAAMAIwAAAASABIAmAAAAAAAAACqAAAAAYIAAgUBKAoAAAAPrYfKbe/jRoW5xDxHeoxC1gBmfWiS5+iX4OAN4xBKG/IFPwfH3agtPEia6YnhsADTVQBSAFMAQQAtAE0ASQBOAE8AUgBaAGEAcABoAG8AZABMAGkAZwBoAHQAQwBpAHQAeQA=";

    public AuthenticatorUtilsTest() throws IOException {
    }

    @DataProvider(name = "proxyAuthorization")
    public Object[][] createData() {
        return new Object[][] {
            { TRUE, AuthScheme.BASIC, null, null, null, null, null, TRUE },
            { TRUE, AuthScheme.BASIC, null, null, null, null, NTLM_MSG_TYPE_1, FALSE },
            { TRUE, AuthScheme.DIGEST, null, null, null, null, null, TRUE },
            { TRUE, AuthScheme.DIGEST, null, null, null, null, NTLM_MSG_TYPE_1, FALSE },
            { TRUE, AuthScheme.KERBEROS, null, null, null, null, null, TRUE },
            { TRUE, AuthScheme.KERBEROS, null, null, null, null, NTLM_MSG_TYPE_1, FALSE },
            { TRUE, AuthScheme.NONE, null, null, null, null, null, TRUE },
            { TRUE, AuthScheme.NONE, null, null, null, null, NTLM_MSG_TYPE_1, FALSE },
            { TRUE, AuthScheme.NTLM, NTLM_PRINCIPAL, NTLM_DOMAIN, null, null, NTLM_MSG_TYPE_1, TRUE },
            { FALSE, AuthScheme.NTLM, NTLM_PRINCIPAL, NTLM_DOMAIN, null, null, NTLM_MSG_TYPE_1, TRUE },
            { TRUE, AuthScheme.NTLM, NTLM_PRINCIPAL, NTLM_DOMAIN, null, null, NTLM_MSG_TYPE_1, FALSE },
            { FALSE, AuthScheme.NTLM, NTLM_PRINCIPAL, NTLM_DOMAIN, null, null, NTLM_MSG_TYPE_1, FALSE },
            { TRUE, AuthScheme.NTLM, NTLM_PRINCIPAL, NTLM_DOMAIN, NTLM_HEADER_KEY, NTLM_HEADER_VALUE, NTLM_HEADER_VALUE, TRUE },
            { FALSE, AuthScheme.NTLM, NTLM_PRINCIPAL, NTLM_DOMAIN, NTLM_HEADER_KEY, NTLM_HEADER_VALUE, null, TRUE },
            { TRUE, AuthScheme.NTLM, NTLM_PRINCIPAL, NTLM_DOMAIN, NTLM_HEADER_KEY, NTLM_HEADER_VALUE, NTLM_HEADER_VALUE, FALSE },
            { FALSE, AuthScheme.NTLM, NTLM_PRINCIPAL, NTLM_DOMAIN, NTLM_HEADER_KEY, NTLM_HEADER_VALUE, null, FALSE },
            { TRUE, AuthScheme.SPNEGO, null, null, null, null, null, TRUE },
            { TRUE, AuthScheme.SPNEGO, null, null, null, null, NTLM_MSG_TYPE_1, FALSE }
        };
    }

    @Test(dataProvider = "proxyAuthorization")
    public void perConnectionProxyAuthorizationHeaderReturnsTheProxyAuthorizationExpected(
        boolean connect, AuthScheme authScheme, String principal, String ntlDomain, String headerKey,
        String headerValue, String proxyAuthorizationExpected, boolean properProxyAuthorization) throws IOException {

        final ProxyServer proxyServer = new ProxyServer(HOST, port, principal, PASSWORD);
        proxyServer.setNtlmDomain(ntlDomain);
        proxyServer.setScheme(authScheme);
        final Request request = new RequestBuilder(METHOD)
            .setProxyServer(proxyServer)
            .setUrl(getUrl())
            .setHeader(headerKey, headerValue)
            .build();

        AuthenticatorUtils.setProperProxyAuthorization(properProxyAuthorization);
        String proxyAuthorization = AuthenticatorUtils.perConnectionProxyAuthorizationHeader(request, proxyServer, connect);

        assertEquals(proxyAuthorization, proxyAuthorizationExpected);
    }

    private String getUrl() {
        return String.format(URL, port);
    }
}
