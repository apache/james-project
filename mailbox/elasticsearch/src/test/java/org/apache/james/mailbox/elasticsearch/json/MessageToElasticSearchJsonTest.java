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

package org.apache.james.mailbox.elasticsearch.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_VALUES;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.extractor.JsoupTextExtractor;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaExtension;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

class MessageToElasticSearchJsonTest {
    static final int SIZE = 25;
    static final int BODY_START_OCTET = 100;
    static final TestId MAILBOX_ID = TestId.of(18L);
    static final MessageId MESSAGE_ID = TestMessageId.of(184L);
    static final ModSeq MOD_SEQ = ModSeq.of(42L);
    static final MessageUid UID = MessageUid.of(25);
    static final Username USERNAME = Username.of("username");

    TextExtractor textExtractor;
    Date date;
    PropertyBuilder propertyBuilder;

    @RegisterExtension
    static TikaExtension tika = new TikaExtension();

    @BeforeEach
    void setUp() throws Exception {
        textExtractor = new TikaTextExtractor(new RecordingMetricFactory(), new TikaHttpClientImpl(TikaConfiguration.builder()
                .host(tika.getIp())
                .port(tika.getPort())
                .timeoutInMillis(tika.getTimeoutInMillis())
                .build()));
        // 2015/06/07 00:00:00 0200 (Paris time zone)
        date = new Date(1433628000000L);
        propertyBuilder = new PropertyBuilder();
        propertyBuilder.setMediaType("plain");
        propertyBuilder.setSubType("text");
        propertyBuilder.setTextualLineCount(18L);
        propertyBuilder.setContentDescription("An e-mail");
    }

