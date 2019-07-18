/*
 * Copyright (c) 2014 AsyncHttpClient Project. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 *     http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.ning.http.client.async;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Realm;
import com.ning.http.client.Realm.AuthScheme;
import com.ning.http.client.Realm.RealmBuilder;
import com.ning.http.client.generators.InputStreamBodyGenerator;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpMethod.POST;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public abstract class NTLMTest extends AbstractBasicTest {

    private static final String PAYLOAD = "PAYLOAD";
    
    public static class NTLMHandler extends HandlerWrapper {

        @Override
        public void handle(String pathInContext, org.eclipse.jetty.server.Request request, HttpServletRequest httpRequest,
                HttpServletResponse httpResponse) throws IOException, ServletException {

            String authorization = httpRequest.getHeader("Authorization");
            if (authorization == null) {
                httpResponse.setStatus(401);
                httpResponse.setHeader("WWW-Authenticate", "NTLM");

            } else if (authorization.equals("NTLM TlRMTVNTUAABAAAAAYIIogAAAAAoAAAAAAAAACgAAAAFASgKAAAADw==")) {
                httpResponse.setStatus(401);
                httpResponse.setHeader("WWW-Authenticate", "NTLM TlRMTVNTUAACAAAAAAAAACgAAAABggAAU3J2Tm9uY2UAAAAAAAAAAA==");

            } else if (authorization
                    .equals("NTLM TlRMTVNTUAADAAAAGAAYAEgAAAAYABgAYAAAABQAFAB4AAAADAAMAIwAAAASABIAmAAAAAAAAACqAAAAAYIAAgUBKAoAAAAPrYfKbe/jRoW5xDxHeoxC1gBmfWiS5+iX4OAN4xBKG/IFPwfH3agtPEia6YnhsADTVQBSAFMAQQAtAE0ASQBOAE8AUgBaAGEAcABoAG8AZABMAGkAZwBoAHQAQwBpAHQAeQA=")) {
                if (request.getMethod().equals(POST.asString())) {
                  Assert.assertEquals(new String(toByteArray(httpRequest.getInputStream())), PAYLOAD);
                }
                httpResponse.setStatus(200);
            } else {
                httpResponse.setStatus(401);
            }
            httpResponse.setContentLength(0);
            httpResponse.getOutputStream().flush();
            httpResponse.getOutputStream().close();
        }
    }

    @Override
    public AbstractHandler configureHandler() throws Exception {
        return new NTLMHandler();
    }

    private RealmBuilder realmBuilderBase() {
        return new Realm.RealmBuilder()//
                .setScheme(AuthScheme.NTLM)//
                .setNtlmDomain("Ursa-Minor")//
                .setNtlmHost("LightCity")//
                .setPrincipal("Zaphod")//
                .setPassword("Beeblebrox");
    }

    private void ntlmAuthWithGetTest(RealmBuilder realmBuilder) throws IOException, InterruptedException, ExecutionException {
  
      AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setRealm(realmBuilder.build()).build();
  
      try (AsyncHttpClient client = getAsyncHttpClient(config)) {
        Request request = new RequestBuilder(GET.asString()).setUrl(getTargetUrl()).build();
        Future<Response> responseFuture = client.executeRequest(request);
        int status = responseFuture.get().getStatusCode();
        Assert.assertEquals(status, 200);
      }
    }
    
    private void ntlmAuthTestWithPost(RealmBuilder realmBuilder) throws IOException, InterruptedException, ExecutionException {

        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setRealm(realmBuilder.build()).setFollowRedirect(true).build();

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
          ByteArrayInputStream body = new ByteArrayInputStream(PAYLOAD.getBytes());
          Request request = new RequestBuilder(POST.asString()).setBody(new InputStreamBodyGenerator(body)).setUrl(getTargetUrl())
              .setBody("PAYLOAD").build();
            
            Future<Response> responseFuture = client.executeRequest(request);
            int status = responseFuture.get().getStatusCode();
            Assert.assertEquals(status, 200);
        }
    }

    @Test
    public void lazyNTLMAuthPostTest() throws IOException, InterruptedException, ExecutionException {
      ntlmAuthTestWithPost(realmBuilderBase());
    }

    @Test
    public void preemptiveNTLMAuthPostTest() throws IOException, InterruptedException, ExecutionException {
      ntlmAuthTestWithPost(realmBuilderBase().setUsePreemptiveAuth(true));
    }
    
    @Test
    public void lazyNTLMAuthGetTest() throws IOException, InterruptedException, ExecutionException {
      ntlmAuthWithGetTest(realmBuilderBase());
    }

    @Test
    public void preemptiveNTLMAuthGetTest() throws IOException, InterruptedException, ExecutionException {
      ntlmAuthWithGetTest(realmBuilderBase().setUsePreemptiveAuth(true));
    }
}
