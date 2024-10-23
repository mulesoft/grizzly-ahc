package com.ning.http.client;

import org.glassfish.grizzly.filterchain.FilterChainContext;

public class PauseHandler {

    private final FilterChainContext ctx;

    public PauseHandler(FilterChainContext ctx) {
        this.ctx = ctx;
    }

    public void requestPause() {
        PauseContextHelper.requestPause(ctx);
    }

    public void resume() {
        PauseContextHelper.resumeFromPausedAction(ctx);
    }
}
