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

package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Attribute;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerTimeTest {
    private ServerTime testee;
    private FakeMailContext mailetContext;

    @BeforeEach
    void setUp() throws Exception {
        testee = new ServerTime();
        mailetContext = FakeMailContext.defaultContext();
        testee.init(FakeMailetConfig.builder().mailetContext(mailetContext).build());
    }

    private static FakeMail.Builder mail(String replyTo) throws Exception {
        return FakeMail.builder()
            .name("mail")
            .sender(new MailAddress("sender@other.org"))
            .recipient(new MailAddress("time@localhost"))
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addFrom("sender@other.org")
                .addHeader("Reply-To", replyTo)
                .setSubject("time?"));
    }

    @Test
    void serviceShouldNotAnswerRemoteAddressesWhenRelayIsNotAllowed() throws Exception {
        testee.service(mail("victim@remote.org").build());

        assertThat(mailetContext.getSentMails()).isEmpty();
    }

    @Test
    void serviceShouldAnswerLocalAddressesWhenRelayIsNotAllowed() throws Exception {
        testee.service(mail("user@localhost").build());

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    void serviceShouldAnswerRemoteAddressesWhenRelayIsAllowed() throws Exception {
        testee.service(mail("victim@remote.org")
            .attribute(Attribute.convertToAttribute("org.apache.james.SMTPIsAuthNetwork", true))
            .build());

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }

    @Test
    void serviceShouldAnswerRemoteAddressesWhenNotTrustingOnlyAuthNetwork() throws Exception {
        testee.init(FakeMailetConfig.builder()
            .mailetContext(mailetContext)
            .setProperty("trustOnlyAuthNetwork", "false")
            .build());

        testee.service(mail("victim@remote.org").build());

        assertThat(mailetContext.getSentMails()).hasSize(1);
    }
}
