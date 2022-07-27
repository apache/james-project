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

import java.util.Collection;
import java.util.Optional;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.rspamd.client.RSpamDClientConfiguration;
import org.apache.james.rspamd.client.RSpamDHttpClient;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.test.FakeMail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

class RSpamDScannerTest {

    @RegisterExtension
    static DockerRSpamDExtension rSpamDExtension = new DockerRSpamDExtension();
    static final String rSpamDPassword = "admin";

    private RSpamDScanner mailet;

    @BeforeEach
    void setup() {
        RSpamDClientConfiguration configuration = new RSpamDClientConfiguration(rSpamDExtension.getBaseUrl(), rSpamDPassword, Optional.empty());
        RSpamDHttpClient client = new RSpamDHttpClient(configuration);
        mailet = new RSpamDScanner(client);
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
            .contains(RSpamDScanner.FLAG_MAIL.asString(), RSpamDScanner.STATUS_MAIL.asString());
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
                    .filter(header -> header.getName().equals(RSpamDScanner.FLAG_MAIL.asString()))
                    .filter(header -> header.getValue().startsWith("NO"))
                    .findAny())
                .isPresent();

            softly.assertThat(headersForRecipient.stream()
                    .filter(header -> header.getName().equals(RSpamDScanner.STATUS_MAIL.asString()))
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
            .recipient("user1@exemple.com")
            .mimeMessage(mimeMessage)
            .build();

        mailet.service(mail);


        Collection<PerRecipientHeaders.Header> headersForRecipient = mail.getPerRecipientSpecificHeaders()
            .getHeadersForRecipient(new MailAddress("user1@exemple.com"));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(headersForRecipient.stream()
                    .filter(header -> header.getName().equals(RSpamDScanner.FLAG_MAIL.asString()))
                    .filter(header -> header.getValue().startsWith("YES"))
                    .findAny())
                .isPresent();

            softly.assertThat(headersForRecipient.stream()
                    .filter(header -> header.getName().equals(RSpamDScanner.STATUS_MAIL.asString()))
                    .filter(header -> header.getValue().startsWith("Yes, actions=reject"))
                    .findAny())
                .isPresent();

        });
    }
}