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

import jakarta.inject.Inject;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
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
    private static final int MAX_DISPLAY_LENGTH = 32;

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
        AtomicBoolean lineHandlerAdded = new AtomicBoolean(false);

        IdleMailboxListener idleListener = selectedMailbox != null
            ? new IdleMailboxListener(session, safeResponder, idleReadySink, idleActive)
            : null;

        return Mono.fromRunnable(() -> idle(request, session, safeResponder, selectedMailbox, idleReadySink, idleActive, lineHandlerAdded, idleListener))
            .then(unsolicitedResponses(session, safeResponder, false))
            .onErrorResume(e -> {
                cleanupIdle(session, selectedMailbox, idleActive, lineHandlerAdded, idleReadySink, idleListener);
                no(request, safeResponder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
                return logAsMono(() -> LOGGER.error("Encountered error executing IMAP IDLE", e));
            })
            .doFinally(signalType -> idleReadySink.tryEmitEmpty());
    }

    private boolean cleanupIdle(ImapSession session, SelectedMailbox selectedMailbox, AtomicBoolean idleActive,
                                AtomicBoolean lineHandlerAdded, Sinks.One<Void> idleReadySink,
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
            try {
                if (session != null && lineHandlerAdded.compareAndSet(true, false)) {
                    session.popLineHandler();
                }
            } finally {
                idleReadySink.tryEmitEmpty();
            }
            return true;
        }
        return false;
    }

    private void idle(IdleRequest request, ImapSession session, Responder safeResponder, SelectedMailbox selectedMailbox,
                      Sinks.One<Void> idleReadySink, AtomicBoolean idleActive, AtomicBoolean lineHandlerAdded,
                      IdleMailboxListener idleListener) {
        try {
            if (selectedMailbox != null) {
                selectedMailbox.registerIdle(idleListener);
            } else {
                idleReadySink.tryEmitEmpty();
            }

            lineHandlerAdded.set(true);
            try {
                session.pushLineHandler((session1, data) -> {
                    if (!idleActive.get()) {
                        return Mono.empty();
                    }
                    cleanupIdle(session1, selectedMailbox, idleActive, lineHandlerAdded, idleReadySink, idleListener);
                    String line = new String(data, StandardCharsets.US_ASCII).trim();
                    if (!session1.isConnected()) {
                        LOGGER.debug("IDLE continuation received disconnected session.");
                        return Mono.empty();
                    }

                    if (DONE.equals(line.toUpperCase(Locale.ROOT))) {
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
                });
            } catch (Exception e) {
                lineHandlerAdded.set(false);
                throw e;
            }

            // Write continuation response after listener and handler are installed (IMAP-341), only if still active
            if (idleActive.get()) {
                safeResponder.respond(new ContinuationResponse(HumanReadableText.IDLING));
                safeResponder.flush();
            }

            if (enableIdle) {
                scheduleHeartbeat(session, safeResponder, selectedMailbox, idleReadySink, idleActive, lineHandlerAdded, idleListener);
            }
        } catch (Exception e) {
            cleanupIdle(session, selectedMailbox, idleActive, lineHandlerAdded, idleReadySink, idleListener);
            throw e;
        }
    }

    private void scheduleHeartbeat(ImapSession session, Responder safeResponder, SelectedMailbox selectedMailbox,
                                   Sinks.One<Void> idleReadySink, AtomicBoolean idleActive, AtomicBoolean lineHandlerAdded,
                                   EventListener.ReactiveEventListener idleListener) {
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
                        cleanupIdle(session, selectedMailbox, idleActive, lineHandlerAdded, idleReadySink, idleListener);
                    }
                } else {
                    cleanupIdle(session, selectedMailbox, idleActive, lineHandlerAdded, idleReadySink, idleListener);
                }
            }
        }, heartbeatInterval);
    }

    @VisibleForTesting
    static String sanitizeForDisplay(String line) {
        StringBuilder sanitized = new StringBuilder(Math.min(line.length(), MAX_DISPLAY_LENGTH));
        boolean truncated = false;
        for (int i = 0; i < line.length(); i++) {
            if (sanitized.length() == MAX_DISPLAY_LENGTH) {
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
                .onErrorResume(e -> logAsMono(() -> LOGGER.info("Failed to push updates to idling client", e)))
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
