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
package org.apache.james.smtpserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.james.smtpserver.fastfail.ValidRcptMX;
import org.junit.jupiter.api.Test;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

class ValidRcptMXTest {

    private static final String INVALID_HOST = "invalid.host.de";

    private SMTPSession setupMockedSMTPSession(MailAddress rcpt) {
        return new BaseFakeSMTPSession() {

            private final HashMap<AttachmentKey<?>, Object> sessionState = new HashMap<>();
            private final HashMap<AttachmentKey<?>, Object> connectionState = new HashMap<>();

            @Override
            public <T> Optional<T> setAttachment(AttachmentKey<T> key, T value, State state) {
                Preconditions.checkNotNull(key, "key cannot be null");
                Preconditions.checkNotNull(value, "value cannot be null");

                if (state == State.Connection) {
                    return key.convert(connectionState.put(key, value));
                } else {
                    return key.convert(sessionState.put(key, value));
                }
            }

            @Override
            public <T> Optional<T> removeAttachment(AttachmentKey<T> key, State state) {
                Preconditions.checkNotNull(key, "key cannot be null");

                if (state == State.Connection) {
                    return key.convert(connectionState.remove(key));
                } else {
                    return key.convert(sessionState.remove(key));
                }
            }

            @Override
            public <T> Optional<T> getAttachment(AttachmentKey<T> key, State state) {
                if (state == State.Connection) {
                    return key.convert(connectionState.get(key));
                } else {
                    return key.convert(sessionState.get(key));
                }
            }
        };
    }

    @Test
    void testRejectLoopbackMX() throws Exception {
        String bannedAddress = "172.53.64.2";

        DNSService dns = new InMemoryDNSService()
            .registerMxRecord(INVALID_HOST, bannedAddress)
            .registerMxRecord("255.255.255.255", "255.255.255.255")
            .registerMxRecord(bannedAddress, bannedAddress);
        MailAddress mailAddress = new MailAddress("test@" + INVALID_HOST);
        SMTPSession session = setupMockedSMTPSession(mailAddress);

        ValidRcptMX handler = new ValidRcptMX(dns);
        handler.setBannedNetworks(ImmutableList.of(bannedAddress), dns);
        HookReturnCode rCode = handler.doRcpt(session, MaybeSender.nullSender(), mailAddress).getResult();

        assertThat(HookReturnCode.deny()).describedAs("Reject").isEqualTo(rCode);
    }
}
