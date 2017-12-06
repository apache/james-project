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

package org.apache.james.transport.matchers;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;

public class SMTPIsAuthNetworkTest {

    private SMTPIsAuthNetwork testee;
    private MailAddress recipient;

    @Before
    public void setUp() throws Exception {
        testee = new SMTPIsAuthNetwork();
        recipient = new MailAddress("recipient@domain.com");
    }

    @Test
    public void matchShouldReturnEmptyWhenNoSmtpInformation() throws Exception {
        Mail mail = FakeMail.builder()
            .recipient(recipient)
            .build();

        assertThat(testee.match(mail))
            .isEmpty();
    }

    @Test
    public void matchShouldReturnAddressesWhenAuthorizedNetwork() throws Exception {
        Mail mail = FakeMail.builder()
            .recipient(recipient)
            .attribute(SMTPIsAuthNetwork.SMTP_AUTH_NETWORK_NAME, "true")
            .build();

        assertThat(testee.match(mail))
            .containsOnly(recipient);
    }

    @Test
    public void matchShouldReturnEmptyWhenNonAuthorizedNetwork() throws Exception {
        Mail mail = FakeMail.builder()
            .recipient(recipient)
            .attribute(SMTPIsAuthNetwork.SMTP_AUTH_NETWORK_NAME, "false")
            .build();

        assertThat(testee.match(mail))
            .isEmpty();
    }
}
