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

package org.apache.james.mailbox.store;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mime4j.dom.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public interface MessageStorer {
    /**
     * If supported by the underlying implementation, this method will parse the messageContent to retrieve associated
     * attachments and will store them.
     *
     * Otherwize an empty optional will be returned on the right side of the pair.
     */
    Pair<MessageMetaData, Optional<List<MessageAttachmentMetadata>>> appendMessageToStore(Mailbox mailbox, Date internalDate, int size, int bodyStartOctet, Content content, Flags flags, PropertyBuilder propertyBuilder, Optional<Message> maybeMessage, MailboxSession session) throws MailboxException;

    /**
     * MessageStorer parsing, storing and returning AttachmentMetadata
     *
     * To be used with implementation that supports attachment content storage
     */
    class WithAttachment implements MessageStorer {
        private static final Logger LOGGER = LoggerFactory.getLogger(WithAttachment.class);
        private static final int START = 0;
        private static final int UNLIMITED = -1;

        private final MailboxSessionMapperFactory mapperFactory;
        private final MessageId.Factory messageIdFactory;
        private final MessageFactory messageFactory;
        private final AttachmentMapperFactory attachmentMapperFactory;
        private final MessageParser messageParser;

        public WithAttachment(MailboxSessionMapperFactory mapperFactory, MessageId.Factory messageIdFactory,
                              MessageFactory messageFactory, AttachmentMapperFactory attachmentMapperFactory,
                              MessageParser messageParser) {
            this.mapperFactory = mapperFactory;
            this.messageIdFactory = messageIdFactory;
            this.messageFactory = messageFactory;
            this.attachmentMapperFactory = attachmentMapperFactory;
            this.messageParser = messageParser;
        }

        @Override
        public Pair<MessageMetaData, Optional<List<MessageAttachmentMetadata>>> appendMessageToStore(Mailbox mailbox, Date internalDate, int size, int bodyStartOctet, Content content, Flags flags, PropertyBuilder propertyBuilder, Optional<Message> maybeMessage, MailboxSession session) throws MailboxException {
            MessageMapper messageMapper = mapperFactory.getMessageMapper(session);
            MessageId messageId = messageIdFactory.generate();

            return mapperFactory.getMessageMapper(session).execute(() -> {
                List<MessageAttachmentMetadata> attachments = storeAttachments(messageId, content, maybeMessage, session);
                MailboxMessage message = messageFactory.createMessage(messageId, mailbox, internalDate, size, bodyStartOctet, content, flags, propertyBuilder, attachments);
                MessageMetaData metadata = messageMapper.add(mailbox, message);
                return Pair.of(metadata, Optional.of(attachments));
            });
        }

        private List<MessageAttachmentMetadata> storeAttachments(MessageId messageId, Content messageContent, Optional<Message> maybeMessage, MailboxSession session) throws MailboxException {
            List<ParsedAttachment> attachments = extractAttachments(messageContent, maybeMessage);
            return attachmentMapperFactory.getAttachmentMapper(session)
                .storeAttachmentsForMessage(attachments, messageId);
        }

        private List<ParsedAttachment> extractAttachments(Content contentIn, Optional<Message> maybeMessage) {
            return maybeMessage.map(message -> {
                try {
                    return messageParser.retrieveAttachments(message);
                } catch (Exception e) {
                    LOGGER.warn("Error while parsing mail's attachments: {}", e.getMessage(), e);
                    return ImmutableList.<ParsedAttachment>of();
                }
            }).orElseGet(() -> {
                try (InputStream inputStream = contentIn.getInputStream()) {
                    return messageParser.retrieveAttachments(inputStream);
                } catch (Exception e) {
                    LOGGER.warn("Error while parsing mail's attachments: {}", e.getMessage(), e);
                    return ImmutableList.of();
                }
            });
        }
    }

    /**
     * MessageStorer that does not parse, store, nor return Attachment metadata
     *
     * To be used when the underlying implementation does not support attachment storage.
     */
    class WithoutAttachment implements MessageStorer {
        private final MailboxSessionMapperFactory mapperFactory;
        private final MessageId.Factory messageIdFactory;
        private final MessageFactory messageFactory;

        public WithoutAttachment(MailboxSessionMapperFactory mapperFactory, MessageId.Factory messageIdFactory, MessageFactory messageFactory) {
            this.mapperFactory = mapperFactory;
            this.messageIdFactory = messageIdFactory;
            this.messageFactory = messageFactory;
        }

        @Override
        public Pair<MessageMetaData, Optional<List<MessageAttachmentMetadata>>> appendMessageToStore(Mailbox mailbox, Date internalDate, int size, int bodyStartOctet, Content content, Flags flags, PropertyBuilder propertyBuilder, Optional<Message> maybeMessage, MailboxSession session) throws MailboxException {
            MessageMapper messageMapper = mapperFactory.getMessageMapper(session);
            MessageId messageId = messageIdFactory.generate();

            return mapperFactory.getMessageMapper(session).execute(() -> {
                MailboxMessage message = messageFactory.createMessage(messageId, mailbox, internalDate, size, bodyStartOctet, content, flags, propertyBuilder, ImmutableList.of());
                MessageMetaData metadata = messageMapper.add(mailbox, message);
                return Pair.of(metadata, Optional.empty());
            });
        }
    }
}
