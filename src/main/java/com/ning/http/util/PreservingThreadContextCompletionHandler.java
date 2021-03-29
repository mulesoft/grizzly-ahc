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
package com.ning.http.util;

import static java.lang.Thread.currentThread;

import java.io.Closeable;
import java.util.Map;

import org.glassfish.grizzly.CompletionHandler;
import org.slf4j.MDC;

public class PreservingThreadContextCompletionHandler<E> implements CompletionHandler<E> {

    private final CompletionHandler<E> delegate;
    private final ClassLoader classLoader;
    private final Map<String, String> mdc;

    public PreservingThreadContextCompletionHandler(CompletionHandler<E> delegate) {
        this.delegate = delegate;
        this.classLoader = currentThread().getContextClassLoader();
        this.mdc = MDC.getCopyOfContextMap();
    }

    @Override
    public void cancelled() {
        ThreadContext tc = new ThreadContext(classLoader, mdc);
        try {
            delegate.cancelled();
        } finally {
            tc.close();
        }
    }

    @Override
    public void failed(Throwable throwable) {
        ThreadContext tc = new ThreadContext(classLoader, mdc);
        try {
            delegate.failed(throwable);
        } finally {
            tc.close();
        }
    }

    @Override
    public void completed(E e) {
        ThreadContext tc = new ThreadContext(classLoader, mdc);
        try {
            delegate.completed(e);
        } finally {
            tc.close();
        }
    }

    @Override
    public void updated(E e) {
        ThreadContext tc = new ThreadContext(classLoader, mdc);
        try {
            delegate.updated(e);
        } finally {
            tc.close();
        }
    }

    private static class ThreadContext implements Closeable {

        private final Thread currentThread;

        private final ClassLoader innerClassLoader;
        private final Map<String, String> innerMDC;

        private final ClassLoader outerClassLoader;
        private final Map<String, String> outerMDC;

        ThreadContext(ClassLoader classLoader, Map<String, String> mdc) {
            currentThread = currentThread();

            innerClassLoader = classLoader;
            innerMDC = mdc;

            outerClassLoader = currentThread.getContextClassLoader();
            outerMDC = MDC.getCopyOfContextMap();

            if (innerMDC != null) {
                MDC.setContextMap(innerMDC);
            }
            setContextClassLoader(currentThread, outerClassLoader, innerClassLoader);
        }

        private void setContextClassLoader(Thread thread, ClassLoader currentClassLoader, ClassLoader newClassLoader) {
            if (currentClassLoader != newClassLoader) {
                thread.setContextClassLoader(newClassLoader);
            }
        }

        @Override
        public void close() {
            try {
                setContextClassLoader(currentThread, innerClassLoader, outerClassLoader);
            } finally {
                if (innerMDC != null && outerMDC != null) {
                    MDC.setContextMap(outerMDC);
                }
            }
        }
    }
}
