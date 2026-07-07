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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.AuthenticateRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.sasl.JamesSaslAuthenticator;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

class AuthenticateProcessorTest {
    private static final String BROKEN_MECHANISM = "BROKEN";
    private static final Username USER = Username.of("user@example.com");
    private static final SaslIdentity IDENTITY = new SaslIdentity(USER, USER);

    private static class TestSaslMechanism implements SaslMechanism {
        private final SaslExchange exchange;

        private TestSaslMechanism(SaslExchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public String name() {
            return BROKEN_MECHANISM;
        }

        @Override
        public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
            return exchange;
        }
    }

    private static class RecordingLineHandlerImapSession extends FakeImapSession {
        private ImapLineHandler lineHandler;
        private boolean throwOnPop;
        private int popCount;

        @Override
        public void pushLineHandler(ImapLineHandler lineHandler) {
            this.lineHandler = lineHandler;
        }

        @Override
        public void popLineHandler() {
            popCount++;
            if (throwOnPop) {
                throw new IllegalStateException("boom");
            }
            lineHandler = null;
        }
    }

    private static class ThrowingResponder implements ImapProcessor.Responder {
        @Override
        public void respond(ImapResponseMessage message) {
            throw new IllegalStateException("boom");
        }

        @Override
        public void flush() {
        }
    }

    private static class ThrowingFirstStepExchange implements SaslExchange {
        private boolean closed;

        @Override
        public SaslStep firstStep() {
            throw new IllegalArgumentException("boom");
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void abort() {
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static class ThrowingContinuationExchange implements SaslExchange {
        private boolean closed;

        @Override
        public SaslStep firstStep() {
            return new SaslStep.Challenge(Optional.of(bytes("challenge")));
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            throw new IllegalStateException("boom");
        }

        @Override
        public void abort() {
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static class SuccessDataExchange implements SaslExchange {
        private boolean closed;

        @Override
        public SaslStep firstStep() {
            return new SaslStep.Success(IDENTITY, Optional.of(bytes("server-data")));
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void abort() {
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private final AuthenticateProcessor testee = new AuthenticateProcessor(
        mock(MailboxManager.class),
        new UnpooledStatusResponseFactory(),
        new RecordingMetricFactory(),
        PathConverter.Factory.DEFAULT,
        new JamesSaslAuthenticator(mock(Authenticator.class), mock(Authorizator.class)));

    @Test
    void processRequestShouldCloseExchangeWhenFirstStepThrows() {
        // GIVEN a mechanism whose exchange fails while computing the first SASL step
        ThrowingFirstStepExchange exchange = new ThrowingFirstStepExchange();
        testee.configureSaslMechanisms(ImmutableList.of(new TestSaslMechanism(exchange)));

        // WHEN the processor handles the malformed exchange
        testee.processRequest(new AuthenticateRequest(BROKEN_MECHANISM, TAG), new FakeImapSession(), mock(ImapProcessor.Responder.class));

        // THEN it closes the exchange even though the terminal step handling was not reached
        assertThat(exchange.closed).isTrue();
    }

    @Test
    void processRequestShouldCloseExchangeWhenInitialChallengeWriteThrows() {
        // GIVEN a first challenge that fails while being written to the client
        ThrowingContinuationExchange exchange = new ThrowingContinuationExchange();
        testee.configureSaslMechanisms(ImmutableList.of(new TestSaslMechanism(exchange)));

        // WHEN the processor handles the initial challenge
        assertThatThrownBy(() -> testee.processRequest(new AuthenticateRequest(BROKEN_MECHANISM, TAG), new FakeImapSession(), new ThrowingResponder()))
            .isInstanceOf(IllegalStateException.class);

        // THEN it closes the exchange even though no continuation handler was installed
        assertThat(exchange.closed).isTrue();
    }

    @Test
    void processRequestShouldCloseExchangeWhenInitialSuccessDataWriteThrows() {
        // GIVEN a first successful SASL step with final server data that fails while being written to the client
        SuccessDataExchange exchange = new SuccessDataExchange();
        testee.configureSaslMechanisms(ImmutableList.of(new TestSaslMechanism(exchange)));

        // WHEN the processor handles the initial success data
        assertThatThrownBy(() -> testee.processRequest(new AuthenticateRequest(BROKEN_MECHANISM, TAG), new FakeImapSession(), new ThrowingResponder()))
            .isInstanceOf(IllegalStateException.class);

        // THEN it closes the exchange because terminal handling did not own it yet
        assertThat(exchange.closed).isTrue();
    }

    @Test
    void continuationShouldCloseExchangeWhenOnResponseThrows() {
        // GIVEN an active continuation whose mechanism fails unexpectedly while processing the client response
        ThrowingContinuationExchange exchange = new ThrowingContinuationExchange();
        RecordingLineHandlerImapSession session = new RecordingLineHandlerImapSession();
        testee.configureSaslMechanisms(ImmutableList.of(new TestSaslMechanism(exchange)));
        testee.processRequest(new AuthenticateRequest(BROKEN_MECHANISM, TAG), session, mock(ImapProcessor.Responder.class));
        ImapLineHandler lineHandler = session.lineHandler;
        assertThat(lineHandler).isNotNull();

        // WHEN the continuation line is processed
        assertThatThrownBy(() -> Mono.from(lineHandler.onLine(session, imapLine("response"))).block())
            .isInstanceOf(IllegalStateException.class);

        // THEN the active line handler is removed and the exchange is closed before rethrowing
        assertThat(session.popCount).isEqualTo(1);
        assertThat(exchange.closed).isTrue();
    }

    @Test
    void successDataAcknowledgementShouldCloseExchangeWhenPopLineHandlerThrows() {
        // GIVEN an active final server-data acknowledgement handler
        SuccessDataExchange exchange = new SuccessDataExchange();
        RecordingLineHandlerImapSession session = new RecordingLineHandlerImapSession();
        testee.configureSaslMechanisms(ImmutableList.of(new TestSaslMechanism(exchange)));
        testee.processRequest(new AuthenticateRequest(BROKEN_MECHANISM, TAG), session, mock(ImapProcessor.Responder.class));
        ImapLineHandler lineHandler = session.lineHandler;
        assertThat(lineHandler).isNotNull();
        session.throwOnPop = true;

        // WHEN the acknowledgement line reaches a failing line-handler cleanup
        assertThatThrownBy(() -> Mono.from(lineHandler.onLine(session, emptyLine())).block())
            .isInstanceOf(IllegalStateException.class);

        // THEN the exchange is still closed before the failure is rethrown
        assertThat(session.popCount).isEqualTo(1);
        assertThat(exchange.closed).isTrue();
    }

    private static byte[] imapLine(String value) {
        return (Base64.getEncoder().encodeToString(bytes(value)) + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] emptyLine() {
        return "\r\n".getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
