/*
 * Copyright (c) 2010-2015 Sonatype, Inc. All rights reserved.
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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static com.ning.http.util.AsyncHttpProviderUtils.getNonEmptyPath;
import static com.ning.http.util.MiscUtils.isNonEmpty;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import com.ning.http.client.ProxyServer;
import com.ning.http.client.Realm;
import com.ning.http.client.Request;
import com.ning.http.client.ntlm.NTLMEngine;
import com.ning.http.client.spnego.SpnegoEngine;
import com.ning.http.client.uri.Uri;

public final class AuthenticatorUtils {
    private static final String PROXY_AUTH_HEADER = "Proxy-Authorization";
    
    public static String perConnectionAuthorizationHeader(Request request,
            Uri uri, ProxyServer proxyServer, Realm realm) throws IOException {
        String authorizationHeader = null;

        if (realm != null && realm.getUsePreemptiveAuth()) {
            switch (realm.getScheme()) {
            case NTLM:
                String msg = NTLMEngine.INSTANCE.generateType1Msg();
                authorizationHeader = "NTLM " + msg;
                break;
            case KERBEROS:
            case SPNEGO:
                String host;
                if (proxyServer != null)
                    host = proxyServer.getHost();
                else if (request.getVirtualHost() != null)
                    host = request.getVirtualHost();
                else
                    host = uri.getHost();

                try {
                    authorizationHeader = "Negotiate " + SpnegoEngine.INSTANCE.generateToken(host);
                } catch (Throwable e) {
                    throw new IOException(e);
                }
                break;
            default:
                break;
            }
        }
        
        return authorizationHeader;
    }
    
    public static String perRequestAuthorizationHeader(Realm realm) {

        String authorizationHeader = null;

        if (realm != null && realm.getUsePreemptiveAuth()) {

            switch (realm.getScheme()) {
            case BASIC:
                authorizationHeader = computeBasicAuthentication(realm);
                break;
            case DIGEST:
                if (isNonEmpty(realm.getNonce()))
                    authorizationHeader = computeDigestAuthentication(realm);
                break;
            case NTLM:
            case KERBEROS:
            case SPNEGO:
                // NTLM, KERBEROS and SPNEGO are only set on the first request with a connection,
                // see perConnectionAuthorizationHeader
                break;
            default:
                throw new IllegalStateException("Invalid Authentication " + realm);
            }
        }

        return authorizationHeader;
    }

    public static String getHeaderWithPrefix(List<String> authenticateHeaders, String prefix) {
        if (authenticateHeaders != null) {
            for (String authenticateHeader : authenticateHeaders) {
                if (authenticateHeader.regionMatches(true, 0, prefix, 0, prefix.length()))
                    return authenticateHeader;
            }
        }

        return null;
    }

    private static List<String> getProxyAuthorizationHeader(Request request) {
        return request.getHeaders().get(PROXY_AUTH_HEADER);
    }

    public static String perConnectionProxyAuthorizationHeader(Request request, Realm proxyRealm) {

        String proxyAuthorization = null;
        if (proxyRealm != null && proxyRealm.getUsePreemptiveAuth()) {
            switch (proxyRealm.getScheme()) {
                case NTLM:
                case KERBEROS:
                case SPNEGO:
                    List<String> auth = getProxyAuthorizationHeader(request);
                    if (getHeaderWithPrefix(auth, "NTLM") == null) {
                        String msg = NTLMEngine.INSTANCE.generateType1Msg();
                        proxyAuthorization = "NTLM " + msg;
                    }

                    break;
                default:
            }
        }

        return proxyAuthorization;
    }
    
    public static String perRequestProxyAuthorizationHeader(Realm proxyRealm) {

        String proxyAuthorization = null;
        if (proxyRealm != null && proxyRealm.getUsePreemptiveAuth()) {

            switch (proxyRealm.getScheme()) {
                case BASIC:
                    proxyAuthorization = computeBasicAuthentication(proxyRealm);
                    break;
                case DIGEST:
                    if (isNonEmpty(proxyRealm.getNonce()))
                        proxyAuthorization = computeDigestAuthentication(proxyRealm);
                    break;
                case NTLM:
                case KERBEROS:
                case SPNEGO:
                    // NTLM, KERBEROS and SPNEGO are only set on the first request with a connection,
                    // see perConnectionProxyAuthorizationHeader
                    break;
                default:
                    throw new IllegalStateException("Invalid Authentication scheme " + proxyRealm.getScheme());
            }
        }

        return proxyAuthorization;
    }
    
    public static String computeBasicAuthentication(Realm realm) {
        return computeBasicAuthentication(realm.getPrincipal(), realm.getPassword(), realm.getCharset());
    }

    private static String computeBasicAuthentication(String principal, String password, Charset charset) {
        String s = principal + ":" + password;
        return "Basic " + Base64.encode(s.getBytes(charset));
    }

    public static String computeRealmURI(Realm realm) {
        return computeRealmURI(realm.getUri(), realm.isUseAbsoluteURI(), realm.isOmitQuery());
    }
    
    public static String computeRealmURI(Uri uri, boolean useAbsoluteURI, boolean omitQuery) {
        if (useAbsoluteURI) {
            return omitQuery && MiscUtils.isNonEmpty(uri.getQuery()) ? uri.withNewQuery(null).toUrl() : uri.toUrl();
        } else {
            String path = getNonEmptyPath(uri);
            return omitQuery || !MiscUtils.isNonEmpty(uri.getQuery()) ? path : path + "?" + uri.getQuery();
        }
    }
    
    public static String computeDigestAuthentication(Realm realm) {

        StringBuilder builder = new StringBuilder().append("Digest ");
        append(builder, "username", realm.getPrincipal(), true);
        append(builder, "realm", realm.getRealmName(), true);
        append(builder, "nonce", realm.getNonce(), true);
        append(builder, "uri", computeRealmURI(realm), true);
        if (isNonEmpty(realm.getAlgorithm()))
            append(builder, "algorithm", realm.getAlgorithm(), false);

        append(builder, "response", realm.getResponse(), true);

        if (realm.getOpaque() != null)
            append(builder, "opaque", realm.getOpaque(), true);

        if (realm.getQop() != null) {
            append(builder, "qop", realm.getQop(), false);
            // nc and cnonce only sent if server sent qop
            append(builder, "nc", realm.getNc(), false);
            append(builder, "cnonce", realm.getCnonce(), true);
        }
        builder.setLength(builder.length() - 2); // remove tailing ", "

        // FIXME isn't there a more efficient way?
        return new String(StringUtils.charSequence2Bytes(builder, ISO_8859_1));
    }

    private static StringBuilder append(StringBuilder builder, String name, String value, boolean quoted) {
        builder.append(name).append('=');
        if (quoted)
            builder.append('"').append(value).append('"');
        else
            builder.append(value);

        return builder.append(", ");
    }

    public static String getHttpHeaderForAuthScheme(List<String> authenticationHeaders, String authScheme) {
        if (authenticationHeaders.size() == 1 || "NONE".equals(authScheme)) {
            return authenticationHeaders.get(0);
        }
        int authLen = authScheme.length();
        for (String authenticateHeader : authenticationHeaders) {
            if (authenticateHeader.length() >= authLen && authenticateHeader.substring(0, authLen).equalsIgnoreCase(authScheme)){
                return authenticateHeader;
            }
        }
        return authenticationHeaders.get(0);
    }
}
