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

package org.apache.james.smtpserver.fastfail;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.jupiter.api.Test;

import com.google.common.net.InetAddresses;

class ReverseEqualsEhloHeloHandlerTest {
    private static final String CLIENT_IP = "192.0.2.7";

    // Behaves like DNSJavaService: an IP literal resolves to an address named after the literal itself,
    // only getHostName performs the PTR lookup, whose answer is an absolute name.
    private static final MockDNSService DNS = new MockDNSService() {
        @Override
        public InetAddress getByName(String host) throws UnknownHostException {
            return InetAddress.getByAddress(host, InetAddresses.forString(host).getAddress());
        }

        @Override
        public String getHostName(InetAddress addr) {
            if (addr.getHostAddress().equals(CLIENT_IP)) {
                return "Client.Example.";
            }
            return addr.getHostAddress();
        }
    };

    private static SMTPSession session() {
        return new BaseFakeSMTPSession() {
            private final Map<AttachmentKey<?>, Object> connectionMap = new HashMap<>();

            @Override
            public Username getUsername() {
                return null;
            }

            @Override
            public boolean isRelayingAllowed() {
                return false;
            }

            @Override
            public InetSocketAddress getRemoteAddress() {
                return new InetSocketAddress(CLIENT_IP, 25);
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

    @Test
    void heloMatchingThePtrRecordShouldBeAccepted() {
        SMTPSession session = session();
        ReverseEqualsEhloHeloHandler handler = new ReverseEqualsEhloHeloHandler(DNS);

        handler.doHelo(session, "client.example");

        assertThat(handler.doMail(session, MaybeSender.nullSender())).isEqualTo(HookResult.DECLINED);
    }

    @Test
    void heloNotMatchingThePtrRecordShouldBeRefused() {
        SMTPSession session = session();
        ReverseEqualsEhloHeloHandler handler = new ReverseEqualsEhloHeloHandler(DNS);

        handler.doHelo(session, "other.example");

        assertThat(handler.doMail(session, MaybeSender.nullSender()).getResult())
            .isEqualTo(HookResult.DENY.getResult());
    }
}
