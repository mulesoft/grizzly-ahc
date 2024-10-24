/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.ning.http.client.providers.grizzly;

import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.InvokeAction;
import org.glassfish.grizzly.filterchain.NextAction;

/**
 * Package-private helper used to pause and resume the event processing of a {@link FilterChainContext}.
 * Apart from the base suspend/resume mechanism from Grizzly, what this helper offers is
 * {@link #pauseIfNeeded(FilterChainContext,NextAction) a method} to attach an {@link NextAction action} to the
 * context, and {@link #resumeFromPausedAction(FilterChainContext) another method} to resume the execution starting
 * from that attached action.
 * This class is intended to be used only by {@link AhcEventFilter}.
 */
final class PauseContextHelper {

    private PauseContextHelper() {
        // private empty constructor to avoid wrong instantiation.
    }

    /**
     * Marks the {@link FilterChainContext} so that the event filter knows that should pause the event processing after
     * the current action.
     * @param ctx the {@link FilterChainContext} to be paused.
     */
    public static void requestPause(FilterChainContext ctx) {
        synchronized (ctx) {
            PauseContext pauseContext = getPauseCtxFromAttribute(ctx);
            if (null == pauseContext) {
                setPauseCtxAttribute(ctx, new PauseContext(null));
            } else {
                throw new IllegalStateException("Can't pause an already paused context");
            }
        }
    }

    /**
     * If the context has to be paused, it attaches the passed pausedAction to the {@link FilterChainContext} so that
     * it can be resumed starting from that action later.
     * @param ctx the {@link FilterChainContext} being paused.
     * @param pausedAction the action to save.
     * @return if the context has to be paused, it returns a suspend action. Otherwise, it just returns the same action
     *         that was received as parameter.
     */
    public static NextAction pauseIfNeeded(FilterChainContext ctx, NextAction pausedAction) {
        if (null == pausedAction || InvokeAction.TYPE != pausedAction.type()) {
            return pausedAction;
        }

        if (null == getPauseCtxFromAttribute(ctx)) {
            return pausedAction;
        }

        synchronized (ctx) {
            PauseContext pauseContext = getPauseCtxFromAttribute(ctx);
            if (pauseContext != null && pauseContext.getPausedAction() != null) {
                throw new IllegalStateException("Can't override a paused action");
            }

            if (pauseContext == null) {
                // it was resumed before this method could be executed
                return pausedAction;
            }

            setPauseCtxAttribute(ctx, new PauseContext(pausedAction));
            return ctx.getSuspendAction();
        }
    }

    /**
     * Resumes the event processing with the action saved by {@link #pauseIfNeeded(FilterChainContext, NextAction)},
     * and clears the corresponding attribute.
     * If the action wasn't saved yet, it only clears the attribute and doesn't call resume.
     * @param ctx the {@link FilterChainContext} to resume.
     */
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
        if (null == attribute) {
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