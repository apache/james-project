/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.smtpserver.tls;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.jupiter.api.Test;

class SmtpRequireTlsParameterHookTest {
    private static SMTPSession session(boolean tlsStarted, boolean startTlsSupported) {
        return new BaseFakeSMTPSession() {
            private final Map<AttachmentKey<?>, Object> attachments = new HashMap<>();

            @Override
            public boolean isTLSStarted() {
                return tlsStarted;
            }

            @Override
            public boolean isStartTLSSupported() {
                return startTlsSupported;
            }

            @Override
            public Username getUsername() {
                return null;
            }

            @Override
            public String getSessionID() {
                return "session";
            }

            @Override
            public <T> Optional<T> setAttachment(AttachmentKey<T> key, T value, State state) {
                return key.convert(attachments.put(key, value));
            }

            @Override
            public <T> Optional<T> getAttachment(AttachmentKey<T> key, State state) {
                return key.convert(attachments.get(key));
            }
        };
    }

    @Test
    void requireTlsShouldBeRecordedOverImplicitTls() {
        SMTPSession session = session(true, false);

        assertThat(new SmtpRequireTlsParameterHook().doMailParameter(session, "REQUIRETLS", null)).isEqualTo(HookResult.DECLINED);
        assertThat(session.getAttachment(SmtpRequireTlsParameterHook.REQUIRETLS_KEY, SMTPSession.State.Transaction)).contains(true);
    }

    @Test
    void requireTlsShouldNotBeRecordedWithoutTls() {
        SMTPSession session = session(false, true);

        assertThat(new SmtpRequireTlsParameterHook().doMailParameter(session, "REQUIRETLS", null)).isEqualTo(HookResult.DECLINED);
        assertThat(session.getAttachment(SmtpRequireTlsParameterHook.REQUIRETLS_KEY, SMTPSession.State.Transaction)).isEmpty();
    }
}