    @Test
    void spamEmailShouldBeWellConvertedToJson() throws IOException {
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("Europe/Paris"), IndexAttachments.YES);
        MailboxMessage spamMail = new SimpleMailboxMessage(MESSAGE_ID,
                date,
                SIZE,
                BODY_START_OCTET,
                ClassLoaderUtils.getSystemResourceAsSharedStream("eml/spamMail.eml"),
                new Flags(),
                propertyBuilder,
                MAILBOX_ID);
        spamMail.setUid(UID);
        spamMail.setModSeq(MOD_SEQ);
        assertThatJson(messageToElasticSearchJson.convertToJson(spamMail, ImmutableList.of(USERNAME)))
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("eml/spamMail.json"));
    }

    @Test
    void htmlEmailShouldBeWellConvertedToJson() throws IOException {
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("Europe/Paris"), IndexAttachments.YES);
        MailboxMessage htmlMail = new SimpleMailboxMessage(MESSAGE_ID,
                date,
                SIZE,
                BODY_START_OCTET,
                ClassLoaderUtils.getSystemResourceAsSharedStream("eml/htmlMail.eml"),
                new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("social", "pocket-money").build(),
                propertyBuilder,
                MAILBOX_ID);
        htmlMail.setModSeq(MOD_SEQ);
        htmlMail.setUid(UID);
        assertThatJson(messageToElasticSearchJson.convertToJson(htmlMail, ImmutableList.of(USERNAME)))
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("eml/htmlMail.json"));
    }

    @Test
    void pgpSignedEmailShouldBeWellConvertedToJson() throws IOException {
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("Europe/Paris"), IndexAttachments.YES);
        MailboxMessage pgpSignedMail = new SimpleMailboxMessage(MESSAGE_ID,
                date,
                SIZE,
                BODY_START_OCTET,
                ClassLoaderUtils.getSystemResourceAsSharedStream("eml/pgpSignedMail.eml"),
                new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("debian", "security").build(),
                propertyBuilder,
                MAILBOX_ID);
        pgpSignedMail.setModSeq(MOD_SEQ);
        pgpSignedMail.setUid(UID);
        assertThatJson(messageToElasticSearchJson.convertToJson(pgpSignedMail, ImmutableList.of(USERNAME)))
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("eml/pgpSignedMail.json"));
    }

    @Test
    void simpleEmailShouldBeWellConvertedToJson() throws IOException {
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("Europe/Paris"), IndexAttachments.YES);
        MailboxMessage mail = new SimpleMailboxMessage(MESSAGE_ID,
                date,
                SIZE,
                BODY_START_OCTET,
                ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mail.eml"),
                new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("debian", "security").build(),
                propertyBuilder,
                MAILBOX_ID);
        mail.setModSeq(MOD_SEQ);
        mail.setUid(UID);
        assertThatJson(messageToElasticSearchJson.convertToJson(mail,
                ImmutableList.of(Username.of("user1"), Username.of("user2"))))
            .when(IGNORING_ARRAY_ORDER).when(IGNORING_VALUES)
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("eml/mail.json"));
    }

    @Test
    void recursiveEmailShouldBeWellConvertedToJson() throws IOException {
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("Europe/Paris"), IndexAttachments.YES);
        MailboxMessage recursiveMail = new SimpleMailboxMessage(MESSAGE_ID, 
                date,
                SIZE,
                BODY_START_OCTET,
                ClassLoaderUtils.getSystemResourceAsSharedStream("eml/recursiveMail.eml"),
                new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("debian", "security").build(),
                propertyBuilder,
                MAILBOX_ID);
        recursiveMail.setModSeq(MOD_SEQ);
        recursiveMail.setUid(UID);
        assertThatJson(messageToElasticSearchJson.convertToJson(recursiveMail, ImmutableList.of(USERNAME)))
            .when(IGNORING_ARRAY_ORDER).when(IGNORING_VALUES)
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("eml/recursiveMail.json"));
    }

    @Test
    void emailWithNoInternalDateShouldUseNowDate() throws IOException {
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("Europe/Paris"), IndexAttachments.YES);
        MailboxMessage mailWithNoInternalDate = new SimpleMailboxMessage(MESSAGE_ID,
                null,
                SIZE,
                BODY_START_OCTET,
                ClassLoaderUtils.getSystemResourceAsSharedStream("eml/recursiveMail.eml"),
                new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("debian", "security").build(),
                propertyBuilder,
                MAILBOX_ID);
        mailWithNoInternalDate.setModSeq(MOD_SEQ);
        mailWithNoInternalDate.setUid(UID);
        assertThatJson(messageToElasticSearchJson.convertToJson(mailWithNoInternalDate, ImmutableList.of(USERNAME)))
            .when(IGNORING_ARRAY_ORDER)
            .when(IGNORING_VALUES)
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("eml/recursiveMail.json"));
    }

    @Test
    void emailWithAttachmentsShouldConvertAttachmentsWhenIndexAttachmentsIsTrue() throws IOException {
        // Given
        MailboxMessage mailWithNoInternalDate = new SimpleMailboxMessage(MESSAGE_ID,
                null,
                SIZE,
                BODY_START_OCTET,
                ClassLoaderUtils.getSystemResourceAsSharedStream("eml/recursiveMail.eml"),
                new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("debian", "security").build(),
                propertyBuilder,
                MAILBOX_ID);
        mailWithNoInternalDate.setModSeq(MOD_SEQ);
        mailWithNoInternalDate.setUid(UID);

        // When
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("Europe/Paris"),
            IndexAttachments.YES);
        String convertToJson = messageToElasticSearchJson.convertToJson(mailWithNoInternalDate, ImmutableList.of(USERNAME));

        // Then
        assertThatJson(convertToJson)
            .when(IGNORING_ARRAY_ORDER)
            .when(IGNORING_VALUES)
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("eml/recursiveMail.json"));
    }

    @Test
    void emailWithAttachmentsShouldNotConvertAttachmentsWhenIndexAttachmentsIsFalse() throws IOException {
        // Given
        MailboxMessage mailWithNoInternalDate = new SimpleMailboxMessage(MESSAGE_ID,
                null,
                SIZE,
                BODY_START_OCTET,
                ClassLoaderUtils.getSystemResourceAsSharedStream("eml/recursiveMail.eml"),
                new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("debian", "security").build(),
                propertyBuilder,
                MAILBOX_ID);
        mailWithNoInternalDate.setModSeq(MOD_SEQ);
        mailWithNoInternalDate.setUid(UID);

        // When
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("Europe/Paris"),
            IndexAttachments.NO);
        String convertToJson = messageToElasticSearchJson.convertToJson(mailWithNoInternalDate, ImmutableList.of(USERNAME));

        // Then
        assertThatJson(convertToJson)
            .when(IGNORING_ARRAY_ORDER)
            .when(IGNORING_VALUES)
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("eml/recursiveMailWithoutAttachments.json"));
    }

    @Test
    void emailWithNoMailboxIdShouldThrow() {
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("Europe/Paris"), IndexAttachments.YES);
        MailboxMessage mailWithNoMailboxId = new SimpleMailboxMessage(MESSAGE_ID, date,
            SIZE,
            BODY_START_OCTET,
            ClassLoaderUtils.getSystemResourceAsSharedStream("eml/recursiveMail.eml"),
            new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("debian", "security").build(),
            propertyBuilder,
            null);
        mailWithNoMailboxId.setModSeq(MOD_SEQ);
        mailWithNoMailboxId.setUid(UID);

        assertThatThrownBy(() ->
            messageToElasticSearchJson.convertToJson(mailWithNoMailboxId, ImmutableList.of(USERNAME)))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getUpdatedJsonMessagePartShouldBehaveWellOnEmptyFlags() throws Exception {
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("Europe/Paris"),
            IndexAttachments.YES);
        assertThatJson(messageToElasticSearchJson.getUpdatedJsonMessagePart(new Flags(), MOD_SEQ))
            .isEqualTo("{\"modSeq\":42,\"isAnswered\":false,\"isDeleted\":false,\"isDraft\":false,\"isFlagged\":false,\"isRecent\":false,\"userFlags\":[],\"isUnread\":true}");
    }

    @Test
    void getUpdatedJsonMessagePartShouldBehaveWellOnNonEmptyFlags() throws Exception {
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("Europe/Paris"),
            IndexAttachments.YES);
        assertThatJson(messageToElasticSearchJson.getUpdatedJsonMessagePart(new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.FLAGGED).add("user").build(), MOD_SEQ))
            .isEqualTo("{\"modSeq\":42,\"isAnswered\":false,\"isDeleted\":true,\"isDraft\":false,\"isFlagged\":true,\"isRecent\":false,\"userFlags\":[\"user\"],\"isUnread\":true}");
    }

    @Test
    void getUpdatedJsonMessagePartShouldThrowIfFlagsIsNull() {
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("Europe/Paris"),
            IndexAttachments.YES);

        assertThatThrownBy(() -> messageToElasticSearchJson.getUpdatedJsonMessagePart(null, MOD_SEQ))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void spamEmailShouldBeWellConvertedToJsonWithApacheTika() throws IOException {
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            textExtractor,
            ZoneId.of("Europe/Paris"),
            IndexAttachments.YES);
        MailboxMessage spamMail = new SimpleMailboxMessage(MESSAGE_ID, date,
            SIZE,
            BODY_START_OCTET,
            ClassLoaderUtils.getSystemResourceAsSharedStream("eml/nonTextual.eml"),
            new Flags(),
            propertyBuilder,
            MAILBOX_ID);
        spamMail.setUid(UID);
        spamMail.setModSeq(MOD_SEQ);

        assertThatJson(messageToElasticSearchJson.convertToJson(spamMail, ImmutableList.of(USERNAME)))
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo(
                ClassLoaderUtils.getSystemResourceAsString("eml/nonTextual.json", StandardCharsets.UTF_8));
    }

    @Test
    void convertToJsonWithoutAttachmentShouldConvertEmailBoby() throws IOException {
        // Given
        MailboxMessage message = new SimpleMailboxMessage(MESSAGE_ID,
            null,
            SIZE,
            BODY_START_OCTET,
            ClassLoaderUtils.getSystemResourceAsSharedStream("eml/emailWithNonIndexableAttachment.eml"),
            new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("debian", "security").build(),
            propertyBuilder,
            MAILBOX_ID);
        message.setModSeq(MOD_SEQ);
        message.setUid(UID);

        // When
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
                new DefaultTextExtractor(),
                ZoneId.of("Europe/Paris"),
                IndexAttachments.NO);
        String convertToJsonWithoutAttachment = messageToElasticSearchJson.convertToJsonWithoutAttachment(message, ImmutableList.of(USERNAME));

        // Then
        assertThatJson(convertToJsonWithoutAttachment)
            .when(IGNORING_ARRAY_ORDER)
            .when(IGNORING_VALUES)
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("eml/emailWithNonIndexableAttachmentWithoutAttachment.json"));
    }

    @Test
    void convertToJsonShouldExtractHtmlText() throws IOException {
        // Given
        MailboxMessage message = new SimpleMailboxMessage(MESSAGE_ID,
            date,
            SIZE,
            BODY_START_OCTET,
            ClassLoaderUtils.getSystemResourceAsSharedStream("eml/emailWithNonIndexableAttachment.eml"),
            new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("debian", "security").build(),
            propertyBuilder,
            MAILBOX_ID);
        message.setModSeq(MOD_SEQ);
        message.setUid(UID);

        // When
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
                new JsoupTextExtractor(),
                ZoneId.of("Europe/Paris"),
                IndexAttachments.NO);
        String convertToJsonWithoutAttachment = messageToElasticSearchJson.convertToJsonWithoutAttachment(message, ImmutableList.of(USERNAME));

        System.out.println(convertToJsonWithoutAttachment);

        // Then
        assertThatJson(convertToJsonWithoutAttachment)
            .when(IGNORING_ARRAY_ORDER)
            .inPath("htmlBody")
            .isString()
            .isEqualTo(ClassLoaderUtils.getSystemResourceAsString("eml/htmlContent.txt", StandardCharsets.UTF_8));
    }
}
