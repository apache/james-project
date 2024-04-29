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
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.utils.MimeMessageHeadersUtil;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.HeaderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

public interface MessageStorer {
    /**
     * If supported by the underlying implementation, this method will parse the messageContent to retrieve associated
     * attachments and will store them.
     * <p>
     * Otherwize an empty optional will be returned on the right side of the pair.
     */
    Mono<Pair<MessageMetaData, Optional<List<MessageAttachmentMetadata>>>> appendMessageToStore(Mailbox mailbox, Date internalDate, int size, int bodyStartOctet, Content content, Flags flags, PropertyBuilder propertyBuilder, Optional<Message> maybeMessage, MailboxSession session, HeaderImpl headers) throws MailboxException;

    /**
     * MessageStorer parsing, storing and returning AttachmentMetadata
     * <p>
     * To be used with implementation that supports attachment content storage
     */
    class WithAttachment implements MessageStorer {
        private static final Logger LOGGER = LoggerFactory.getLogger(WithAttachment.class);

        private final MailboxSessionMapperFactory mapperFactory;
        private final MessageId.Factory messageIdFactory;
        private final MessageFactory messageFactory;
        private final AttachmentMapperFactory attachmentMapperFactory;
        private final MessageParser messageParser;
        private final ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm;
        private final Clock clock;

        public WithAttachment(MailboxSessionMapperFactory mapperFactory, MessageId.Factory messageIdFactory,
                              MessageFactory messageFactory, AttachmentMapperFactory attachmentMapperFactory,
                              MessageParser messageParser, ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm, Clock clock) {
            this.mapperFactory = mapperFactory;
            this.messageIdFactory = messageIdFactory;
            this.messageFactory = messageFactory;
            this.attachmentMapperFactory = attachmentMapperFactory;
            this.messageParser = messageParser;
            this.threadIdGuessingAlgorithm = threadIdGuessingAlgorithm;
            this.clock = clock;
        }

        @Override
        public Mono<Pair<MessageMetaData, Optional<List<MessageAttachmentMetadata>>>> appendMessageToStore(Mailbox mailbox, Date internalDate, int size, int bodyStartOctet, Content content, Flags flags, PropertyBuilder propertyBuilder, Optional<Message> maybeMessage, MailboxSession session, HeaderImpl headers) {
            MessageMapper messageMapper = mapperFactory.getMessageMapper(session);
            MessageId messageId = messageIdFactory.generate();
            Optional<MimeMessageId> mimeMessageId = MimeMessageHeadersUtil.parseMimeMessageId(headers);
            Optional<MimeMessageId> inReplyTo = MimeMessageHeadersUtil.parseInReplyTo(headers);
            Optional<List<MimeMessageId>> references = MimeMessageHeadersUtil.parseReferences(headers);
            Optional<Subject> subject = MimeMessageHeadersUtil.parseSubject(headers);

            return mapperFactory.getMessageMapper(session)
                .executeReactive(
                    storeAttachments(messageId, content, maybeMessage, session)
                        .subscribeOn(Schedulers.boundedElastic())
                        .zipWith(threadIdGuessingAlgorithm.guessThreadIdReactive(messageId, mimeMessageId, inReplyTo, references, subject, session))
                        .flatMap(Throwing.function((Tuple2<List<MessageAttachmentMetadata>, ThreadId> pair) -> {
                            List<MessageAttachmentMetadata> attachments = pair.getT1();
                            ThreadId threadId = pair.getT2();
                            Date saveDate = Date.from(clock.instant());

                            MailboxMessage message = messageFactory.createMessage(messageId, threadId, mailbox, internalDate, saveDate, size, bodyStartOctet, content, flags, propertyBuilder, attachments);
                            return Mono.from(messageMapper.addReactive(mailbox, message))
                                .map(metadata -> Pair.of(metadata, Optional.of(attachments)));
                        }).sneakyThrow()));
        }

