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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapProcessor.Responder;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.IdleRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import reactor.core.publisher.Mono;

class IdleProcessorTest {

    private static final Tag TAG = new Tag("a1");

    private MailboxManager mailboxManager;
    private StatusResponseFactory statusResponseFactory;
    private IdleProcessor processor;
    private ImapSession session;
    private SelectedMailbox selectedMailbox;
    private List<ImapResponseMessage> responses;
    private Responder responder;
    private AtomicReference<ImapLineHandler> lineHandlerRef;
    private AtomicBoolean connectedRef;

    @BeforeEach
    void setUp() {
        mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.manageProcessing(any(), any())).thenAnswer((Answer<Mono<Void>>) invocation -> invocation.getArgument(0));

        statusResponseFactory = new UnpooledStatusResponseFactory();
        processor = new IdleProcessor(mailboxManager, statusResponseFactory, new RecordingMetricFactory());

        responses = new ArrayList<>();
        responder = new Responder() {
            @Override
            public void respond(ImapResponseMessage message) {
                responses.add(message);
            }

            @Override
            public void flush() {
            }
        };

        selectedMailbox = mock(SelectedMailbox.class);
        lineHandlerRef = new AtomicReference<>();
        connectedRef = new AtomicBoolean(true);

        session = mock(ImapSession.class);
        when(session.getState()).thenReturn(ImapSessionState.SELECTED);
        when(session.getSelected()).thenReturn(selectedMailbox);
        when(session.isConnected()).thenAnswer(inv -> connectedRef.get());
        when(session.threadSafe(any())).thenAnswer(inv -> inv.getArgument(0));

        org.mockito.Mockito.doAnswer(inv -> {
            lineHandlerRef.set(inv.getArgument(0));
            return null;
        }).when(session).pushLineHandler(any(ImapLineHandler.class));

        org.mockito.Mockito.doAnswer(inv -> {
            lineHandlerRef.set(null);
            return null;
        }).when(session).popLineHandler();
    }

    @Test
    void getImplementedCapabilitiesShouldIncludeIdle() {
        assertThat(processor.getImplementedCapabilities(session))
            .contains(Capability.of("IDLE"));
    }

    @Test
    void unselectedIdleShouldSucceedAndNotRegisterListener() {
        when(session.getSelected()).thenReturn(null);

        processor.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        verify(selectedMailbox, never()).registerIdle(any());
        assertThat(responses).isNotEmpty();
        assertThat(responses.get(0).toString()).contains("Idling");
    }

    @Test
    void idleShouldRegisterListenerOnSelectedMailbox() {
        processor.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        verify(selectedMailbox).registerIdle(any());
        assertThat(lineHandlerRef.get()).isNotNull();
    }

    @Test
    void doneShouldCompleteIdleAndUnregisterListener() {
        processor.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        ImapLineHandler handler = lineHandlerRef.get();
        assertThat(handler).isNotNull();

        Mono.from(handler.onLine(session, "DONE\r\n".getBytes(StandardCharsets.US_ASCII))).block();

        verify(selectedMailbox).unregisterIdle();
        verify(session).popLineHandler();
        assertThat(lineHandlerRef.get()).isNull();

        assertThat(responses)
            .filteredOn(r -> r instanceof StatusResponse)
            .map(r -> ((StatusResponse) r).getServerResponseType())
            .contains(StatusResponse.Type.OK);
    }

    @Test
    void invalidContinuationShouldRespondBadAndUnregisterListener() {
        processor.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        ImapLineHandler handler = lineHandlerRef.get();
        assertThat(handler).isNotNull();

        Mono.from(handler.onLine(session, "INVALID_COMMAND\r\n".getBytes(StandardCharsets.US_ASCII))).block();

        verify(selectedMailbox).unregisterIdle();
        verify(session).popLineHandler();
        assertThat(lineHandlerRef.get()).isNull();

        assertThat(responses)
            .filteredOn(r -> r instanceof StatusResponse)
            .map(r -> ((StatusResponse) r).getServerResponseType())
            .contains(StatusResponse.Type.BAD);
    }

    @Test
    void emptyLineDisconnectShouldDropWithoutBadResponse() {
        processor.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        ImapLineHandler handler = lineHandlerRef.get();
        assertThat(handler).isNotNull();

        Mono.from(handler.onLine(session, "".getBytes(StandardCharsets.US_ASCII))).block();

        verify(selectedMailbox).unregisterIdle();
        verify(session).popLineHandler();
        assertThat(lineHandlerRef.get()).isNull();

        assertThat(responses)
            .filteredOn(r -> r instanceof StatusResponse)
            .map(r -> ((StatusResponse) r).getServerResponseType())
            .doesNotContain(StatusResponse.Type.BAD);
    }

    @Test
    void heartbeatShouldAbortWhenSessionDisconnected() {
        processor.configure(ImapConfiguration.builder()
            .enableIdle(true)
            .idleTimeInterval(1)
            .build());

        AtomicReference<Runnable> scheduledRunnable = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(inv -> {
            scheduledRunnable.set(inv.getArgument(0));
            return null;
        }).when(session).schedule(any(Runnable.class), any(Duration.class));

        processor.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        Runnable heartbeatTask = scheduledRunnable.get();
        assertThat(heartbeatTask).isNotNull();

        // Simulate disconnection
        connectedRef.set(false);

        // Run heartbeat task
        heartbeatTask.run();

        // Should unregister and pop handler
        verify(selectedMailbox).unregisterIdle();
        verify(session).popLineHandler();
    }
}
