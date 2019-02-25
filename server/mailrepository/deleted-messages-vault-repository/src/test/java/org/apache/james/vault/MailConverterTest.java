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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.server.core.MailImpl;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class MailConverterTest {

    private MailConverter mailConverter;

    @BeforeEach
    void setUp() {
        mailConverter = new MailConverter(new InMemoryId.Factory(), new InMemoryMessageId.Factory());
    }

    @Test
    void convertBackAndForthShouldPreserveDeletedMessageWithSubjectEquality() throws Exception {
        assertThat(mailConverter.fromMail(mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT))))
            .isEqualTo(DELETED_MESSAGE_WITH_SUBJECT);
    }

    @Test
    void convertBackAndForthShouldPreserveDeletedMessageWithoutSubjectEquality() throws Exception {
        assertThat(mailConverter.fromMail(mailConverter.toMail(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT))))
            .isEqualTo(DELETED_MESSAGE);
    }

    @Test
    void fromMailShouldReturnDeletedMessageWhenNoRecipient() throws Exception {
        Mail mail = mailConverter.toMail(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT));
        mail.setRecipients(ImmutableList.of());

        DeletedMessage deletedMessage = mailConverter.fromMail(mail);

        assertThat(deletedMessage.getRecipients())
            .isEmpty();
    }

    @Test
    void fromMailShouldReturnDeletedMessageWhenNoSender() throws Exception {
        MailImpl mail = mailConverter.toMail(DELETED_MESSAGE, new ByteArrayInputStream(CONTENT));
        mail.setSender(MailAddress.nullSender());

        DeletedMessage deletedMessage = mailConverter.fromMail(mail);

        assertThat(deletedMessage.getSender())
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    void toMailShouldGenerateAMailWithTheRightContent() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.setAttribute(MailConverter.OWNER_ATTRIBUTE_NAME.withValue(AttributeValue.of("notASerializedUser")));

        assertThat(new ByteArrayInputStream(MimeMessageUtil.asBytes(deletedMessageAsMail.getMessage())))
            .hasSameContentAs(new ByteArrayInputStream(CONTENT));
    }

    @Test
    void fromMailShouldThrowWhenNameIsNotAMessageId() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.setName("notAMessageId");

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMailShouldThrowWhenNoOriginMailboxes() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.removeAttribute(MailConverter.ORIGIN_MAILBOXES_ATTRIBUTE_NAME);

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMailShouldThrowWhenOriginMailboxesIsNotAList() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.setAttribute(MailConverter.ORIGIN_MAILBOXES_ATTRIBUTE_NAME.withValue(AttributeValue.of("notAList")));

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMailShouldThrowWhenOriginMailboxesContainsNonStringElements() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.setAttribute(MailConverter.ORIGIN_MAILBOXES_ATTRIBUTE_NAME.withValue(
            AttributeValue.of(ImmutableList.of(AttributeValue.of(42)))));

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMailShouldThrowWhenOriginMailboxesContainsBadFormatStringElements() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.setAttribute(MailConverter.ORIGIN_MAILBOXES_ATTRIBUTE_NAME.withValue(
            AttributeValue.of(ImmutableList.of(AttributeValue.of("badFormat")))));

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMailShouldThrowWhenNoHasAttachment() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.removeAttribute(MailConverter.HAS_ATTACHMENT_ATTRIBUTE_NAME);

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMailShouldThrowWhenHasAttachmentIsNotABoolean() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.setAttribute(MailConverter.HAS_ATTACHMENT_ATTRIBUTE_NAME.withValue(AttributeValue.of("notABoolean")));

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMailShouldThrowWhenNoOwner() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.removeAttribute(MailConverter.OWNER_ATTRIBUTE_NAME);

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMailShouldThrowWhenOwnerIsNotASerializedUser() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.setAttribute(MailConverter.OWNER_ATTRIBUTE_NAME.withValue(AttributeValue.of("notASerializedUser")));

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMailShouldThrowWhenNoDeliveryDate() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.removeAttribute(MailConverter.DELIVERY_DATE_ATTRIBUTE_NAME);

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMailShouldThrowWhenDeliveryDateIsNotASerializedDate() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.setAttribute(MailConverter.DELIVERY_DATE_ATTRIBUTE_NAME.withValue(AttributeValue.of("notADate")));

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMailShouldThrowWhenNoDeletionDate() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.removeAttribute(MailConverter.DELETION_DATE_ATTRIBUTE_NAME);

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMailShouldThrowWhenDeletionDateIsNotASerializedDate() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.setAttribute(MailConverter.DELETION_DATE_ATTRIBUTE_NAME.withValue(AttributeValue.of("notADate")));

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromMailShouldThrowWhenSubjectIsNotAString() throws Exception {
        Mail deletedMessageAsMail = mailConverter.toMail(DELETED_MESSAGE_WITH_SUBJECT, new ByteArrayInputStream(CONTENT));

        deletedMessageAsMail.setAttribute(MailConverter.SUBJECT_ATTRIBUTE_VALUE.withValue(AttributeValue.of(42)));

        assertThatThrownBy(() -> mailConverter.fromMail(deletedMessageAsMail))
            .isInstanceOf(IllegalArgumentException.class);
    }
}