        private Mono<List<MessageAttachmentMetadata>> storeAttachments(MessageId messageId, Content messageContent, Optional<Message> maybeMessage, MailboxSession session) {
            return Mono.usingWhen(Mono.fromCallable(() -> extractAttachments(messageContent, maybeMessage)),
                attachments -> attachmentMapperFactory.getAttachmentMapper(session)
                    .storeAttachmentsReactive(attachments.getAttachments(), messageId),
                parsingResults -> Mono.fromRunnable(parsingResults::dispose).subscribeOn(Schedulers.boundedElastic()));
        }

        private MessageParser.ParsingResult extractAttachments(Content contentIn, Optional<Message> maybeMessage) {
            return maybeMessage.map(message -> {
                try {
                    return new MessageParser.ParsingResult(messageParser.retrieveAttachments(message), () -> {

                    });
                } catch (Exception e) {
                    LOGGER.warn("Error while parsing mail's attachments: {}", e.getMessage(), e);
                    return MessageParser.ParsingResult.EMPTY;
                }
            }).orElseGet(() -> {
                try (InputStream inputStream = contentIn.getInputStream()) {
                    return messageParser.retrieveAttachments(inputStream);
                } catch (Exception e) {
                    LOGGER.warn("Error while parsing mail's attachments: {}", e.getMessage(), e);
                    return MessageParser.ParsingResult.EMPTY;
                }
            });
        }
    }

    /**
     * MessageStorer that does not parse, store, nor return Attachment metadata
     * <p>
     * To be used when the underlying implementation does not support attachment storage.
     */
    class WithoutAttachment implements MessageStorer {
        private final MailboxSessionMapperFactory mapperFactory;
        private final MessageId.Factory messageIdFactory;
        private final MessageFactory messageFactory;
        private final ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm;
        private final Clock clock;

        public WithoutAttachment(MailboxSessionMapperFactory mapperFactory, MessageId.Factory messageIdFactory, MessageFactory messageFactory, ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm, Clock clock) {
            this.mapperFactory = mapperFactory;
            this.messageIdFactory = messageIdFactory;
            this.messageFactory = messageFactory;
            this.threadIdGuessingAlgorithm = threadIdGuessingAlgorithm;
            this.clock = clock;
        }

        @Override
        public Mono<Pair<MessageMetaData, Optional<List<MessageAttachmentMetadata>>>> appendMessageToStore(Mailbox mailbox, Date internalDate, int size, int bodyStartOctet, Content content, Flags flags, PropertyBuilder propertyBuilder, Optional<Message> maybeMessage, MailboxSession session, HeaderImpl headers) throws MailboxException {
            MessageMapper messageMapper = mapperFactory.getMessageMapper(session);
            MessageId messageId = messageIdFactory.generate();
            Optional<MimeMessageId> mimeMessageId = MimeMessageHeadersUtil.parseMimeMessageId(headers);
            Optional<MimeMessageId> inReplyTo = MimeMessageHeadersUtil.parseInReplyTo(headers);
            Optional<List<MimeMessageId>> references = MimeMessageHeadersUtil.parseReferences(headers);
            Optional<Subject> subject = MimeMessageHeadersUtil.parseSubject(headers);

            return mapperFactory.getMessageMapper(session)
                .executeReactive(threadIdGuessingAlgorithm.guessThreadIdReactive(messageId, mimeMessageId, inReplyTo, references, subject, session)
                    .flatMap(Throwing.function((ThreadId threadId) -> {
                        Date saveDate = Date.from(clock.instant());

                        MailboxMessage message = messageFactory.createMessage(messageId, threadId, mailbox, internalDate, saveDate, size, bodyStartOctet, content, flags, propertyBuilder, ImmutableList.of());
                        return Mono.from(messageMapper.addReactive(mailbox, message))
                            .map(metadata -> Pair.of(metadata, Optional.empty()));
                    })));
        }
    }
}
