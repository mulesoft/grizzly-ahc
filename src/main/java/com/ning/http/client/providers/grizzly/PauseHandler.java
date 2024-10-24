/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.ning.http.client.providers.grizzly;

import com.ning.http.client.AsyncHandler;
import org.glassfish.grizzly.filterchain.FilterChainContext;

/**
 * Handler to pause/resume event processing. Useful when an {@link AsyncHandler}'s resources are saturated, and it can't
 * handle a piece of content temporarily. If the handler uses the {@link #requestPause()}  method, it's also responsible
 * for calling {@link PauseHandler#resume()} when it could handle the part correctly.
 */
public class PauseHandler {

    private final FilterChainContext ctx;

    public PauseHandler(FilterChainContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Marks the underlying {@link FilterChainContext} so that the event filter knows that should pause the event
     * processing after the current action.
     * If the user calls this method, it's responsible to resume the event processing by calling {@link #resume()}.
     */
    public void requestPause() {
        PauseContextHelper.requestPause(ctx);
    }

    /**
     * Resumes the event processing with the action saved when paused.
     * If a pause action wasn't saved yet (because this method is called before the actual pause happens), it doesn't
     * call resume.
     */
    public void resume() {
        PauseContextHelper.resumeFromPausedAction(ctx);
    }
}
