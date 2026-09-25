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

import static org.apache.james.imap.ImapFixture.TAG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.IdleRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class IdleProcessorLifecycleTest {

    private static class ImmediateCallbackImapSession extends FakeImapSession {
        private final Deque<ImapLineHandler> handlers = new ArrayDeque<>();
        private final AtomicInteger popCount = new AtomicInteger();
        private boolean triggerCallbackDuringPush = false;

        @Override
        public ImapProcessor.Responder threadSafe(ImapProcessor.Responder responder) {
            return responder;
        }

        @Override
        public void pushLineHandler(ImapLineHandler lineHandler) {
            handlers.push(lineHandler);
            if (triggerCallbackDuringPush) {
                // Emulate Netty event loop immediately receiving the DONE line
                // before pushLineHandler() returns to the IdleProcessor caller
                Mono.from(lineHandler.onLine(this, "DONE\r\n".getBytes(StandardCharsets.US_ASCII))).block();
            }
        }

        @Override
        public void popLineHandler() {
            popCount.incrementAndGet();
            if (!handlers.isEmpty()) {
                handlers.pop();
            }
        }
    }

    private static class RecordingResponder implements ImapProcessor.Responder {
        private final List<ImapResponseMessage> responses = new CopyOnWriteArrayList<>();

        @Override
        public void respond(ImapResponseMessage message) {
            responses.add(message);
        }

        @Override
        public void flush() {
        }

        public List<ImapResponseMessage> getResponses() {
            return responses;
        }
    }

    @Test
    void earlyCallbackDuringPushLineHandlerShouldPopHandlerExactlyOnce() {
        IdleProcessor testee = new IdleProcessor(
            mock(MailboxManager.class),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());

        ImmediateCallbackImapSession session = new ImmediateCallbackImapSession();
        session.triggerCallbackDuringPush = true;

        // Push another dummy handler before IDLE to ensure IdleProcessor doesn't pop foreign handlers
        ImapLineHandler baseHandler = (session1, data) -> Mono.empty();
        session.pushLineHandler(baseHandler);

        testee.processRequestReactive(new IdleRequest(TAG), session, new RecordingResponder()).block();

        // Verification:
        // 1. popLineHandler() was invoked exactly once for the IDLE handler
        assertThat(session.popCount.get()).isEqualTo(1);
        // 2. The IDLE handler was cleaned up and the pre-existing handler was preserved
        assertThat(session.handlers).containsExactly(baseHandler);
    }

    private static class BlockingPushImapSession extends FakeImapSession {
        private final Deque<ImapLineHandler> handlers = new ArrayDeque<>();
        private final AtomicInteger popCount = new AtomicInteger();
        private Consumer<FakeImapSession> onPush;

        @Override
        public ImapProcessor.Responder threadSafe(ImapProcessor.Responder responder) {
            return responder;
        }

        @Override
        public void pushLineHandler(ImapLineHandler lineHandler) {
            handlers.push(lineHandler);
            if (onPush != null) {
                onPush.accept(this);
            }
        }

        @Override
        public void popLineHandler() {
            popCount.incrementAndGet();
            if (!handlers.isEmpty()) {
                handlers.pop();
            }
        }
    }

    @Test
    void midPushDisconnectOrCleanupShouldPopHandlerExactlyOnceWhenPushCompletes() {
        IdleProcessor testee = new IdleProcessor(
            mock(MailboxManager.class),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());

        BlockingPushImapSession session = new BlockingPushImapSession();
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        session.selected(selectedMailbox).block();

        // Push another dummy handler before IDLE to ensure IdleProcessor doesn't pop foreign handlers
        ImapLineHandler baseHandler = (session1, data) -> Mono.empty();
        session.pushLineHandler(baseHandler);

        // When pushLineHandler executes, simulate deselect / cleanup occurring concurrently while in INSTALLING state
        session.onPush = s -> s.deselect().block();

        testee.processRequestReactive(new IdleRequest(TAG), session, new RecordingResponder()).block();

        // Verification:
        // 1. popLineHandler() was invoked exactly once
        assertThat(session.popCount.get()).isEqualTo(1);
        // 2. The IDLE handler was cleaned up and the base handler remained intact
        assertThat(session.handlers).containsExactly(baseHandler);
    }

    @Test
    void cleanupDuringRegisterIdleShouldUnregisterListenerAndNotPopHandler() {
        IdleProcessor testee = new IdleProcessor(
            mock(MailboxManager.class),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());

        BlockingPushImapSession session = new BlockingPushImapSession();
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        session.selected(selectedMailbox).block();

        ImapLineHandler baseHandler = (session1, data) -> Mono.empty();
        session.pushLineHandler(baseHandler);

        // Simulate concurrent disconnect/cleanup occurring during registerIdle execution without blocking
        doAnswer(invocation -> {
            session.deselect().block();
            return null;
        }).when(selectedMailbox).registerIdle(any());

        testee.processRequestReactive(new IdleRequest(TAG), session, new RecordingResponder()).block();

        // Verification:
        // 1. unregisterIdle was called to clean up the registered listener
        verify(selectedMailbox).unregisterIdle(any());
        // 2. IDLE line handler was never pushed, so baseHandler was not popped
        assertThat(session.popCount.get()).isZero();
        assertThat(session.handlers).containsExactly(baseHandler);
    }

    @Test
    void exceptionDuringRegisterIdleShouldUnregisterListenerAndNotPopHandler() {
        IdleProcessor testee = new IdleProcessor(
            mock(MailboxManager.class),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());

        BlockingPushImapSession session = new BlockingPushImapSession();
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        session.selected(selectedMailbox).block();

        ImapLineHandler baseHandler = (session1, data) -> Mono.empty();
        session.pushLineHandler(baseHandler);

        doThrow(new RuntimeException("Mailbox error"))
            .when(selectedMailbox).registerIdle(any());

        RecordingResponder responder = new RecordingResponder();
        testee.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        // Verification:
        // 1. unregisterIdle was called in catch block
        verify(selectedMailbox).unregisterIdle(any());
        // 2. base handler was not popped
        assertThat(session.popCount.get()).isZero();
        assertThat(session.handlers).containsExactly(baseHandler);
        // 3. NO response with GENERIC_FAILURE_DURING_PROCESSING sent to client
        assertThat(responder.getResponses()).hasSize(1);
        assertThat(responder.getResponses().get(0))
            .isInstanceOf(StatusResponse.class);
        StatusResponse statusResponse = (StatusResponse) responder.getResponses().get(0);
        assertThat(statusResponse.getServerResponseType())
            .isEqualTo(StatusResponse.Type.NO);
    }

    @Test
    void exceptionDuringUnregisterIdleShouldStillCleanUpLineHandlerStateAndSink() {
        IdleProcessor testee = new IdleProcessor(
            mock(MailboxManager.class),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());

        ImmediateCallbackImapSession session = new ImmediateCallbackImapSession();
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        session.selected(selectedMailbox).block();

        doThrow(new RuntimeException("Unregister failed"))
            .when(selectedMailbox).unregisterIdle(any());

        ImapLineHandler baseHandler = (session1, data) -> Mono.empty();
        session.pushLineHandler(baseHandler);

        session.triggerCallbackDuringPush = true;

        RecordingResponder responder = new RecordingResponder();
        testee.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        // Verification:
        // 1. popLineHandler() was invoked despite unregisterIdle throwing
        assertThat(session.popCount.get()).isEqualTo(1);
        assertThat(session.handlers).containsExactly(baseHandler);
        // 2. unregisterIdle was attempted
        verify(selectedMailbox).unregisterIdle(any());
        // 3. Response was delivered cleanly
        assertThat(responder.getResponses()).hasSize(1);
    }

    @Test
    void unregisterIdleRetryAfterFailureShouldSucceed() {
        IdleProcessor testee = new IdleProcessor(
            mock(MailboxManager.class),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());

        BlockingPushImapSession session = new BlockingPushImapSession();
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        session.selected(selectedMailbox).block();

        // First attempt throws, second succeeds
        doThrow(new RuntimeException("Transient failure"))
            .doNothing()
            .when(selectedMailbox).unregisterIdle(any());

        ImapLineHandler baseHandler = (session1, data) -> Mono.empty();
        session.pushLineHandler(baseHandler);

        // When push happens, throw an error in pushLineHandler to trigger catch block cleanup
        session.onPush = s -> {
            throw new RuntimeException("Push failed");
        };

        RecordingResponder responder = new RecordingResponder();
        testee.processRequestReactive(new IdleRequest(TAG), session, responder).block();

        // Verification:
        // 1. unregisterIdle was retried during error handling and called twice (initial catch and onErrorResume)
        verify(selectedMailbox, org.mockito.Mockito.times(2)).unregisterIdle(any());
        // 2. Base handler was preserved
        assertThat(session.handlers).containsExactly(baseHandler);
        // 3. Error response sent
        assertThat(responder.getResponses()).hasSize(1);
    }
}


