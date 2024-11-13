/*
 * Copyright (c) 2015 AsyncHttpClient Project. All rights reserved.
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
package com.ning.http.client.async.grizzly;

import static java.lang.Class.forName;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpProvider;
import com.ning.http.client.async.NTLMTest;
import com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProvider;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

// TODO: W-17216089 - Remove when the kill switch is removed
@Test
public class GrizzlyNTLMTestWithForcePayloadOnType1Test extends NTLMTest {

    private static final String NTLM_FORCE_SEND_PAYLOAD_ON_TYPE1_SYS_PROP_NAME = "mule.ntlm.force.send.payload.on.type1";

    // Creates a new classloader which will force reloading the impl classes so the system property change takes effect and only
    // in the scope of this test case
    private static TestClassLoader testClassLoader;
    private static Constructor<?> asyncProviderImplCtor;

    @BeforeClass
    public static void createTestClassLoader() {
        URL[] urls = new URL[] {
          GrizzlyAsyncHttpProvider.class.getProtectionDomain().getCodeSource().getLocation()
        };
        testClassLoader = new TestClassLoader(urls, NTLMTest.class.getClassLoader());
    }

    @BeforeClass
    public static void getProviderImplConstructor() throws ReflectiveOperationException {
        Class<?> asyncProviderImplClass = forName(GrizzlyAsyncHttpProvider.class.getName(), true, testClassLoader);
        asyncProviderImplCtor = asyncProviderImplClass.getConstructor(AsyncHttpClientConfig.class);
    }

    @AfterClass
    public static void closeTestClassLoader() throws IOException {
        testClassLoader.close();
    }

    @Override
    public AbstractHandler configureHandler() throws Exception {
        return new NTLMHandler(true);
    }

    @Override
    public AsyncHttpClient getAsyncHttpClient(AsyncHttpClientConfig config) {
        try {
            System.setProperty(NTLM_FORCE_SEND_PAYLOAD_ON_TYPE1_SYS_PROP_NAME, "true");
            return new AsyncHttpClient((AsyncHttpProvider) asyncProviderImplCtor.newInstance(config), config);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        } finally {
            System.setProperty(NTLM_FORCE_SEND_PAYLOAD_ON_TYPE1_SYS_PROP_NAME, "false");
        }
    }

    private static class TestClassLoader extends URLClassLoader {

        private static final String GRIZZLY_PROVIDERS_PACKAGE_NAME = "com.ning.http.client.providers.grizzly.";

        public TestClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (mustReload(name)) {
                return super.findClass(name);
            }
            return super.loadClass(name);
        }

        private boolean mustReload(String name) {
            return name.startsWith(GRIZZLY_PROVIDERS_PACKAGE_NAME);
        }
    }
}
