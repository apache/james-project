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
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.StartTLSRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StartTLSProcessorTest {
    private static class StartTlsCapableSession extends FakeImapSession {
        private final AtomicBoolean tlsStarted = new AtomicBoolean(false);

        @Override
        public boolean supportStartTLS() {
            return true;
        }

        @Override
        public boolean startTLS(Runnable runnable) {
            tlsStarted.set(true);
            runnable.run();
            return true;
        }
    }

    @Test
    void startTlsShouldBeRefusedOnceAuthenticated() {
        StartTlsCapableSession session = new StartTlsCapableSession();
        session.authenticated();
        ImapProcessor.Responder responder = mock(ImapProcessor.Responder.class);

        new StartTLSProcessor(new UnpooledStatusResponseFactory())
            .processReactive(new StartTLSRequest(TAG), responder, session)
            .block();

        ArgumentCaptor<ImapResponseMessage> response = ArgumentCaptor.forClass(ImapResponseMessage.class);
        verify(responder).respond(response.capture());
        assertThat(((StatusResponse) response.getValue()).getServerResponseType()).isEqualTo(StatusResponse.Type.NO);
        assertThat(session.tlsStarted.get()).isFalse();
    }

    @Test
    void startTlsShouldBeAcceptedBeforeAuthentication() {
        StartTlsCapableSession session = new StartTlsCapableSession();
        ImapProcessor.Responder responder = mock(ImapProcessor.Responder.class);

        new StartTLSProcessor(new UnpooledStatusResponseFactory())
            .processReactive(new StartTLSRequest(TAG), responder, session)
            .block();

        assertThat(session.tlsStarted.get()).isTrue();
    }
}
