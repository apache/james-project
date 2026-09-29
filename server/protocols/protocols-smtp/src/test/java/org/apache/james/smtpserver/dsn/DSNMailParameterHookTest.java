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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.jupiter.api.Test;

class DSNMailParameterHookTest {

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
    void doMailParameterShouldAcceptValidEnvId() {
        DSNMailParameterHook hook = new DSNMailParameterHook();
        SMTPSession session = newSession();

        HookResult result = hook.doMailParameter(session, "ENVID", "QQ314159");

        assertThat(result.getResult()).isEqualTo(HookReturnCode.declined());
        assertThat(session.getAttachment(DSNMailParameterHook.DSN_ENVID, SMTPSession.State.Transaction)).isPresent();
    }

    @Test
    void doMailParameterShouldRejectMalformedEnvIdWithSyntaxErrorInsteadOfThrowing() {
        DSNMailParameterHook hook = new DSNMailParameterHook();
        SMTPSession session = newSession();

        // '=' is not a valid xtext character outside of a hex escape (RFC 3461 section 4).
        HookResult result = hook.doMailParameter(session, "ENVID", "invalid=value");

        assertThat(result.getResult().getAction()).isEqualTo(HookReturnCode.Action.DENY);
        assertThat(result.getSmtpRetCode()).isEqualTo("501");
        assertThat(session.getAttachment(DSNMailParameterHook.DSN_ENVID, SMTPSession.State.Transaction)).isEmpty();
    }

    @Test
    void doMailParameterShouldIgnoreInvalidRetInsteadOfThrowing() {
        DSNMailParameterHook hook = new DSNMailParameterHook();
        SMTPSession session = newSession();

        HookResult result = hook.doMailParameter(session, "RET", "BOGUS");

        assertThat(result.getResult()).isEqualTo(HookReturnCode.declined());
        assertThat(session.getAttachment(DSNMailParameterHook.DSN_RET, SMTPSession.State.Transaction)).isEmpty();
    }
}
