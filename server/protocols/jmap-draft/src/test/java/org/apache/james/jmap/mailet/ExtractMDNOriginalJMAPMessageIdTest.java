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

package org.apache.james.jmap.mailet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.message.SingleBodyBuilder;
import org.apache.james.user.api.UsersRepository;
import org.junit.Test;

public class ExtractMDNOriginalJMAPMessageIdTest {

    @Test
    public void extractReportShouldRejectNonMultipartMessage() throws IOException {
        ExtractMDNOriginalJMAPMessageId testee = new ExtractMDNOriginalJMAPMessageId(mock(MailboxManager.class), mock(UsersRepository.class));

        Message message = Message.Builder.of()
            .setBody("content", StandardCharsets.UTF_8)
            .build();

        assertThat(testee.extractReport(message)).isEmpty();
    }

    @Test
    public void extractReportShouldRejectMultipartWithSinglePart() throws Exception {
        ExtractMDNOriginalJMAPMessageId testee = new ExtractMDNOriginalJMAPMessageId(mock(MailboxManager.class), mock(UsersRepository.class));

        Message message = Message.Builder.of()
            .setBody(
                MultipartBuilder.create()
                    .setSubType("report")
                    .addTextPart("content", StandardCharsets.UTF_8)
                    .build())
            .build();

        assertThat(testee.extractReport(message)).isEmpty();
    }

    @Test
    public void extractReportShouldRejectSecondPartWithBadContentType() throws IOException {
        ExtractMDNOriginalJMAPMessageId testee = new ExtractMDNOriginalJMAPMessageId(mock(MailboxManager.class), mock(UsersRepository.class));

        Message message = Message.Builder.of()
            .setBody(MultipartBuilder.create()
                .setSubType("report")
                .addTextPart("first", StandardCharsets.UTF_8)
                .addTextPart("second", StandardCharsets.UTF_8)
                .build())
            .build();

        assertThat(testee.extractReport(message)).isEmpty();
    }

    @Test
    public void extractReportShouldExtractMDNWhenValidMDN() throws IOException {
        ExtractMDNOriginalJMAPMessageId testee = new ExtractMDNOriginalJMAPMessageId(mock(MailboxManager.class), mock(UsersRepository.class));

        BodyPart mdn = BodyPartBuilder
            .create()
            .setBody(SingleBodyBuilder.create()
                .setText(
                    "Reporting-UA: linagora.com; Evolution 3.26.5-1+b1 \n" +
                        "Final-Recipient: rfc822; homer@linagora.com\n" +
                        "Original-Message-ID: <1521557867.2614.0.camel@apache.org>\n" +
                        "Disposition: manual-action/MDN-sent-manually;displayed\n")
                .buildText())
            .setContentType("message/disposition-notification")
            .build();

        Message message = Message.Builder.of()
            .setBody(MultipartBuilder.create("report")
                .addTextPart("first", StandardCharsets.UTF_8)
                .addBodyPart(mdn)
                .build())
            .build();

        assertThat(testee.extractReport(message))
            .isNotEmpty()
            .contains(mdn);
    }
}