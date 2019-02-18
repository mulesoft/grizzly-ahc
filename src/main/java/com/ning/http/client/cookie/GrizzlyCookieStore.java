package com.ning.http.client.cookie;

import com.ning.http.client.RequestBuilderBase;
import org.glassfish.grizzly.http.Cookies;
import org.glassfish.grizzly.http.CookiesBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;

import static com.ning.http.util.MiscUtils.isNonEmpty;

public class GrizzlyCookieStore {

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
            this.cookieJar = new ArrayList<>(3);
    }

    public void addCookie(Cookie cookie) {
        lazyInitCookies();
        if (!cookie.hasCookieExpired()) {
            this.cookieJar.add(cookie);
        }
    }

    public void addOrReplaceCookie(Cookie cookie) {
        if (cookie.hasCookieExpired()) {
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
            CookiesBuilder.ServerCookiesBuilder builder =
                    new CookiesBuilder.ServerCookiesBuilder(false, true);
            for (String header : headers) {
                builder.parse(header);
            }
            convertCookies(builder.build());
        }
        this.cookieJar = Collections.emptyList();
    }

    private GrizzlyCookieStore convertCookies(Cookies cookies) {
        final org.glassfish.grizzly.http.Cookie[] grizzlyCookies = cookies.get();
        List<Cookie> convertedCookies = new ArrayList<>(grizzlyCookies.length);
        for (org.glassfish.grizzly.http.Cookie gCookie : grizzlyCookies) {
            Cookie extractedCookie = new Cookie(gCookie.getName(),
                    gCookie.getValue(),
                    false,
                    gCookie.getDomain(),
                    gCookie.getPath(),
                    gCookie.getMaxAge(),
                    gCookie.isSecure(),
                    false);
            if (!extractedCookie.hasCookieExpired()){
                convertedCookies.add(extractedCookie);
            }
        }
        this.cookieJar = Collections.unmodifiableList(convertedCookies);
        return this;
    }

}
