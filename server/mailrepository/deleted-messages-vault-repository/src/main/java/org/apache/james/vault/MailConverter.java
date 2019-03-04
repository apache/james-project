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

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.User;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

class MailConverter {
    static final AttributeName ORIGIN_MAILBOXES_ATTRIBUTE_NAME = AttributeName.of("originMailboxes");
    static final AttributeName HAS_ATTACHMENT_ATTRIBUTE_NAME = AttributeName.of("hasAttachment");
    static final AttributeName OWNER_ATTRIBUTE_NAME = AttributeName.of("owner");
    static final AttributeName DELIVERY_DATE_ATTRIBUTE_NAME = AttributeName.of("deliveryDate");
    static final AttributeName DELETION_DATE_ATTRIBUTE_NAME = AttributeName.of("deletionDate");
    static final AttributeName SUBJECT_ATTRIBUTE_VALUE = AttributeName.of("subject");

    private final MailboxId.Factory mailboxIdFactory;
    private final MessageId.Factory messageIdFactory;

    @Inject
    MailConverter(MailboxId.Factory mailboxIdFactory, MessageId.Factory messageIdFactory) {
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageIdFactory = messageIdFactory;
    }

    MailImpl toMail(DeletedMessage deletedMessage, InputStream inputStream) throws MessagingException {
        return MailImpl.builder()
            .name(deletedMessage.getMessageId().serialize())
            .sender(deletedMessage.getSender())
            .addRecipients(deletedMessage.getRecipients())
            .mimeMessage(new MimeMessage(Session.getDefaultInstance(new Properties()), inputStream))
            .addAttribute(OWNER_ATTRIBUTE_NAME.withValue(SerializableUser.toAttributeValue(deletedMessage.getOwner())))
            .addAttribute(HAS_ATTACHMENT_ATTRIBUTE_NAME.withValue(AttributeValue.of(deletedMessage.hasAttachment())))
            .addAttribute(DELIVERY_DATE_ATTRIBUTE_NAME.withValue(SerializableDate.toAttributeValue(deletedMessage.getDeliveryDate())))
            .addAttribute(DELETION_DATE_ATTRIBUTE_NAME.withValue(SerializableDate.toAttributeValue(deletedMessage.getDeletionDate())))
            .addAttribute(ORIGIN_MAILBOXES_ATTRIBUTE_NAME.withValue(AttributeValue.of(serializedMailboxIds(deletedMessage))))
            .addAttribute(SUBJECT_ATTRIBUTE_VALUE.withValue(subjectAttributeValue(deletedMessage)))
            .build();
    }

    DeletedMessage fromMail(Mail mail) {
        return DeletedMessage.builder()
            .messageId(messageIdFactory.fromString(mail.getName()))
            .originMailboxes(retrieveMailboxIds(mail))
            .user(retrieveOwner(mail))
            .deliveryDate(retrieveDate(mail, DELIVERY_DATE_ATTRIBUTE_NAME))
            .deletionDate(retrieveDate(mail, DELETION_DATE_ATTRIBUTE_NAME))
            .sender(mail.getMaybeSender())
            .recipients(mail.getRecipients())
            .hasAttachment(retrieveHasAttachment(mail))
            .subject(retrieveSubject(mail))
            .build();
    }

    private ImmutableList<AttributeValue<?>> serializedMailboxIds(DeletedMessage deletedMessage) {
        return deletedMessage.getOriginMailboxes().stream()
            .map(MailboxId::serialize)
            .map(AttributeValue::of)
            .collect(Guavate.toImmutableList());
    }

    private AttributeValue<Optional<AttributeValue<String>>> subjectAttributeValue(DeletedMessage deletedMessage) {
        return AttributeValue.of(deletedMessage.getSubject().map(AttributeValue::of));
    }

    private Optional<String> retrieveSubject(Mail mail) {
        return AttributeUtils.getValueAndCastFromMail(mail, SUBJECT_ATTRIBUTE_VALUE, Optional.class)
                .map(this::retrieveSubject)
                .orElseThrow(() -> new IllegalArgumentException("mail should have a 'subject' attribute being of type 'Optional<String>"));
    }

    private Optional<String> retrieveSubject(Optional<?> maybeSubject) {
        return maybeSubject.map(this::castSubjectToString);
    }

    private String castSubjectToString(Object object) {
        Optional<String> optional = Optional.of(object)
            .filter(obj -> obj instanceof AttributeValue)
            .map(AttributeValue.class::cast)
            .flatMap(attributeValue -> attributeValue.valueAs(String.class));

        return optional
            .orElseThrow(() -> new IllegalArgumentException("mail should have a 'subject' attribute being of type 'Optional<String>"));
    }

    private boolean retrieveHasAttachment(Mail mail) {
        return AttributeUtils.getValueAndCastFromMail(mail, HAS_ATTACHMENT_ATTRIBUTE_NAME, Boolean.class)
            .orElseThrow(() -> new IllegalArgumentException("mail should have a 'hasAttachment' attribute of type boolean"));
    }

    private ZonedDateTime retrieveDate(Mail mail, AttributeName attributeName) {
        return AttributeUtils.getValueAndCastFromMail(mail, attributeName, SerializableDate.class)
            .map(SerializableDate::getValue)
            .orElseThrow(() -> new IllegalArgumentException("'mail' should have a '" + attributeName.asString() + "' attribute of type SerializableDate"));
    }

    private User retrieveOwner(Mail mail) {
        return AttributeUtils.getValueAndCastFromMail(mail, OWNER_ATTRIBUTE_NAME, SerializableUser.class)
            .map(SerializableUser::getValue)
            .orElseThrow(() -> new IllegalArgumentException("Supplied email is missing the 'owner' attribute of type SerializableUser"));
    }

    private List<MailboxId> retrieveMailboxIds(Mail mail) {
        return AttributeUtils.getValueAndCastFromMail(mail, ORIGIN_MAILBOXES_ATTRIBUTE_NAME, List.class)
            .map(this::retrieveMailboxIds)
            .orElseThrow(() -> new IllegalArgumentException("Supplied email is missing the 'originMailboxes' attribute of type List<String>"));
    }

    private List<MailboxId> retrieveMailboxIds(List<?> list) {
        return list.stream()
            .map(this::retrieveMailboxId)
            .collect(Guavate.toImmutableList());
    }

    private MailboxId retrieveMailboxId(Object object) {
        Optional<String> serializedMailboxId = Optional.of(object)
            .filter(obj -> obj instanceof AttributeValue)
            .map(AttributeValue.class::cast)
            .flatMap(attributeValue -> attributeValue.valueAs(String.class));

        return serializedMailboxId
            .map(mailboxIdFactory::fromString)
            .orElseThrow(() -> new IllegalArgumentException("Found a non String element in originMailboxes attribute"));
    }
}
