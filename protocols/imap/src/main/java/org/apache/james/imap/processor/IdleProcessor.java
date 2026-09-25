/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.imap.processor;

import static org.apache.james.imap.api.ImapConstants.SUPPORTS_IDLE;
import static org.apache.james.util.ReactorUtils.logAsMono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.IdleRequest;
import org.apache.james.imap.message.response.ContinuationResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public class IdleProcessor extends AbstractMailboxProcessor<IdleRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(IdleProcessor.class);

    private static final List<Capability> CAPS = ImmutableList.of(SUPPORTS_IDLE);
    private static final String DONE = "DONE";

    private enum LineHandlerState {
        NOT_INSTALLED,
        INSTALLING,
        INSTALLED,
        REMOVAL_PENDING,
        REMOVED
    }

    private Duration heartbeatInterval;
    private boolean enableIdle;

    @Inject
    public IdleProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                         MetricFactory metricFactory) {
        super(IdleRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    public void configure(ImapConfiguration imapConfiguration) {
        super.configure(imapConfiguration);

        this.heartbeatInterval = imapConfiguration.idleTimeIntervalAsDuration();
        this.enableIdle = imapConfiguration.isEnableIdle() && !heartbeatInterval.isZero() && !heartbeatInterval.isNegative();
    }

    @Override
    protected Mono<Void> processRequestReactive(IdleRequest request, ImapSession session, Responder responder) {
        Responder safeResponder = session.threadSafe(responder);
        SelectedMailbox selectedMailbox = session.getSelected();
        Sinks.One<Void> idleReadySink = Sinks.one();
        AtomicBoolean idleActive = new AtomicBoolean(true);
        AtomicReference<LineHandlerState> lineHandlerState = new AtomicReference<>(LineHandlerState.NOT_INSTALLED);
        AtomicReference<EventListener.ReactiveEventListener> idleListenerRef = new AtomicReference<>();
        return Mono.fromRunnable(() -> idle(request, session, safeResponder, selectedMailbox, idleReadySink, idleActive, lineHandlerState, idleListenerRef))
            .then(unsolicitedResponses(session, safeResponder, false))
            .onErrorResume(e -> {
                cleanupIdle(session, selectedMailbox, idleActive, lineHandlerState, idleReadySink, idleListenerRef.get());
                no(request, safeResponder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
                return logAsMono(() -> LOGGER.error("Encountered error executing IMAP IDLE", e));
            })
            .doFinally(signalType -> idleReadySink.tryEmitEmpty());
    }

    private boolean cleanupIdle(ImapSession session, SelectedMailbox selectedMailbox, AtomicBoolean idleActive,
                                AtomicReference<LineHandlerState> lineHandlerState, Sinks.One<Void> idleReadySink,
                                EventListener.ReactiveEventListener idleListener) {
        boolean cleanupOwner = idleActive.compareAndSet(true, false);
        if (cleanupOwner) {
            if (selectedMailbox != null && idleListener != null) {
                try {
                    selectedMailbox.unregisterIdle(idleListener);
                } catch (Exception e) {
                    LOGGER.debug("Failed to unregister IDLE listener", e);
                }
            }
            LineHandlerState previous = lineHandlerState.getAndUpdate(state -> {
                if (state == LineHandlerState.INSTALLING) {
                    return LineHandlerState.REMOVAL_PENDING;
                }
                if (state == LineHandlerState.INSTALLED) {
                    return LineHandlerState.REMOVED;
                }
                return state;
            });
            try {
                if (previous == LineHandlerState.INSTALLED && session != null) {
                    session.popLineHandler();
                }
            } finally {
                idleReadySink.tryEmitEmpty();
            }
            return true;
        }
        return false;
    }

    private EventListener.ReactiveEventListener registerIdleListener(ImapSession session, Responder safeResponder,
                                                                    SelectedMailbox selectedMailbox, Sinks.One<Void> idleReadySink,
                                                                    AtomicBoolean idleActive, AtomicReference<LineHandlerState> lineHandlerState,
                                                                    AtomicReference<EventListener.ReactiveEventListener> idleListenerRef) {
        EventListener.ReactiveEventListener idleListener = null;
        try {
            if (selectedMailbox != null) {
                idleListener = new IdleMailboxListener(session, safeResponder, idleReadySink, idleActive);
                idleListenerRef.set(idleListener);
                selectedMailbox.registerIdle(idleListener);
            } else {
                idleReadySink.tryEmitEmpty();
            }

            if (!idleActive.get()) {
                cleanupIdle(session, selectedMailbox, idleActive, lineHandlerState, idleReadySink, idleListener);
                return null;
            }
            return idleListener;
        } catch (Exception e) {
            try {
                cleanupIdle(session, selectedMailbox, idleActive, lineHandlerState, idleReadySink, idleListener);
            } catch (Exception cleanupException) {
                e.addSuppressed(cleanupException);
            } finally {
                lineHandlerState.set(LineHandlerState.REMOVED);
            }
            throw e;
        }
    }

    private ImapLineHandler createIdleLineHandler(IdleRequest request, Responder safeResponder, SelectedMailbox selectedMailbox,
                                                  Sinks.One<Void> idleReadySink, AtomicBoolean idleActive,
                                                  AtomicReference<LineHandlerState> lineHandlerState,
                                                  EventListener.ReactiveEventListener idleListener) {
        return (session1, data) -> {
            if (!idleActive.get()) {
                return Mono.empty();
            }
            lineHandlerState.compareAndSet(LineHandlerState.INSTALLING, LineHandlerState.INSTALLED);
            if (!cleanupIdle(session1, selectedMailbox, idleActive, lineHandlerState, idleReadySink, idleListener)) {
                // IDLE was already cleaned up by another thread (heartbeat, disconnect, etc.)
                return Mono.empty();
            }
            String line = new String(data, StandardCharsets.US_ASCII).trim();

            if (!session1.isConnected()) {
                LOGGER.debug("IDLE continuation received disconnected session.");
                return Mono.empty();
            }
            String upper = line.toUpperCase(Locale.ROOT);
            if (DONE.equals(upper)) {
                okComplete(request, safeResponder);
                safeResponder.flush();
                return Mono.empty();
            }
            String displayLine = sanitizeForDisplay(line);
            String message = String.format("Continuation for IMAP IDLE was not understood. Expected 'DONE', got '%s'.", displayLine);
            StatusResponse response = getStatusResponseFactory()
                .taggedBad(request.getTag(), request.getCommand(),
                    new HumanReadableText("org.apache.james.imap.INVALID_CONTINUATION",
                        "failed. " + message));
            LOGGER.debug(message);
            safeResponder.respond(response);
            safeResponder.flush();
            return Mono.empty();
        };
    }

    private void installLineHandler(IdleRequest request, ImapSession session, Responder safeResponder, SelectedMailbox selectedMailbox,
                                    Sinks.One<Void> idleReadySink, AtomicBoolean idleActive,
                                    AtomicReference<LineHandlerState> lineHandlerState,
                                    EventListener.ReactiveEventListener idleListener) {
        try {
            session.pushLineHandler(createIdleLineHandler(request, safeResponder, selectedMailbox, idleReadySink, idleActive, lineHandlerState, idleListener));
            LineHandlerState previous = lineHandlerState.getAndUpdate(state -> {
                if (state == LineHandlerState.INSTALLING) {
                    return LineHandlerState.INSTALLED;
                }
                if (state == LineHandlerState.REMOVAL_PENDING) {
                    return LineHandlerState.REMOVED;
                }
                return state;
            });
            if (previous == LineHandlerState.REMOVAL_PENDING) {
                // cleanupIdle was called while pushLineHandler was in progress;
                // cleanupIdle couldn't pop the handler because pushLineHandler had not completed yet,
                // so pop it now that pushLineHandler has completed.
                session.popLineHandler();
            }
        } catch (Exception e) {
            try {
                cleanupIdle(session, selectedMailbox, idleActive, lineHandlerState, idleReadySink, idleListener);
            } catch (Exception cleanupException) {
                e.addSuppressed(cleanupException);
            } finally {
                lineHandlerState.set(LineHandlerState.REMOVED);
            }
            throw e;
        }
    }

    private void scheduleHeartbeat(ImapSession session, Responder safeResponder, SelectedMailbox selectedMailbox,
                                   Sinks.One<Void> idleReadySink, AtomicBoolean idleActive,
                                   AtomicReference<LineHandlerState> lineHandlerState,
                                   EventListener.ReactiveEventListener idleListener) {
        if (!enableIdle) {
            return;
        }
        session.schedule(new Runnable() {
            @Override
            public void run() {
                if (session.isConnected() && session.getState() != ImapSessionState.LOGOUT && idleActive.get()) {
                    try {
                        StatusResponse response = getStatusResponseFactory().untaggedOk(HumanReadableText.HEARTBEAT);
                        safeResponder.respond(response);
                        safeResponder.flush();

                        if (idleActive.get() && session.isConnected() && session.getState() != ImapSessionState.LOGOUT) {
                            session.schedule(this, heartbeatInterval);
                        }
                    } catch (Exception e) {
                        LOGGER.debug("Failed to send IMAP IDLE heartbeat, stopping keepalive task", e);
                        cleanupIdle(session, selectedMailbox, idleActive, lineHandlerState, idleReadySink, idleListener);
                    }
                } else {
                    cleanupIdle(session, selectedMailbox, idleActive, lineHandlerState, idleReadySink, idleListener);
                }
            }
        }, heartbeatInterval);
    }

    private void idle(IdleRequest request, ImapSession session, Responder safeResponder, SelectedMailbox selectedMailbox,
                      Sinks.One<Void> idleReadySink, AtomicBoolean idleActive, AtomicReference<LineHandlerState> lineHandlerState,
                      AtomicReference<EventListener.ReactiveEventListener> idleListenerRef) {
        if (!lineHandlerState.compareAndSet(LineHandlerState.NOT_INSTALLED, LineHandlerState.INSTALLING)) {
            return;
        }

        EventListener.ReactiveEventListener idleListener = registerIdleListener(session, safeResponder, selectedMailbox,
            idleReadySink, idleActive, lineHandlerState, idleListenerRef);
        if (idleListener == null && selectedMailbox != null) {
            return;
        }

        installLineHandler(request, session, safeResponder, selectedMailbox, idleReadySink, idleActive,
            lineHandlerState, idleListener);

        if (!idleActive.get()) {
            return;
        }

        // Write the response after the listener was added (IMAP-341)
        safeResponder.respond(new ContinuationResponse(HumanReadableText.IDLING));
        safeResponder.flush();

        scheduleHeartbeat(session, safeResponder, selectedMailbox, idleReadySink, idleActive,
            lineHandlerState, idleListener);
    }

    @VisibleForTesting
    static String sanitizeForDisplay(String line) {
        StringBuilder sanitized = new StringBuilder(Math.min(line.length(), 32));
        boolean truncated = false;
        for (int i = 0; i < line.length(); i++) {
            if (sanitized.length() == 32) {
                truncated = true;
                break;
            }
            char c = line.charAt(i);
            if (c >= 32 && c < 127) {
                sanitized.append(c);
            }
        }
        return truncated ? sanitized + "..." : sanitized.toString();
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPS;
    }

    private class IdleMailboxListener implements EventListener.ReactiveEventListener {

        private final Responder responder;
        private final ImapSession session;
        private final Sinks.One<Void> idleReadySink;
        private final AtomicBoolean idleActive;

        public IdleMailboxListener(ImapSession session, Responder responder, Sinks.One<Void> idleReadySink,
                                   AtomicBoolean idleActive) {
            this.session = session;
            this.responder = responder;
            this.idleReadySink = idleReadySink;
            this.idleActive = idleActive;
        }

        @Override
        public boolean isHandling(Event event) {
            return event instanceof Added || event instanceof Expunged || event instanceof FlagsUpdated;
        }

        @Override
        public Publisher<Void> reactiveEvent(Event event) {
            return idleReadySink.asMono()
                .then(Mono.defer(() -> {
                    if (!idleActive.get()) {
                        return Mono.empty();
                    }
                    return unsolicitedResponses(session, responder, false)
                        .then(Mono.fromRunnable(responder::flush));
                }))
                .onErrorResume(e -> logAsMono(() -> LOGGER.debug("Failed to push updates to idling client", e)))
                .then();
        }

        @Override
        public ExecutionMode getExecutionMode() {
            return ExecutionMode.ASYNCHRONOUS;
        }
    }

    @Override
    protected MDCBuilder mdc(IdleRequest message) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "IDLE");
    }
}
