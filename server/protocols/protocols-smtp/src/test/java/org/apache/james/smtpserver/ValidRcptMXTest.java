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

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.james.smtpserver.fastfail.ValidRcptMX;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class ValidRcptMXTest {

    private static final String INVALID_HOST = "invalid.host.de";

    private SMTPSession setupMockedSMTPSession(MailAddress rcpt) {
        return new BaseFakeSMTPSession() {

            private final HashMap<String, Object> sstate = new HashMap<>();
            private final HashMap<String, Object> connectionState = new HashMap<>();

            @Override
            public Object setAttachment(String key, Object value, State state) {
                if (state == State.Connection) {
                    if (value == null) {
                        return connectionState.remove(key);
                    }
                    return connectionState.put(key, value);
                } else {
                    if (value == null) {
                        return sstate.remove(key);
                    }
                    return sstate.put(key, value);
                }
            }

            @Override
            public Object getAttachment(String key, State state) {
                if (state == State.Connection) {
                    return connectionState.get(key);
                } else {
                    return sstate.get(key);
                }
            }
        };
    }

    @Test
    public void testRejectLoopbackMX() throws Exception {
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
