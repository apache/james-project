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

package org.apache.james.vault;

import static org.apache.james.vault.DeletedMessageFixture.CONTENT;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_WITH_SUBJECT;
import static org.apache.james.vault.DeletedMessageFixture.DELETION_DATE;
import static org.apache.james.vault.DeletedMessageFixture.DELIVERY_DATE;
import static org.apache.james.vault.DeletedMessageFixture.MAILBOX_ID_1;
import static org.apache.james.vault.DeletedMessageFixture.MAILBOX_ID_2;
import static org.apache.james.vault.DeletedMessageFixture.MESSAGE_ID;
import static org.apache.james.vault.DeletedMessageFixture.SUBJECT;
import static org.apache.james.vault.DeletedMessageFixture.USERNAME;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT2;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.StringBackedAttachmentId;
import org.apache.james.mailbox.store.MessageBuilder;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class DeletedMessageConverterTest {

    private static final String FROM_FIELD = "From";
    private static final String TO_FIELD = "To";
    private static final String DATE_FIELD = "Date";
    private static final String SUBJECT_FIELD = "Subject";
    private static final String SENDER_FIELD = "Sender";
    private static final String CC_FIELD = "cc";

    private static final List<MailboxId> ORIGIN_MAILBOXES = ImmutableList.of(MAILBOX_ID_1, MAILBOX_ID_2);
    private static final DeletedMessageVaultHook.DeletedMessageMailboxContext DELETED_MESSAGE_MAILBOX_CONTEXT = new DeletedMessageVaultHook.DeletedMessageMailboxContext(
        DeletedMessageFixture.MESSAGE_ID,
        USERNAME,
        ORIGIN_MAILBOXES);

    private static final Username EMPTY_OWNER = null;

    private static final Collection<MessageAttachmentMetadata> NO_ATTACHMENT = ImmutableList.of();
    private static final Collection<MessageAttachmentMetadata> ATTACHMENTS = ImmutableList.of(MessageAttachmentMetadata.builder()
        .attachment(AttachmentMetadata.builder()
            .attachmentId(StringBackedAttachmentId.from("1"))
            .messageId(DeletedMessageFixture.MESSAGE_ID)
            .type("type")
            .size(48)
            .build())
        .build());

    private DeletedMessageConverter deletedMessageConverter;

    private MessageBuilder getMessageBuilder() {
        return new MessageBuilder()
            .header(SENDER_FIELD, SENDER.asString())
            .header(FROM_FIELD, "alice@james.com")
            .header(TO_FIELD, RECIPIENT1.asString())
            .header(CC_FIELD, RECIPIENT2.asString())
            .header(SUBJECT_FIELD, SUBJECT)
            .header(DATE_FIELD, "Thu, 30 Oct 2014 14:12:00 +0000 (GMT)");
    }

    private MailboxMessage buildMessage(MessageBuilder messageBuilder, Collection<MessageAttachmentMetadata> attachments) throws Exception {
        MailboxMessage mailboxMessage = messageBuilder
            .size(CONTENT.length)
            .build(MESSAGE_ID);
        return SimpleMailboxMessage.fromWithoutAttachments(mailboxMessage)
            .mailboxId(mailboxMessage.getMailboxId())
            .internalDate(Date.from(DELIVERY_DATE.toInstant()))
            .addAttachments(attachments)
            .build();
    }

    @BeforeEach
    void setUp() {
        deletedMessageConverter = new DeletedMessageConverter();
    }

    @Test
    void convertShouldThrowWhenNoOwner() {
        DeletedMessageVaultHook.DeletedMessageMailboxContext deletedMessageMailboxContext = new DeletedMessageVaultHook.DeletedMessageMailboxContext(MESSAGE_ID, EMPTY_OWNER, ORIGIN_MAILBOXES);
        assertThatThrownBy(() -> deletedMessageConverter.convert(deletedMessageMailboxContext, buildMessage(getMessageBuilder(), ATTACHMENTS), DELETION_DATE))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void convertShouldReturnCorrespondingDeletedMessageWhenNoDeliveryDateInMimeMessageButInMailboxMessage() throws Exception {
        MessageBuilder builder = new MessageBuilder()
            .header(SENDER_FIELD, SENDER.asString())
            .header(FROM_FIELD, "alice@james.com")
            .header(TO_FIELD, RECIPIENT1.asString())
            .header(CC_FIELD, RECIPIENT2.asString())
            .header(SUBJECT_FIELD, SUBJECT);

        assertThat(deletedMessageConverter.convert(DELETED_MESSAGE_MAILBOX_CONTEXT, buildMessage(builder, NO_ATTACHMENT), DELETION_DATE))
            .isEqualTo(DELETED_MESSAGE_WITH_SUBJECT);
    }

    @Test
    void convertShouldReturnCorrespondingDeletedMessageWhenNoSubject() throws Exception {
        MessageBuilder builder = new MessageBuilder()
            .header(SENDER_FIELD, SENDER.asString())
            .header(FROM_FIELD, "alice@james.com")
            .header(TO_FIELD, RECIPIENT1.asString())
            .header(CC_FIELD, RECIPIENT2.asString())
            .header(DATE_FIELD, "Thu, 30 Oct 2014 14:12:00 +0000 (GMT)");

        assertThat(deletedMessageConverter.convert(DELETED_MESSAGE_MAILBOX_CONTEXT, buildMessage(builder, NO_ATTACHMENT), DELETION_DATE))
            .isEqualTo(DELETED_MESSAGE);
    }

    @Test
    void convertShouldReturnCorrespondingDeletedMessage() throws Exception {
        MessageBuilder builder = getMessageBuilder();

        assertThat(deletedMessageConverter.convert(DELETED_MESSAGE_MAILBOX_CONTEXT, buildMessage(builder, NO_ATTACHMENT), DELETION_DATE))
            .isEqualTo(DELETED_MESSAGE_WITH_SUBJECT);
    }

    @Test
    void convertShouldReturnCorrespondingDeletedMessageWhenNoRecipient() throws Exception {
        MessageBuilder builder = new MessageBuilder()
            .header(SENDER_FIELD, SENDER.asString())
            .header(FROM_FIELD, "alice@james.com")
            .header(SUBJECT_FIELD, SUBJECT)
            .header(DATE_FIELD, "Thu, 30 Oct 2014 14:12:00 +0000 (GMT)");

        DeletedMessage deletedMessage = deletedMessageConverter.convert(DELETED_MESSAGE_MAILBOX_CONTEXT, buildMessage(builder, NO_ATTACHMENT), DELETION_DATE);

        assertThat(deletedMessage.getRecipients())
            .isEmpty();
    }

    @Test
    void convertShouldReturnCorrespondingDeletedMessageWhenInvalidRecipient() throws Exception {
        MessageBuilder builder = getMessageBuilder();
        builder.header(TO_FIELD, "bad@bad@bad");
        builder.header(CC_FIELD, "dad@");

        DeletedMessage deletedMessage = deletedMessageConverter.convert(DELETED_MESSAGE_MAILBOX_CONTEXT, buildMessage(builder, NO_ATTACHMENT), DELETION_DATE);
        assertThat(deletedMessage.getRecipients())
            .isEmpty();
    }

    @Test
    void convertShouldReturnCorrespondingDeletedMessageWhenInvalidHaveOneOfBadRecipient() throws Exception {
        MessageBuilder builder = getMessageBuilder();
        builder.header(TO_FIELD, "bad@bad@bad");

        DeletedMessage deletedMessage = deletedMessageConverter.convert(DELETED_MESSAGE_MAILBOX_CONTEXT, buildMessage(builder, NO_ATTACHMENT), DELETION_DATE);
        assertThat(deletedMessage.getRecipients())
            .containsOnly(RECIPIENT2);
    }

    @Test
    void convertShouldReturnCorrespondingDeletedMessageWhenNoSender() throws Exception {
        MessageBuilder builder = new MessageBuilder()
            .header(FROM_FIELD, "alice@james.com")
            .header(TO_FIELD, RECIPIENT1.asString())
            .header(CC_FIELD, RECIPIENT2.asString())
            .header(SUBJECT_FIELD, SUBJECT)
            .header(DATE_FIELD, "Thu, 30 Oct 2014 14:12:00 +0000 (GMT)");

        DeletedMessage deletedMessage = deletedMessageConverter.convert(DELETED_MESSAGE_MAILBOX_CONTEXT, buildMessage(builder, NO_ATTACHMENT), DELETION_DATE);

        assertThat(deletedMessage.getSender())
            .isEqualTo(MaybeSender.nullSender());
    }
}
