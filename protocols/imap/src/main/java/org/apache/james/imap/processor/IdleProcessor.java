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

import java.io.Closeable;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.events.Registration;
import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.IdleRequest;
import org.apache.james.imap.message.response.ContinuationResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.concurrent.NamedThreadFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class IdleProcessor extends AbstractMailboxProcessor<IdleRequest> implements CapabilityImplementingProcessor {
    private static final List<Capability> CAPS = ImmutableList.of(SUPPORTS_IDLE);
    public static final int DEFAULT_SCHEDULED_POOL_CORE_SIZE = 5;
    private static final String DONE = "DONE";

    private final EventBus eventBus;
    private TimeUnit heartbeatIntervalUnit;
    private long heartbeatInterval;
    private boolean enableIdle;
    private ScheduledExecutorService heartbeatExecutor;

    public IdleProcessor(ImapProcessor next, MailboxManager mailboxManager, EventBus eventBus, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(IdleRequest.class, next, mailboxManager, factory, metricFactory);
        this.eventBus = eventBus;
    }

    @Override
    public void configure(ImapConfiguration imapConfiguration) {
        super.configure(imapConfiguration);

        this.heartbeatInterval = imapConfiguration.getIdleTimeInterval();
        this.heartbeatIntervalUnit = imapConfiguration.getIdleTimeIntervalUnit();
        this.enableIdle = imapConfiguration.isEnableIdle();
        if (enableIdle) {
            ThreadFactory threadFactory = NamedThreadFactory.withClassName(getClass());
            this.heartbeatExecutor = Executors.newScheduledThreadPool(DEFAULT_SCHEDULED_POOL_CORE_SIZE, threadFactory);
        }
    }

    @Override
    protected void processRequest(IdleRequest request, ImapSession session, Responder responder) {
        SelectedMailbox sm = session.getSelected();
        Registration registration;
        if (sm != null) {
            registration = Mono.from(eventBus.register(new IdleMailboxListener(session, responder), new MailboxIdRegistrationKey(sm.getMailboxId())))
                .subscribeOn(Schedulers.elastic())
                .block();
        } else {
            registration = null;
        }

        final AtomicBoolean idleActive = new AtomicBoolean(true);

        session.pushLineHandler((session1, data) -> {
            String line;
            if (data.length > 2) {
                line = new String(data, 0, data.length - 2);
            } else {
                line = "";
            }

            if (registration != null) {
                registration.unregister();
            }
            session1.popLineHandler();
            if (!DONE.equals(line.toUpperCase(Locale.US))) {
                StatusResponse response = getStatusResponseFactory().taggedBad(request.getTag(), request.getCommand(), HumanReadableText.INVALID_COMMAND);
                responder.respond(response);
            } else {
                okComplete(request, responder);

            }
            idleActive.set(false);
        });

        // Check if we should send heartbeats
        if (enableIdle) {
            heartbeatExecutor.schedule(new Runnable() {

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
                        heartbeatExecutor.schedule(this, heartbeatInterval, heartbeatIntervalUnit);
                    }
                }
            }, heartbeatInterval, heartbeatIntervalUnit);
        }

        // Write the response after the listener was add
        // IMAP-341
        responder.respond(new ContinuationResponse(HumanReadableText.IDLING));
        unsolicitedResponses(session, responder, false);
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPS;
    }

    private class IdleMailboxListener implements EventListener {

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
        public void event(Event event) {
                unsolicitedResponses(session, responder, false);
        }

        @Override
        public ExecutionMode getExecutionMode() {
            return ExecutionMode.ASYNCHRONOUS;
        }
    }

    @Override
    protected Closeable addContextToMDC(IdleRequest message) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "IDLE")
            .build();
    }
}
