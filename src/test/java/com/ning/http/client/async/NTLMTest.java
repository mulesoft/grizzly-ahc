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

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.slf4j.LoggerFactory.getLogger;
import static org.testng.Assert.assertEquals;

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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public abstract class NTLMTest extends AbstractBasicTest {

    private static final Logger LOGGER = getLogger(NTLMTest.class);
    private static final Set<String> seenClients = new HashSet<>();
    private static final Set<String> authenticatedClients = new HashSet<>();
    private static final String PAYLOAD = "PAYLOAD";
    private AsyncHttpClient client;

    public static class NTLMHandler extends HandlerWrapper {

        @Override
        public void handle(String pathInContext, org.eclipse.jetty.server.Request request, HttpServletRequest httpRequest,
                HttpServletResponse httpResponse) throws IOException, ServletException {

            // Register seen client INet addresses. This will uniquely identify them
            seenClients.add(request.getRemoteInetSocketAddress().toString());
            LOGGER.error("Seen socket address = {}", request.getRemoteInetSocketAddress());

            String authorization = httpRequest.getHeader("Authorization");
            if (authenticatedClients.contains(request.getRemoteInetSocketAddress().toString())) {
                // Connection already authenticated, NTLM is connection oriented
                if (request.getMethod().equals(POST.asString())) {
                    assertEquals(new String(toByteArray(httpRequest.getInputStream())), PAYLOAD);
                }
                httpResponse.setStatus(200);
            } else if (authorization == null) {
                // First step:
                // - No authorization header
                // - Sends the supported authentication protocols
                httpResponse.setStatus(401);
                httpResponse.setHeader("WWW-Authenticate", "NTLM");
            } else if (authorization.equals("NTLM TlRMTVNTUAABAAAAAYIIogAAAAAoAAAAAAAAACgAAAAFASgKAAAADw==")) {
                // Second step:
                // - Authorization header contains user-password
                // - Set the challenge
                httpResponse.setStatus(401);
                httpResponse.setHeader("WWW-Authenticate", "NTLM TlRMTVNTUAACAAAAAAAAACgAAAABggAAU3J2Tm9uY2UAAAAAAAAAAA==");
            } else if (authorization.equals("NTLM TlRMTVNTUAADAAAAGAAYAEgAAAAYABgAYAAAABQAFAB4AAAADAAMAIwAAAASABIAmAAAAAAAAACqAAAAAYIAAgUBKAoAAAAPrYfKbe/jRoW5xDxHeoxC1gBmfWiS5+iX4OAN4xBKG/IFPwfH3agtPEia6YnhsADTVQBSAFMAQQAtAE0ASQBOAE8AUgBaAGEAcABoAG8AZABMAGkAZwBoAHQAQwBpAHQAeQA=")) {
                // Third step:
                // - Receive the correct response to the challenge
                authenticatedClients.add(request.getRemoteInetSocketAddress().toString());
                if (request.getMethod().equals(POST.asString())) {
                  assertEquals(new String(toByteArray(httpRequest.getInputStream())), PAYLOAD);
                }
                httpResponse.setStatus(200);
            } else {
                // Authentication fails, unauthorized
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
        assertEquals(status, 200);
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
            assertEquals(status, 200);
        }
    }

    private Future<Response> makeNtlmAuthenticatedRequestWithCredentials(RealmBuilder realmBuilder) throws InterruptedException, ExecutionException, IOException {
        Request request = new RequestBuilder(GET.asString())
                .setUrl(getTargetUrl())
                .setRealm(realmBuilder.build())
                .build();

        return client.executeRequest(request);
    }

    @BeforeMethod
    public void cleanTestWatchers() {
        seenClients.clear();
        authenticatedClients.clear();
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
    public void ntlmCustomConnectionManagementOnEachCredentialsSet() throws Exception {
        // Build client to be used
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().build();
        client = getAsyncHttpClient(config);

        // Make good request
        Future<Response> responseFuture =  makeNtlmAuthenticatedRequestWithCredentials(realmBuilderBase());
        assertEquals(responseFuture.get().getStatusCode(), SC_OK);

        // Make bad request
        responseFuture =  makeNtlmAuthenticatedRequestWithCredentials(realmBuilderBase().setPassword("goat"));
        assertEquals(responseFuture.get().getStatusCode(), SC_UNAUTHORIZED);
        client.close();

        // Since connection management is handled different in NTLM, this will mean that for each
        // credentials set, a new connection will be created and managed from that moment on.
        assertEquals(seenClients.size(), 2);
    }

    @Test
    public void renegotiateNTLMCredentials() throws Exception {
        // Build client to be used
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().build();
        client = getAsyncHttpClient(config);

        // Make good request
        Future<Response> responseFuture =  makeNtlmAuthenticatedRequestWithCredentials(realmBuilderBase());
        assertEquals(responseFuture.get().getStatusCode(), SC_OK);

        // Second request using same connection is also ok
        responseFuture =  makeNtlmAuthenticatedRequestWithCredentials(realmBuilderBase());
        assertEquals(responseFuture.get().getStatusCode(), SC_OK);

        // Make bad request
        responseFuture =  makeNtlmAuthenticatedRequestWithCredentials(realmBuilderBase().setPassword("goat"));
        assertEquals(responseFuture.get().getStatusCode(), SC_UNAUTHORIZED);

        // twice
        responseFuture =  makeNtlmAuthenticatedRequestWithCredentials(realmBuilderBase().setPassword("goat"));
        assertEquals(responseFuture.get().getStatusCode(), SC_UNAUTHORIZED);

        // New good request is ok
        responseFuture =  makeNtlmAuthenticatedRequestWithCredentials(realmBuilderBase());
        assertEquals(responseFuture.get().getStatusCode(), SC_OK);

        // Re-authenticate!
        authenticatedClients.clear();
        responseFuture =  makeNtlmAuthenticatedRequestWithCredentials(realmBuilderBase());
        assertEquals(responseFuture.get().getStatusCode(), SC_OK);

        client.close();

        // Since connection management is handled different in NTLM, this will mean that for each
        // credentials set, a new connection will be created and managed from that moment on.
        assertEquals(seenClients.size(), 2);
        assertEquals(authenticatedClients.size(), 1);
    }
}
