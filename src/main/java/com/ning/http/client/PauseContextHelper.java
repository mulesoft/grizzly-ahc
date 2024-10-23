package com.ning.http.client;

import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

public final class PauseContextHelper {

    private PauseContextHelper() {
        // private empty constructor to avoid wrong instantiation.
    }

    public static void requestPause(FilterChainContext ctx) {
        synchronized (ctx) {
            PauseContext pauseContext = getPauseCtxFromAttribute(ctx);
            if (pauseContext == null) {
                setPauseCtxAttribute(ctx, new PauseContext(null));
            } else {
                throw new IllegalStateException("Can't pause an already paused context");
            }
        }
    }

    public static boolean isPauseRequested(FilterChainContext ctx) {
        synchronized (ctx) {
            PauseContext pauseContext = getPauseCtxFromAttribute(ctx);
            if (pauseContext == null) {
                return false;
            }
            return pauseContext.getPausedAction() == null;
        }
    }

    public static void savePausedAction(FilterChainContext ctx, NextAction pausedAction) {
        synchronized (ctx) {
            PauseContext pauseContext = getPauseCtxFromAttribute(ctx);
            if (pauseContext != null && pauseContext.getPausedAction() != null) {
                throw new IllegalStateException("Can't override a paused action");
            }

            if (pauseContext == null) {
                // it was resumed
                return;
            }

            setPauseCtxAttribute(ctx, new PauseContext(pausedAction));
        }
    }

    public static void resumeFromPausedAction(FilterChainContext ctx) {
        synchronized (ctx) {
            PauseContext pauseContext = getPauseCtxFromAttribute(ctx);
            if (pauseContext == null) {
                throw new IllegalStateException("Can't resume a non-paused context");
            }

            removePauseCtxAttribute(ctx);

            if (pauseContext.getPausedAction() == null) {
                // resuming before actually pausing
                return;
            }

            ctx.resume(pauseContext.getPausedAction());
        }
    }

    private static void removePauseCtxAttribute(FilterChainContext ctx) {
        ctx.getAttributes().removeAttribute(PauseContext.NAME);
    }

    private static void setPauseCtxAttribute(FilterChainContext ctx, PauseContext pauseContext) {
        ctx.getAttributes().setAttribute(PauseContext.NAME, pauseContext);
    }

    private static PauseContext getPauseCtxFromAttribute(FilterChainContext ctx) {
        Object attribute = ctx.getAttributes().getAttribute(PauseContext.NAME);
        if (attribute == null) {
            return null;
        }
        if (attribute instanceof PauseContext) {
            return (PauseContext) attribute;
        }
        return null;
    }

    private static final class PauseContext {

        private static final String NAME = "PauseContextAttribute";
        private final NextAction pausedAction;

        public PauseContext(NextAction pausedAction) {
            this.pausedAction = pausedAction;
        }

        public NextAction getPausedAction() {
            return pausedAction;
        }
    }
}