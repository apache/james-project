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

import java.net.InetSocketAddress;
import java.time.Duration;

import org.apache.james.protocols.lib.POP3BeforeSMTPHelper;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.jupiter.api.Test;

class POP3BeforeSMTPHandlerTest {

    private SMTPSession mockedSession;

    private void setupMockedSMTPSession() {
        mockedSession = new BaseFakeSMTPSession() {

            private boolean relayingAllowed = false;

            @Override
            public InetSocketAddress getRemoteAddress() {
                return new InetSocketAddress(getRemoteIPAddress(), 0);
            }

            public String getRemoteIPAddress() {
                return "192.168.200.1";
            }

            @Override
            public boolean isRelayingAllowed() {
                return relayingAllowed;
            }

            @Override
            public void setRelayingAllowed(boolean relayingAllowed) {
                this.relayingAllowed = relayingAllowed;
            }
        };
    }

    @Test
    void testAuthWorks() {

        POP3BeforeSMTPHandler handler = new POP3BeforeSMTPHandler();

        setupMockedSMTPSession();
        POP3BeforeSMTPHelper.addIPAddress("192.168.200.1");

        assertThat(mockedSession.isRelayingAllowed()).isFalse();
        handler.onConnect(mockedSession);
        assertThat(mockedSession.isRelayingAllowed()).isTrue();
    }

    @Test
    void testIPGetRemoved() {
        long sleepTime = 100;
        POP3BeforeSMTPHandler handler = new POP3BeforeSMTPHandler();

        setupMockedSMTPSession();
        POP3BeforeSMTPHelper.addIPAddress("192.168.200.1");
        assertThat(mockedSession.isRelayingAllowed()).isFalse();

        try {
            Thread.sleep(sleepTime);
            POP3BeforeSMTPHelper.removeExpiredIP(Duration.ofMillis(10));
            handler.onConnect(mockedSession);
            assertThat(mockedSession.isRelayingAllowed()).isFalse();

        } catch (InterruptedException e) {
            // ignore
        }
    }

    @Test
    void testThrowExceptionOnIllegalExpireTime() {
        boolean exception = false;
        POP3BeforeSMTPHandler handler = new POP3BeforeSMTPHandler();

        setupMockedSMTPSession();

        try {
            handler.setExpireTime("1 unit");
        } catch (NumberFormatException e) {
            exception = true;
        }
        assertThat(exception).isTrue();
    }

    @Test
    void testValidExpireTime() {
        boolean exception = false;
        POP3BeforeSMTPHandler handler = new POP3BeforeSMTPHandler();

        setupMockedSMTPSession();

        try {
            handler.setExpireTime("1 hour");
        } catch (NumberFormatException e) {
            exception = true;
        }
        assertThat(exception).isFalse();
    }
}
