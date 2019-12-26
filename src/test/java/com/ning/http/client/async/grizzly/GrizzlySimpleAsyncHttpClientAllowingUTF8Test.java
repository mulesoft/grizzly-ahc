/*
 * Copyright (c) 2012 Sonatype, Inc. All rights reserved.
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

package com.ning.http.client.async.grizzly;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.async.ProviderUtil;
import com.ning.http.client.async.SimpleAsyncHttpClientTest;
import com.ning.http.client.multipart.PartBase;
import com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProvider;

public class GrizzlySimpleAsyncHttpClientAllowingUTF8Test extends SimpleAsyncHttpClientTest {

    @BeforeTest
    public void before() throws Exception {
        setHeadersCharSet(UTF_8);
    }
    
    @AfterTest
    public void after() throws Exception {
      setHeadersCharSet(US_ASCII);
    }

    private void setHeadersCharSet(Charset charset) throws NoSuchFieldException, IllegalAccessException
    {
        Field headersCharset = PartBase.class.getDeclaredField("HEADERS_CHARSET");
          headersCharset.setAccessible(true);
          headersCharset.set(null, charset);
    }
    
    @Override
    public AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config) {
        return ProviderUtil.grizzlyProvider(config);
    }

    public String getProviderClass() {
        return GrizzlyAsyncHttpProvider.class.getName();
    }
    
    @Override
    protected String getContentDispositionHeader() {
        return "bäPart";
    }
    
    @Override
    protected String getFilename() {
      return "filenamë";
    }
    
}
