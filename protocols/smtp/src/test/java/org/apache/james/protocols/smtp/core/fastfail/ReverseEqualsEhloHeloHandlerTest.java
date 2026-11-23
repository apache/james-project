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

package org.apache.james.protocols.smtp.core.fastfail;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.jupiter.api.Test;

class ReverseEqualsEhloHeloHandlerTest {
    private static final String CLIENT_HOST = "client.example";

    private static SMTPSession session() {
        return session(null, false);
    }

    private static SMTPSession session(Username username, boolean relayingAllowed) {
        return new BaseFakeSMTPSession() {
            private final Map<AttachmentKey<?>, Object> connectionMap = new HashMap<>();

            @Override
            public Username getUsername() {
                return username;
            }

            @Override
            public boolean isRelayingAllowed() {
                return relayingAllowed;
            }

            @Override
            public InetSocketAddress getRemoteAddress() {
                return new InetSocketAddress("127.0.0.1", 25);
            }

            @Override
            public <T> Optional<T> setAttachment(AttachmentKey<T> key, T value, State state) {
                return key.convert(connectionMap.put(key, value));
            }

            @Override
            public <T> Optional<T> removeAttachment(AttachmentKey<T> key, State state) {
                return key.convert(connectionMap.remove(key));
            }

            @Override
            public <T> Optional<T> getAttachment(AttachmentKey<T> key, State state) {
                return key.convert(connectionMap.get(key));
            }
        };
    }

    private static ReverseEqualsEhloHeloHandler handler() {
        return new ReverseEqualsEhloHeloHandler() {
            @Override
            protected String resolve(String host) {
                return CLIENT_HOST;
            }
        };
    }

    @Test
    void heloNotMatchingTheReverseDnsShouldBeRefused() {
        SMTPSession session = session();
        ReverseEqualsEhloHeloHandler handler = handler();

        handler.doHelo(session, "other.example");

        assertThat(handler.doMail(session, MaybeSender.nullSender()).getResult())
            .isEqualTo(HookResult.DENY.getResult());
    }

    @Test
    void heloMatchingTheReverseDnsShouldBeAccepted() {
        SMTPSession session = session();
        ReverseEqualsEhloHeloHandler handler = handler();

        handler.doHelo(session, CLIENT_HOST);

        assertThat(handler.doMail(session, MaybeSender.nullSender())).isEqualTo(HookResult.DECLINED);
    }

    @Test
    void heloShouldBeComparedToTheReverseDnsIgnoringCaseAndTheTrailingDot() {
        SMTPSession session = session();
        ReverseEqualsEhloHeloHandler handler = new ReverseEqualsEhloHeloHandler() {
            @Override
            protected String resolve(String host) {
                return "Client.Example.";
            }
        };

        handler.doHelo(session, CLIENT_HOST);

        assertThat(handler.doMail(session, MaybeSender.nullSender())).isEqualTo(HookResult.DECLINED);
    }

    @Test
    void authenticatedSessionsShouldNotBeChecked() {
        SMTPSession session = session(Username.of("bob@example.com"), false);
        ReverseEqualsEhloHeloHandler handler = handler();

        handler.doHelo(session, "other.example");

        assertThat(handler.doMail(session, MaybeSender.nullSender())).isEqualTo(HookResult.DECLINED);
    }

    @Test
    void sessionsAllowedToRelayShouldNotBeChecked() {
        SMTPSession session = session(null, true);
        ReverseEqualsEhloHeloHandler handler = handler();

        handler.doHelo(session, "other.example");

        assertThat(handler.doMail(session, MaybeSender.nullSender())).isEqualTo(HookResult.DECLINED);
    }
}
