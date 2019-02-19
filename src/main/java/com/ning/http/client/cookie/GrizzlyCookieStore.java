package com.ning.http.client.cookie;

import org.glassfish.grizzly.http.Cookies;
import org.glassfish.grizzly.http.CookiesBuilder.ServerCookiesBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;

import static com.ning.http.util.MiscUtils.isNonEmpty;

public class GrizzlyCookieStore {
    private static final int INITIAL_CAPACITY = 3;

    private List<Cookie> cookieJar;

    public GrizzlyCookieStore() { }

    public GrizzlyCookieStore(List<String> headers){
        build(headers);
    }

    public GrizzlyCookieStore(Collection<Cookie> cookies){
        this.cookieJar = new ArrayList<>(cookies);
    }

    public void setCookies(Collection<Cookie> cookies){
        this.cookieJar = new ArrayList<>(cookies);
    }

    public void lazyInitCookies(){
        if (this.cookieJar == null)
            this.cookieJar = new ArrayList<>(INITIAL_CAPACITY);
    }

    public void addCookie(Cookie cookie) {
        lazyInitCookies();
        if (!cookie.hasCookieExpired()) {
            this.cookieJar.add(cookie);
        }
    }

    public void addOrReplaceCookie(Cookie cookie) {
        if (cookie.hasCookieExpired() || this.cookieJar.contains(cookie)) {
            return;
        }
        String cookieKey = cookie.getName();
        boolean replace = false;
        int index = 0;
        lazyInitCookies();
        for (Cookie c : cookieJar) {
            if (c.getName().equals(cookieKey)) {
                replace = true;
                break;
            }

            index++;
        }
        if (replace) {
            cookieJar.set(index, cookie);
        } else {
            cookieJar.add(cookie);
        }
    }

    public Collection<Cookie> getCookies() {
        return cookieJar != null ? Collections.unmodifiableCollection(cookieJar) : Collections.<Cookie> emptyList();
    }

    public void clear(){
        if (cookieJar != null) {
            cookieJar.clear();
        }
    }

    private void build(List<String> headers) {
        if (isNonEmpty(headers)) {
            ServerCookiesBuilder builder =
                    new ServerCookiesBuilder(false, true);
            for (String header : headers) {
                builder.parse(header);
            }
            convertCookies(builder.build());
        }
        this.cookieJar = Collections.emptyList();
    }

    public static List<Cookie> buildCookiesList(List<String> headers) {
        if (isNonEmpty(headers)) {
            ServerCookiesBuilder builder =
                new ServerCookiesBuilder(false, true);
            for (String header : headers) {
                builder.parse(header);
            }
            return convertCookies(builder.build());

        } else {
            return Collections.emptyList();
        }
    }

    protected static List<Cookie> convertCookies(Cookies cookies) {
        final org.glassfish.grizzly.http.Cookie[] grizzlyCookies = cookies.get();
        List<Cookie> convertedCookies = new ArrayList<>(grizzlyCookies.length);

        for (org.glassfish.grizzly.http.Cookie gCookie : grizzlyCookies) {
            convertedCookies.add(new Cookie(gCookie.getName(),
                    gCookie.getValue(),
                    false,
                    gCookie.getDomain(),
                    gCookie.getPath(),
                    gCookie.getMaxAge(),
                    gCookie.isSecure(),
                    false));
        }
        return Collections.unmodifiableList(convertedCookies);

    }

}
