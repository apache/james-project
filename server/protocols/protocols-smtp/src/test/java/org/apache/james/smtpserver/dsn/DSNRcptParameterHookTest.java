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

package org.apache.james.smtpserver.dsn;

import static org.apache.james.protocols.api.ProtocolSession.State.Transaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

class DSNRcptParameterHookTest {

    private SMTPSession newSession() {
        return new BaseFakeSMTPSession() {
            private final Map<AttachmentKey<?>, Object> connectionState = new HashMap<>();
            private final Map<AttachmentKey<?>, Object> transactionState = new HashMap<>();

            private Map<AttachmentKey<?>, Object> stateFor(State state) {
                return state == State.Connection ? connectionState : transactionState;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<T> setAttachment(AttachmentKey<T> key, T value, State state) {
                return Optional.ofNullable((T) stateFor(state).put(key, value));
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<T> getAttachment(AttachmentKey<T> key, State state) {
                return Optional.ofNullable((T) stateFor(state).get(key));
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<T> removeAttachment(AttachmentKey<T> key, State state) {
                return Optional.ofNullable((T) stateFor(state).remove(key));
            }
        };
    }

    @Test
    void doRcptShouldAcceptValidNotifyAndOrcpt() throws Exception {
        DSNRcptParameterHook hook = new DSNRcptParameterHook();
        SMTPSession session = newSession();
        MailAddress rcpt = new MailAddress("rcpt@localhost");

        HookResult result = hook.doRcpt(session, MaybeSender.nullSender(), rcpt,
            ImmutableMap.of("NOTIFY", "SUCCESS,FAILURE", "ORCPT", "rfc822;orcpt@localhost"));

        assertThat(result.getResult()).isEqualTo(HookReturnCode.declined());
        assertThat(session.getAttachment(DSNRcptParameterHook.DSN_RCPT_PARAMETERS, Transaction)).isPresent();
    }

    @Test
    void doRcptShouldRejectUnknownNotifyValueWithSyntaxErrorInsteadOfThrowing() throws Exception {
        DSNRcptParameterHook hook = new DSNRcptParameterHook();
        SMTPSession session = newSession();
        MailAddress rcpt = new MailAddress("rcpt@localhost");

        HookResult result = hook.doRcpt(session, MaybeSender.nullSender(), rcpt,
            ImmutableMap.of("NOTIFY", "BOGUS"));

        assertThat(result.getResult().getAction()).isEqualTo(HookReturnCode.Action.DENY);
        assertThat(result.getSmtpRetCode()).isEqualTo("501");
    }

    @Test
    void doRcptShouldRejectNeverCombinedWithOtherValuesWithSyntaxErrorInsteadOfThrowing() throws Exception {
        DSNRcptParameterHook hook = new DSNRcptParameterHook();
        SMTPSession session = newSession();
        MailAddress rcpt = new MailAddress("rcpt@localhost");

        // RFC 3461 4.1: NEVER MUST NOT be combined with other notify keywords.
        HookResult result = hook.doRcpt(session, MaybeSender.nullSender(), rcpt,
            ImmutableMap.of("NOTIFY", "NEVER,SUCCESS"));

        assertThat(result.getResult().getAction()).isEqualTo(HookReturnCode.Action.DENY);
        assertThat(result.getSmtpRetCode()).isEqualTo("501");
    }

    @Test
    void doRcptShouldRejectOrcptWithoutRfc822PrefixWithSyntaxErrorInsteadOfThrowing() throws Exception {
        DSNRcptParameterHook hook = new DSNRcptParameterHook();
        SMTPSession session = newSession();
        MailAddress rcpt = new MailAddress("rcpt@localhost");

        HookResult result = hook.doRcpt(session, MaybeSender.nullSender(), rcpt,
            ImmutableMap.of("ORCPT", "bob@apache.org"));

        assertThat(result.getResult().getAction()).isEqualTo(HookReturnCode.Action.DENY);
        assertThat(result.getSmtpRetCode()).isEqualTo("501");
    }

    @Test
    void doRcptShouldRejectMalformedOrcptAddressWithSyntaxErrorInsteadOfThrowing() throws Exception {
        DSNRcptParameterHook hook = new DSNRcptParameterHook();
        SMTPSession session = newSession();
        MailAddress rcpt = new MailAddress("rcpt@localhost");

        HookResult result = hook.doRcpt(session, MaybeSender.nullSender(), rcpt,
            ImmutableMap.of("ORCPT", "rfc822;bob@apache@oups.org"));

        assertThat(result.getResult().getAction()).isEqualTo(HookReturnCode.Action.DENY);
        assertThat(result.getSmtpRetCode()).isEqualTo("501");
    }

    @Test
    void doRcptShouldNotPersistPartialStateWhenRejected() throws Exception {
        DSNRcptParameterHook hook = new DSNRcptParameterHook();
        SMTPSession session = newSession();
        MailAddress rcpt = new MailAddress("rcpt@localhost");

        hook.doRcpt(session, MaybeSender.nullSender(), rcpt, ImmutableMap.of("NOTIFY", "BOGUS"));

        assertThat(session.getAttachment(DSNRcptParameterHook.DSN_RCPT_PARAMETERS, Transaction)).isEmpty();
    }
}
