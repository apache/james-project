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

import static org.junit.Assert.assertEquals;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import javax.mail.internet.ParseException;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.MailAddressException;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.james.smtpserver.fastfail.ValidRcptMX;
import org.junit.Test;

public class ValidRcptMXTest {

    private final static String INVALID_HOST = "invalid.host.de";
    private final static String INVALID_MX = "mx." + INVALID_HOST;
    private final static String LOOPBACK = "127.0.0.1";

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

    private DNSService setupMockedDNSServer() {

        return new MockDNSService() {

            @Override
            public Collection<String> findMXRecords(String hostname) {
                Collection<String> mx = new ArrayList<>();

                if (hostname.equals(INVALID_HOST)) {
                    mx.add(INVALID_MX);
                }
                return mx;
            }

            @Override
            public InetAddress getByName(String host) throws UnknownHostException {
                if (host.equals(INVALID_MX) || host.equals(LOOPBACK)) {
                    return InetAddress.getByName(LOOPBACK);
                } else if (host.equals("255.255.255.255")) {
                    return InetAddress.getByName("255.255.255.255");
                }
                throw new UnknownHostException("Unknown host");
            }
        };
    }

    @Test
    public void testRejectLoopbackMX() throws ParseException, MailAddressException {
        Collection<String> bNetworks = new ArrayList<>();
        bNetworks.add("127.0.0.1");

        DNSService dns = setupMockedDNSServer();
        MailAddress mailAddress = new MailAddress("test@" + INVALID_HOST);
        SMTPSession session = setupMockedSMTPSession(mailAddress);
        ValidRcptMX handler = new ValidRcptMX();

        handler.setDNSService(dns);
        handler.setBannedNetworks(bNetworks, dns);
        int rCode = handler.doRcpt(session, null, mailAddress).getResult();

        assertEquals("Reject", rCode, HookReturnCode.DENY);
    }
}
