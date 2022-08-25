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

package org.apache.james.spamassassin;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.spamassassin.mock.MockSpamd;
import org.apache.james.spamassassin.mock.MockSpamdExtension;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.util.Host;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

class SpamAssassinTest {

    private static final DomainList NO_DOMAIN_LIST = null;

    @RegisterExtension
    MockSpamdExtension spamd = new MockSpamdExtension();

    private SpamAssassin mailet() {
        return new SpamAssassin(new RecordingMetricFactory(), MemoryUsersRepository.withVirtualHosting(NO_DOMAIN_LIST), new SpamAssassinConfiguration(Host.from("localhost", spamd.getPort())));
    }

    @Test
    void serviceShouldWriteSpamAttributeOnMail() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .build();
        SpamAssassin mailet = mailet();
        mailet.init(mailetConfiguration);

        Mail mail = FakeMail.builder()
            .name("name")
            .recipient("user1@exemple.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient("user1@exemple.com")
                .addFrom("sender@exemple.com")
                .setSubject("testing")
                .setText("Please!")
                .build())
            .build();

        mailet.service(mail);


        assertThat(
            mail.getPerRecipientSpecificHeaders()
                .getHeadersByRecipient()
                .get(new MailAddress("user1@exemple.com"))
                .stream()
                .map(PerRecipientHeaders.Header::getName)
                .collect(ImmutableList.toImmutableList()))
            .contains(SpamAssassinResult.FLAG_MAIL.asString(), SpamAssassinResult.STATUS_MAIL.asString());
    }

    @Test
    void serviceShouldWriteSpamAttributeOnMailWhenGlobalScan() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .setProperty("perUserScans", "false")
            .build();
        SpamAssassin mailet = mailet();
        mailet.init(mailetConfiguration);

        Mail mail = FakeMail.builder()
            .name("name")
            .recipient("user1@exemple.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient("user1@exemple.com")
                .addFrom("sender@exemple.com")
                .setSubject("testing")
                .setText("Please!")
                .build())
            .build();

        mailet.service(mail);


        assertThat(mail.getMessage().getHeader(SpamAssassinResult.FLAG_MAIL.asString())).isNotNull();
        assertThat(mail.getMessage().getHeader(SpamAssassinResult.STATUS_MAIL.asString())).isNotNull();
    }

    @Test
    void serviceShouldWriteMessageAsNotSpamWhenNotSpam() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .build();
        SpamAssassin mailet = mailet();
        mailet.init(mailetConfiguration);

        Mail mail = FakeMail.builder()
            .name("name")
            .recipient("user1@exemple.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient("user1@exemple.com")
                .addFrom("sender@exemple.com")
                .setSubject("testing")
                .setText("Please!")
                .build())
            .build();

        mailet.service(mail);

        assertThat(mail.getPerRecipientSpecificHeaders())
            .isEqualTo(new PerRecipientHeaders()
                .addHeaderForRecipient(
                    PerRecipientHeaders.Header.builder()
                        .name(SpamAssassinResult.FLAG_MAIL.asString())
                        .value("NO"),
                    new MailAddress("user1@exemple.com"))
                .addHeaderForRecipient(
                    PerRecipientHeaders.Header.builder()
                        .name(SpamAssassinResult.STATUS_MAIL.asString())
                        .value("No, hits=3 required=5"),
                    new MailAddress("user1@exemple.com")));
    }

    @Test
    void serviceShouldWriteMessageAsSpamWhenSpam() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .build();
        SpamAssassin mailet = mailet();
        mailet.init(mailetConfiguration);

        Mail mail = FakeMail.builder()
            .name("name")
            .recipient("user1@exemple.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient("user1@exemple.com")
                .addFrom("sender@exemple.com")
                .setSubject(MockSpamd.GTUBE + " testing")
                .setText("Please!")
                .build())
            .build();

        mailet.service(mail);

        assertThat(mail.getPerRecipientSpecificHeaders())
            .isEqualTo(new PerRecipientHeaders()
                .addHeaderForRecipient(
                    PerRecipientHeaders.Header.builder()
                        .name(SpamAssassinResult.FLAG_MAIL.asString())
                        .value("YES"),
                    new MailAddress("user1@exemple.com"))
                .addHeaderForRecipient(
                    PerRecipientHeaders.Header.builder()
                        .name(SpamAssassinResult.STATUS_MAIL.asString())
                        .value("Yes, hits=1000 required=5"),
                    new MailAddress("user1@exemple.com")));
    }

    @Test
    void getMailetInfoShouldReturnSpamAssasinMailetInformation() {
        assertThat(mailet().getMailetInfo()).isEqualTo("Checks message against SpamAssassin");
    }
}
