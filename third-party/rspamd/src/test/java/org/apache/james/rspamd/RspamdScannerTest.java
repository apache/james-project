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

package org.apache.james.rspamd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Optional;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.rspamd.client.RspamdClientConfiguration;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.apache.james.rspamd.model.AnalysisResult;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

@Tag(Unstable.TAG)
class RspamdScannerTest {
    @RegisterExtension
    static DockerRspamdExtension rspamdExtension = new DockerRspamdExtension();
    static final String rspamdPassword = "admin";
    static final String INIT_SUBJECT = "initial subject";
    static final String REWRITE_SUBJECT = "rewrite subject";

    private RspamdScanner mailet;

    @BeforeEach
    void setup() {
        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), rspamdPassword, Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);
        mailet = new RspamdScanner(client);
    }

    @Test
    void serviceShouldWriteSpamAttributeOnMail() throws Exception {
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
            .contains(RspamdScanner.FLAG_MAIL.asString(), RspamdScanner.STATUS_MAIL.asString());
    }

    @Test
    void serviceShouldWriteMessageAsNotSpamWhenNotSpam() throws Exception {
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

        Collection<PerRecipientHeaders.Header> headersForRecipient = mail.getPerRecipientSpecificHeaders()
            .getHeadersForRecipient(new MailAddress("user1@exemple.com"));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(headersForRecipient.stream()
                    .filter(header -> header.getName().equals(RspamdScanner.FLAG_MAIL.asString()))
                    .filter(header -> header.getValue().startsWith("NO"))
                    .findAny())
                .isPresent();

            softly.assertThat(headersForRecipient.stream()
                    .filter(header -> header.getName().equals(RspamdScanner.STATUS_MAIL.asString()))
                    .filter(header -> header.getValue().startsWith("No, actions=no action"))
                    .findAny())
                .isPresent();

        });
    }

    @Test
    void serviceShouldWriteMessageAsSpamWhenSpam() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("mail/spam/spam8.eml"));

        Mail mail = FakeMail.builder()
            .name("name")
            .remoteAddr("1.55.251.37")
            .recipient("user1@exemple.com")
            .mimeMessage(mimeMessage)
            .build();

        mailet.service(mail);


        Collection<PerRecipientHeaders.Header> headersForRecipient = mail.getPerRecipientSpecificHeaders()
            .getHeadersForRecipient(new MailAddress("user1@exemple.com"));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(headersForRecipient.stream()
                    .filter(header -> header.getName().equals(RspamdScanner.FLAG_MAIL.asString()))
                    .filter(header -> header.getValue().startsWith("YES"))
                    .findAny())
                .isPresent();

            softly.assertThat(headersForRecipient.stream()
                    .filter(header -> header.getName().equals(RspamdScanner.STATUS_MAIL.asString()))
                    .filter(header -> header.getValue().startsWith("Yes, actions=reject"))
                    .findAny())
                .isPresent();

        });
    }

    @Test
    void serviceShouldNotFailWhenAllInfos() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("mail/ham/ham1.eml"));

        Mail mail = FakeMail.builder()
            .name("name")
            .remoteAddr("127.0.0.1")
            .sender("user1@exemple.com")
            .recipient("user1@exemple.com")
            .attribute(new Attribute(Mail.SMTP_HELO, AttributeValue.of("exemple.com")))
            .attribute(new Attribute(Mail.SMTP_AUTH_USER, AttributeValue.of("user1@exemple.com")))
            .mimeMessage(mimeMessage)
            .build();

        mailet.service(mail);

        Collection<PerRecipientHeaders.Header> headersForRecipient = mail.getPerRecipientSpecificHeaders()
            .getHeadersForRecipient(new MailAddress("user1@exemple.com"));

        assertThat(headersForRecipient.stream()
                    .filter(header -> header.getName().equals(RspamdScanner.FLAG_MAIL.asString()))
                    .filter(header -> header.getValue().startsWith("NO"))
                    .findAny())
                .isPresent();
    }

    @Test
    void shouldRewriteSubjectWhenRewriteSubjectIsTrueAndAnalysisResultHasDesiredRewriteSubject() throws Exception {
        RspamdHttpClient rspamdHttpClient = mock(RspamdHttpClient.class);
        when(rspamdHttpClient.checkV2(any(Mail.class))).thenReturn(Mono.just(AnalysisResult.builder()
                .action(AnalysisResult.Action.REWRITE_SUBJECT)
                .score(12.1F)
                .requiredScore(14F)
                .desiredRewriteSubject(REWRITE_SUBJECT)
            .build()));

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty("rewriteSubject", "true")
            .build();

        mailet = new RspamdScanner(rspamdHttpClient);

        Mail mail = FakeMail.builder()
            .name("name")
            .recipient("user1@exemple.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient("user1@exemple.com")
                .addFrom("sender@exemple.com")
                .setSubject(INIT_SUBJECT)
                .setText("Please!")
                .build())
            .build();

        mailet.init(mailetConfig);
        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo(REWRITE_SUBJECT);
    }

    @Test
    void shouldNotRewriteSubjectWhenRewriteSubjectIsFalseByDefaultAndAnalysisResultHasDesiredRewriteSubject() throws Exception {
        RspamdHttpClient rspamdHttpClient = mock(RspamdHttpClient.class);
        when(rspamdHttpClient.checkV2(any(Mail.class))).thenReturn(Mono.just(AnalysisResult.builder()
            .action(AnalysisResult.Action.REWRITE_SUBJECT)
            .score(12.1F)
            .requiredScore(14F)
            .desiredRewriteSubject(REWRITE_SUBJECT)
            .build()));

        mailet = new RspamdScanner(rspamdHttpClient);

        Mail mail = FakeMail.builder()
            .name("name")
            .recipient("user1@exemple.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient("user1@exemple.com")
                .addFrom("sender@exemple.com")
                .setSubject(INIT_SUBJECT)
                .setText("Please!")
                .build())
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo(INIT_SUBJECT);
    }

    @Test
    void shouldNotRewriteSubjectWhenRewriteSubjectIsTrueAndAnalysisResultDoesNotHaveDesiredRewriteSubject() throws Exception {
        RspamdHttpClient rspamdHttpClient = mock(RspamdHttpClient.class);
        when(rspamdHttpClient.checkV2(any(Mail.class))).thenReturn(Mono.just(AnalysisResult.builder()
            .action(AnalysisResult.Action.NO_ACTION)
            .score(0.99F)
            .requiredScore(14F)
            .build()));

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty("rewriteSubject", "true")
            .build();

        mailet = new RspamdScanner(rspamdHttpClient);

        Mail mail = FakeMail.builder()
            .name("name")
            .recipient("user1@exemple.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addToRecipient("user1@exemple.com")
                .addFrom("sender@exemple.com")
                .setSubject(INIT_SUBJECT)
                .setText("Please!")
                .build())
            .build();

        mailet.init(mailetConfig);
        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo(INIT_SUBJECT);
    }

    @Test
    void shouldSendMailToSpamProcessorWhenMailHasAVirusAndConfigVirusProcessor() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty("virusProcessor", "virus")
            .build();

        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("mail/attachment/inlineVirusTextAttachment.eml"));

        Mail mail = FakeMail.builder()
            .name("name")
            .recipient("user1@exemple.com")
            .mimeMessage(mimeMessage)
            .build();

        mailet.init(mailetConfig);
        mailet.service(mail);

        assertThat(mail.getState()).isEqualTo("virus");
    }

    @Test
    void shouldNotSendMailToVirusProcessorWhenMailHasNoVirus() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty("virusProcessor", "virus")
            .build();

        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("mail/attachment/inlineNonVirusTextAttachment.eml"));

        Mail mail = FakeMail.builder()
            .name("name")
            .recipient("user1@exemple.com")
            .mimeMessage(mimeMessage)
            .build();

        mailet.init(mailetConfig);
        mailet.service(mail);

        assertThat(mail.getState()).isNull();
    }

    @Test
    void shouldNotSendMailToVirusProcessorWhenMailHasAVirusByDefault() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder().build();
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("mail/attachment/inlineVirusTextAttachment.eml"));

        Mail mail = FakeMail.builder()
            .name("name")
            .recipient("user1@exemple.com")
            .mimeMessage(mimeMessage)
            .build();

        mailet.init(mailetConfig);
        mailet.service(mail);

        assertThat(mail.getState()).isNull();
    }
}