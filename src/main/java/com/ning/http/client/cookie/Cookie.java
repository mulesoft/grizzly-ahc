/*
 * Copyright (c) 2014 AsyncHttpClient Project. All rights reserved.
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
package com.ning.http.client.cookie;

import org.slf4j.LoggerFactory;

public class Cookie {

    private static final long MAX_AGE_UNSPECIFIED = Long.MIN_VALUE;

    /**
     * @param expires parameter will be ignored.
     * Use the other factory that don't take an expires.
     * 
     * @deprecated
     */
    @Deprecated
    public static Cookie newValidCookie(String name, String value, boolean wrap, String domain, String path, int expires, long maxAge, boolean secure, boolean httpOnly) {
        return newValidCookie(name, value, wrap, domain, path, maxAge, secure, httpOnly);
    }

    public static Cookie newValidCookie(String name, String value, boolean wrap, String domain, String path, long maxAge, boolean secure, boolean httpOnly) {

        if (name == null) {
            throw new NullPointerException("name");
        }
        name = name.trim();
        if (name.length() == 0) {
            throw new IllegalArgumentException("empty name");
        }

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c > 127) {
                throw new IllegalArgumentException("name contains non-ascii character: " + name);
            }

            // Check prohibited characters.
            switch (c) {
            case '\t':
            case '\n':
            case 0x0b:
            case '\f':
            case '\r':
            case ' ':
            case ',':
            case ';':
            case '=':
                throw new IllegalArgumentException("name contains one of the following prohibited characters: " + "=,; \\t\\r\\n\\v\\f: " + name);
            }
        }

        if (name.charAt(0) == '$') {
            throw new IllegalArgumentException("name starting with '$' not allowed: " + name);
        }

        if (value == null) {
            throw new NullPointerException("value");
        }

        domain = validateValue("domain", domain);
        path = validateValue("path", path);

        return new Cookie(name, value, wrap, domain, path, maxAge, secure, httpOnly);
    }

    private static String validateValue(String name, String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        if (value.length() == 0) {
            return null;
        }

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
            case '\r':
            case '\n':
            case '\f':
            case 0x0b:
            case ';':
                throw new IllegalArgumentException(name + " contains one of the following prohibited characters: " + ";\\r\\n\\f\\v (" + value + ')');
            }
        }
        return value;
    }

    private final String name;
    private final String value;
    private final boolean wrap;
    private final String domain;
    private final String path;
    private long maxAge = MAX_AGE_UNSPECIFIED;
    private final boolean secure;
    private final boolean httpOnly;
    // Hold the creation time (in seconds) of the http cookie for later
    // expiration calculation
    private final long whenCreated;


    public Cookie(String name, String value, boolean wrap, String domain, String path, long maxAge, boolean secure, boolean httpOnly) {
        this.name = name;
        this.value = value;
        this.wrap = wrap;
        this.domain = domain;
        this.path = path;
        this.maxAge = maxAge;
        this.secure = secure;
        this.httpOnly = httpOnly;
        this.whenCreated = System.currentTimeMillis();
    }

    public String getDomain() {
        return domain;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public boolean isWrap() {
        return wrap;
    }

    public String getPath() {
        return path;
    }

    @Deprecated
    public long getExpires() {
        return Long.MIN_VALUE;
    }

    public boolean hasExpired() {
        if (maxAge == 0) return true;

        // if not specify max-age, this cookie should be
        // discarded when user agent is to be closed, but
        // it is not expired.
        if (maxAge == MAX_AGE_UNSPECIFIED){
            return false;
        }

        long deltaSecond = (System.currentTimeMillis() - whenCreated) / 1000;
        return deltaSecond > maxAge;
    }
    
    public long getMaxAge() {
        return maxAge;
    }

    public boolean isSecure() {
        return secure;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(name);
        buf.append('=');
        if (wrap)
            buf.append('"').append(value).append('"');
        else
            buf.append(value);
        if (domain != null) {
            buf.append("; domain=");
            buf.append(domain);
        }
        if (path != null) {
            buf.append("; path=");
            buf.append(path);
        }
        if (maxAge >= 0) {
            buf.append("; maxAge=");
            buf.append(maxAge);
            buf.append('s');
        }
        if (secure) {
            buf.append("; secure");
        }
        if (httpOnly) {
            buf.append("; HTTPOnly");
        }
        return buf.toString();
    }
}
