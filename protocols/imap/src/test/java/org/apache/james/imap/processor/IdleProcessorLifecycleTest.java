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
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapProcessor;
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
        @Override
        public void respond(ImapResponseMessage message) {
        }

        @Override
        public void flush() {
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

        // Push another dummy handler before IDLE to ensure IdleProcessor doesn't pop foreign handlers
        ImapLineHandler baseHandler = (session1, data) -> Mono.empty();
        session.pushLineHandler(baseHandler);

        // When pushLineHandler executes, simulate session disconnect / cleanup happening concurrently
        session.onPush = s -> s.close();

        testee.processRequestReactive(new IdleRequest(TAG), session, new RecordingResponder()).block();

        // Verification:
        // 1. popLineHandler() was invoked exactly once
        assertThat(session.popCount.get()).isEqualTo(1);
        // 2. The IDLE handler was cleaned up and the base handler remained intact
        assertThat(session.handlers).containsExactly(baseHandler);
    }
}
