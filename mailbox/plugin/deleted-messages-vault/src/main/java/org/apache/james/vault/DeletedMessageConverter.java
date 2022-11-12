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

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.mime4j.MimeIOException;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.server.core.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

class DeletedMessageConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeletedMessageConverter.class);

    DeletedMessage convert(DeletedMessageVaultHook.DeletedMessageMailboxContext deletedMessageMailboxContext, org.apache.james.mailbox.store.mail.model.Message message, ZonedDateTime deletionDate) throws IOException {
        Preconditions.checkNotNull(deletedMessageMailboxContext);
        Preconditions.checkNotNull(message);

        Optional<Message> mimeMessage = parseMessage(message);

        DeletedMessage deletedMessage = DeletedMessage.builder()
            .messageId(deletedMessageMailboxContext.getMessageId())
            .originMailboxes(deletedMessageMailboxContext.getOwnerMailboxes())
            .user(retrieveOwner(deletedMessageMailboxContext))
            .deliveryDate(retrieveDeliveryDate(mimeMessage, message))
            .deletionDate(deletionDate)
            .sender(retrieveSender(mimeMessage))
            .recipients(retrieveRecipients(mimeMessage))
            .hasAttachment(!message.getAttachments().isEmpty())
            .size(message.getFullContentOctets())
            .subject(mimeMessage.map(Message::getSubject))
            .build();
        mimeMessage.ifPresent(Message::dispose);
        return deletedMessage;
    }

    private Optional<Message> parseMessage(org.apache.james.mailbox.store.mail.model.Message message) throws IOException {
        DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
        messageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        messageBuilder.setDecodeMonitor(DecodeMonitor.SILENT);
        try {
            return Optional.ofNullable(messageBuilder.parseMessage(message.getFullContent()));
        } catch (MimeIOException e) {
            LOGGER.warn("Can not parse the message {}", message.getMessageId(), e);
            return Optional.empty();
        }
    }

    private Username retrieveOwner(DeletedMessageVaultHook.DeletedMessageMailboxContext deletedMessageMailboxContext) {
        Preconditions.checkNotNull(deletedMessageMailboxContext.getOwner(), "Deleted mail is missing owner");
        return deletedMessageMailboxContext.getOwner();
    }

    private ZonedDateTime retrieveDeliveryDate(Optional<Message> mimeMessage, org.apache.james.mailbox.store.mail.model.Message message) {
        return mimeMessage.map(Message::getDate)
            .map(Date::toInstant)
            .map(instant -> ZonedDateTime.ofInstant(instant, ZoneOffset.UTC))
            .orElse(ZonedDateTime.ofInstant(message.getInternalDate().toInstant(), ZoneOffset.UTC));
    }

    private MaybeSender retrieveSender(Optional<Message> mimeMessage) {
        return mimeMessage
            .map(Message::getSender)
            .map(Mailbox::getAddress)
            .map(MaybeSender::getMailSender)
            .orElse(MaybeSender.nullSender());
    }

    private Set<MailAddress> retrieveRecipients(Optional<Message> maybeMessage) {
        return maybeMessage.map(message -> Envelope.fromMime4JMessage(message, Envelope.ValidationPolicy.IGNORE))
            .map(Envelope::getRecipients)
            .orElse(ImmutableSet.of());
    }
}
