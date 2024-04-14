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

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class IdleProcessor extends AbstractMailboxProcessor<IdleRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(IdleProcessor.class);

    private static final List<Capability> CAPS = ImmutableList.of(SUPPORTS_IDLE);
    private static final String DONE = "DONE";

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
        this.enableIdle = imapConfiguration.isEnableIdle();
    }

    @Override
    protected Mono<Void> processRequestReactive(IdleRequest request, ImapSession session, Responder responder) {
        SelectedMailbox sm = session.getSelected();
        if (sm != null) {
            sm.registerIdle(new IdleMailboxListener(session, responder));
        }

        final AtomicBoolean idleActive = new AtomicBoolean(true);

        session.pushLineHandler((session1, data) -> {
            String line;
            if (data.length > 2) {
                line = new String(data, 0, data.length - 2);
            } else {
                line = "";
            }

            if (sm != null) {
                sm.unregisterIdle();
            }
            if (!DONE.equals(line.toUpperCase(Locale.US))) {
                String message = String.format("Continuation for IMAP IDLE was not understood. Expected 'DONE', got '%s'.", line);
                StatusResponse response = getStatusResponseFactory()
                    .taggedBad(request.getTag(), request.getCommand(),
                        new HumanReadableText("org.apache.james.imap.INVALID_CONTINUATION",
                            "failed. " + message));
                LOGGER.info(message);
                responder.respond(response);
                responder.flush();
            } else {
                okComplete(request, responder);
                responder.flush();
            }
            session1.popLineHandler();
            idleActive.set(false);
        });

        // Check if we should send heartbeats
        if (enableIdle) {
            session.schedule(new Runnable() {

                @Override
                public void run() {
                    // check if we need to cancel the Runnable
                    // See IMAP-275
                    if (session.getState() != ImapSessionState.LOGOUT && idleActive.get()) {
                        // Send a heartbeat to the client to make sure we
                        // reset the idle timeout. This is kind of the same
                        // workaround as dovecot use.
                        //
                        // This is mostly needed because of the broken
                        // outlook client, but can't harm for other clients
                        // too.
                        // See IMAP-272
                        StatusResponse response = getStatusResponseFactory().untaggedOk(HumanReadableText.HEARTBEAT);
                        responder.respond(response);

                        // schedule the heartbeat again for the next interval
                        session.schedule(this, heartbeatInterval);
                    }
                }
            }, heartbeatInterval);
        }

        // Write the response after the listener was add
        // IMAP-341
        responder.respond(new ContinuationResponse(HumanReadableText.IDLING));
        return unsolicitedResponses(session, responder, false);
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPS;
    }

    private class IdleMailboxListener implements EventListener.ReactiveEventListener {

        private final Responder responder;
        private final ImapSession session;

        public IdleMailboxListener(ImapSession session, Responder responder) {
            this.session = session;
            this.responder = responder;
        }

        @Override
        public boolean isHandling(Event event) {
            return event instanceof Added || event instanceof Expunged || event instanceof FlagsUpdated;
        }

        @Override
        public Publisher<Void> reactiveEvent(Event event) {
            return unsolicitedResponses(session, responder, false)
                .then(Mono.fromRunnable(responder::flush));
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
