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

package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.spamassassin.SpamAssassinResult;
import org.apache.james.spamassassin.mock.MockSpamd;
import org.apache.james.spamassassin.mock.MockSpamdTestRule;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.util.Port;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Rule;
import org.junit.Test;

import com.github.steveash.guavate.Guavate;

public class SpamAssassinTest {

    public static final DomainList NO_DOMAIN_LIST = null;
    @Rule
    public MockSpamdTestRule spamd = new MockSpamdTestRule();

    private SpamAssassin mailet = new SpamAssassin(new RecordingMetricFactory(), MemoryUsersRepository.withVirtualHosting(NO_DOMAIN_LIST));

    @Test
    public void initShouldSetDefaultSpamdHostWhenNone() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .build());

        assertThat(mailet.getSpamdHost()).isEqualTo(SpamAssassin.DEFAULT_HOST);
    }

    @Test
    public void initShouldSetDefaultSpamdPortWhenNone() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .build());

        assertThat(mailet.getSpamdPort()).isEqualTo(SpamAssassin.DEFAULT_PORT);
    }

    @Test
    public void initShouldSetSpamdHostWhenPresent() throws Exception {
        String spamdHost = "any.host";
        mailet.init(FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .setProperty(SpamAssassin.SPAMD_HOST, spamdHost)
            .build());

        assertThat(mailet.getSpamdHost()).isEqualTo(spamdHost);
    }

    @Test
    public void getSpamHostShouldReturnDefaultValueWhenEmpty() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .setProperty(SpamAssassin.SPAMD_HOST, "")
            .build());

        assertThat(mailet.getSpamdHost()).isEqualTo(SpamAssassin.DEFAULT_HOST);
    }

    @Test
    public void initShouldSetDefaultSpamdPortWhenDefault() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .build());

        assertThat(mailet.getSpamdPort()).isEqualTo(SpamAssassin.DEFAULT_PORT);
    }

    @Test
    public void initShouldThrowWhenSpamdPortIsNotNumber() {
        assertThatThrownBy(() -> mailet.init(FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .setProperty(SpamAssassin.SPAMD_PORT, "noNumber")
            .build())).isInstanceOf(MessagingException.class);
    }

    @Test
    public void initShouldThrowWhenSpamdPortIsNegative() {
        assertThatThrownBy(() -> mailet.init(FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .setProperty(SpamAssassin.SPAMD_PORT, "-1")
            .build())).isInstanceOf(MessagingException.class);
    }

    @Test
    public void initShouldThrowWhenSpamdPortIsZero() {
        assertThatThrownBy(() -> mailet.init(FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .setProperty(SpamAssassin.SPAMD_PORT, "0")
            .build())).isInstanceOf(MessagingException.class);
    }

    @Test
    public void initShouldThrowWhenSpamdPortTooBig() {
        assertThatThrownBy(() -> mailet.init(FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .setProperty(SpamAssassin.SPAMD_PORT,
                String.valueOf(Port.MAX_PORT_VALUE + 1))
            .build())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void initShouldSetSpamPortWhenPresent() throws Exception {
        int spamPort = 1000;
        mailet.init(FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .setProperty(SpamAssassin.SPAMD_PORT, String.valueOf(spamPort))
            .build());

        assertThat(mailet.getSpamdPort()).isEqualTo(spamPort);
    }

    @Test
    public void serviceShouldWriteSpamAttributeOnMail() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .setProperty(SpamAssassin.SPAMD_HOST, "localhost")
            .setProperty(SpamAssassin.SPAMD_PORT, String.valueOf(spamd.getPort()))
            .build();
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
                .collect(Guavate.toImmutableList()))
            .contains(SpamAssassinResult.FLAG_MAIL.asString(), SpamAssassinResult.STATUS_MAIL.asString());
    }

    @Test
    public void serviceShouldWriteMessageAsNotSpamWhenNotSpam() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .setProperty(SpamAssassin.SPAMD_HOST, "localhost")
            .setProperty(SpamAssassin.SPAMD_PORT, String.valueOf(spamd.getPort()))
            .build();
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
    public void serviceShouldWriteMessageAsSpamWhenSpam() throws Exception {
        FakeMailetConfig mailetConfiguration = FakeMailetConfig.builder()
            .mailetName("SpamAssassin")
            .setProperty(SpamAssassin.SPAMD_HOST, "localhost")
            .setProperty(SpamAssassin.SPAMD_PORT, String.valueOf(spamd.getPort()))
            .build();
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
    public void getMailetInfoShouldReturnSpamAssasinMailetInformation() {
        assertThat(mailet.getMailetInfo()).isEqualTo("Checks message against SpamAssassin");
    }

}