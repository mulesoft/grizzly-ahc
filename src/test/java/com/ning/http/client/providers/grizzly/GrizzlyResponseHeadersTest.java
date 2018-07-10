/*
 * Copyright (c) 2012-2015 Sonatype, Inc. All rights reserved.
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

package com.ning.http.client.providers.grizzly;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.concurrent.CountDownLatch;

import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.testng.annotations.Test;

/**
 * This race condition appeared when two different threads wanted to retrieve the keySet from the Grizzly Response. If
 * grizzlyHeaders.getHeaders().keySet() was invoked, there existed the possibility that the keySet was invoked from one
 * thread before all the headers were added to the headers to return, resulting in a ConcurrentModificationException,
 * because the keySet iterates keyLookup. This became apparent when a javaagent like New Relic add tracing facilities.
 *
 */
public class GrizzlyResponseHeadersTest
{
    private static final int MIME_HEADERS_COUNT = 1000000;
    private static final String HEADER_PREFIX = "Header";

    @Test
    public void testNotRaceCondition() throws Exception
    {

        HttpResponsePacket response = mock(HttpResponsePacket.class);
        MimeHeaders headers = new MimeHeaders();
        headers.setMaxNumHeaders(MIME_HEADERS_COUNT);
        for (int i = 0; i < MIME_HEADERS_COUNT; i++)
        {
            headers.addValue(HEADER_PREFIX + i);
        }
        when(response.getHeaders()).thenReturn(headers);
        final ConcurrencyTestResult result1 = new ConcurrencyTestResult();
        final ConcurrencyTestResult result2 = new ConcurrencyTestResult();
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final GrizzlyResponseHeaders grizzlyHeaders = new GrizzlyResponseHeaders(response);
        final Thread t1 = getThread(result1, latch1, grizzlyHeaders);
        final Thread t2 = getThread(result2, latch2, grizzlyHeaders);
        t1.start();
        t1.sleep(100);
        t2.start();
        latch1.await();
        latch2.await();
        assertEquals(result1.getFails(), 0);
        assertEquals(result2.getFails(), 0);

    }

    private Thread getThread(final ConcurrencyTestResult concurrencyTestResult, final CountDownLatch latch, final GrizzlyResponseHeaders grizzlyHeaders)
    {
        return new Thread()
        {

            @Override
            public void run()
            {
                try
                {
                    grizzlyHeaders.getHeaders().keySet();
                }
                catch (Exception e)
                {
                    concurrencyTestResult.addFail();
                }
                finally
                {
                    latch.countDown();
                }

            }
        };
    }

    private static final class ConcurrencyTestResult
    {
        private int fails = 0;

        public int getFails()
        {
            return fails;
        }

        public void addFail()
        {
            fails++;
        }

    }
}
