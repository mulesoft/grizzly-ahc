/*
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */
package com.ning.http.client;

import static com.ning.http.util.MiscUtils.isNonEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.ning.http.client.Realm.AuthScheme;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a proxy server.
 */
public class ProxyServer {

    public enum Protocol {
        HTTP("http"), HTTPS("https"), NTLM("NTLM"), KERBEROS("KERBEROS"), SPNEGO("SPNEGO");

        private final String protocol;

        private Protocol(final String protocol) {
            this.protocol = protocol;
        }

        public String getProtocol() {
            return protocol;
        }

        @Override
        public String toString() {
            return getProtocol();
        }
    }

    private final List<String> nonProxyHosts = new ArrayList<>();
    private final Protocol protocol;
    private final String host;
    private final String principal;
    private final String password;
    private final int port;
    private final String url;
    private Charset charset = UTF_8;
    private String ntlmDomain = System.getProperty("http.auth.ntlm.domain", "");
    private String ntlmHost;
    private AuthScheme scheme = AuthScheme.BASIC;
    private boolean forceHttp10 = false;

    public ProxyServer(final Protocol protocol, final String host, final int port, String principal, String password) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.principal = principal;
        this.password = password;
        this.url = protocol + "://" + host + ":" + port;
    }

    public ProxyServer(final String host, final int port, String principal, String password) {
        this(Protocol.HTTP, host, port, principal, password);
    }

    public ProxyServer(final Protocol protocol, final String host, final int port) {
        this(protocol, host, port, null, null);
    }

    public ProxyServer(final String host, final int port) {
        this(Protocol.HTTP, host, port, null, null);
    }

    public Realm.RealmBuilder realmBuilder() {
        return new Realm.RealmBuilder()//
        .setNtlmDomain(ntlmDomain)
        .setNtlmHost(ntlmHost)
        .setPrincipal(principal)
        .setPassword(password)
        .setScheme(scheme);
    }
    
    public Protocol getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPrincipal() {
        return principal;
    }

    public String getPassword() {
        return password;
    }

    public ProxyServer setCharset(Charset charset) {
        this.charset = charset;
        return this;
    }

    public Charset getCharset() {
        return charset;
    }

    public ProxyServer addNonProxyHost(String uri) {
        nonProxyHosts.add(uri);
        return this;
    }

    public ProxyServer removeNonProxyHost(String uri) {
        nonProxyHosts.remove(uri);
        return this;
    }

    public List<String> getNonProxyHosts() {
        return Collections.unmodifiableList(nonProxyHosts);
    }

    public ProxyServer setNtlmDomain(String ntlmDomain) {
        this.ntlmDomain = ntlmDomain;
        return this;
    }

    public String getNtlmDomain() {
        return ntlmDomain;
    }
    
    public AuthScheme getScheme() {
        return scheme;
    }

    public void setScheme(AuthScheme scheme) {
        this.scheme = scheme;
    }

    public String getNtlmHost() {
        return ntlmHost;
    }

    public void setNtlmHost(String ntlmHost) {
        this.ntlmHost = ntlmHost;
    }

    public String getUrl() {
        return url;
    }
    
    public boolean isForceHttp10() {
        return forceHttp10;
    }

    public void setForceHttp10(boolean forceHttp10) {
        this.forceHttp10 = forceHttp10;
    }

    @Override
    public String toString() {
        return url;
    }

    /**
     * Checks whether proxy should be used according to nonProxyHosts settings of it, or we want to go directly to
     * target host. If <code>null</code> proxy is passed in, this method returns true -- since there is NO proxy, we
     * should avoid to use it. Simple hostname pattern matching using "*" are supported, but only as prefixes.
     *
     * @param hostname the hostname
     * @return true if we have to ignore proxy use (obeying non-proxy hosts settings), false otherwise.
     * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html">Networking Properties</a>
     */
    public boolean isIgnoredForHost(String hostname) {
        if (hostname == null)
            throw new NullPointerException("hostname");

        if (isNonEmpty(nonProxyHosts)) {
            for (String nonProxyHost : nonProxyHosts) {
                if (matchNonProxyHost(hostname, nonProxyHost))
                    return true;
            }
        }

        return false;
    }

    private boolean matchNonProxyHost(String targetHost, String nonProxyHost) {

        if (nonProxyHost.length() > 1) {
            if (nonProxyHost.charAt(0) == '*') {
                return targetHost.regionMatches(true, targetHost.length() - nonProxyHost.length() + 1, nonProxyHost, 1,
                    nonProxyHost.length() - 1);
            } else if (nonProxyHost.charAt(nonProxyHost.length() - 1) == '*')
                return targetHost.regionMatches(true, 0, nonProxyHost, 0, nonProxyHost.length() - 1);
        }

        return nonProxyHost.equalsIgnoreCase(targetHost);
    }
}

