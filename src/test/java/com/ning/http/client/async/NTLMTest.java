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

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.slf4j.LoggerFactory.getLogger;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Realm;
import com.ning.http.client.Realm.AuthScheme;
import com.ning.http.client.Realm.RealmBuilder;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.generators.InputStreamBodyGenerator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.slf4j.Logger;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public abstract class NTLMTest extends AbstractBasicTest {

    private static final String PAYLOAD = "PAYLOAD";
    private AsyncHttpClient client;
    private static final Logger LOGGER = getLogger(NTLMTest.class);
    private static final Set<String> seenClients = new HashSet<>();

    public static class NTLMHandler extends HandlerWrapper {

        @Override
        public void handle(String pathInContext, org.eclipse.jetty.server.Request request, HttpServletRequest httpRequest,
                HttpServletResponse httpResponse) throws IOException, ServletException {

            seenClients.add(request.getRemoteInetSocketAddress().toString());

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

    @BeforeTest
    public void cleanTestWatchers() {
        seenClients.clear();
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

    @Test
    public void ntlmSubsequentAuthAgainstRemoteIIS() throws Exception {

        // Build client to be used
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().build();
        client = getAsyncHttpClient(config);

        // Make good request
        Future<Response> responseFuture =  makeNtlmAuthenticatedRequestWithCredentials(realmBuilderBase());
        LOGGER.error("Making good request");
        Assert.assertEquals(responseFuture.get().getStatusCode(), 200);

        // Make bad request
        responseFuture =  makeNtlmAuthenticatedRequestWithCredentials(realmBuilderBase().setPassword("perro"));
        LOGGER.error("Making bad request");
        Assert.assertEquals(responseFuture.get().getStatusCode(), 401);
        client.close();

        Assert.assertEquals(seenClients.size(), 2);
    }

    private Future<Response> makeNtlmAuthenticatedRequestWithCredentials(RealmBuilder realmBuilder) throws InterruptedException, ExecutionException, IOException {
        Request request = new RequestBuilder(GET.asString())
                .setUrl(getTargetUrl())
                // .setUrl("http://10.250.1.231:5959/")
                .setRealm(realmBuilder.build())
                .build();

        return client.executeRequest(request);
    }
}
