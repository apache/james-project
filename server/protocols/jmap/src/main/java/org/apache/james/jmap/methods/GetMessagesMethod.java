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

package org.apache.james.jmap.methods;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.json.FieldNamePropertyFilter;
import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetMessagesRequest;
import org.apache.james.jmap.model.GetMessagesResponse;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessageProperties;
import org.apache.james.jmap.model.MessageProperties.HeaderProperty;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.Attachment;
import org.apache.james.mailbox.store.mail.model.AttachmentId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.util.streams.Collectors;
import org.javatuples.Pair;

import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class GetMessagesMethod implements Method {

    public static final String HEADERS_FILTER = "headersFilter";
    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMessages");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messages");
    private final MessageMapperFactory messageMapperFactory;
    private final MailboxMapperFactory mailboxMapperFactory;
    private final AttachmentMapperFactory attachmentMapperFactory;

    @Inject
    @VisibleForTesting GetMessagesMethod(
            MessageMapperFactory messageMapperFactory,
            MailboxMapperFactory mailboxMapperFactory,
            AttachmentMapperFactory attachmentMapperFactory) {
        this.messageMapperFactory = messageMapperFactory;
        this.mailboxMapperFactory = mailboxMapperFactory;
        this.attachmentMapperFactory = attachmentMapperFactory;
    }
    
    @Override
    public Method.Request.Name requestHandled() {
        return METHOD_NAME;
    }
    
    @Override
    public Class<? extends JmapRequest> requestType() {
        return GetMessagesRequest.class;
    }
    
    @Override
    public Stream<JmapResponse> process(JmapRequest request, ClientId clientId, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(mailboxSession);
        Preconditions.checkArgument(request instanceof GetMessagesRequest);
        GetMessagesRequest getMessagesRequest = (GetMessagesRequest) request;
        MessageProperties outputProperties = getMessagesRequest.getProperties().toOutputProperties();
        return Stream.of(JmapResponse.builder().clientId(clientId)
                            .response(getMessagesResponse(mailboxSession, getMessagesRequest))
                            .responseName(RESPONSE_NAME)
                            .properties(outputProperties.getOptionalMessageProperties())
                            .filterProvider(buildOptionalHeadersFilteringFilterProvider(outputProperties))
                            .build());
    }

    private Optional<SimpleFilterProvider> buildOptionalHeadersFilteringFilterProvider(MessageProperties properties) {
        return properties.getOptionalHeadersProperties()
            .map(this::buildHeadersPropertyFilter)
            .map(propertyFilter -> new SimpleFilterProvider()
                .addFilter(HEADERS_FILTER, propertyFilter));
    }
    
    private PropertyFilter buildHeadersPropertyFilter(ImmutableSet<HeaderProperty> headerProperties) {
        return new FieldNamePropertyFilter((fieldName) -> headerProperties.contains(HeaderProperty.fromFieldName(fieldName)));
    }

    private GetMessagesResponse getMessagesResponse(MailboxSession mailboxSession, GetMessagesRequest getMessagesRequest) {
        getMessagesRequest.getAccountId().ifPresent(GetMessagesMethod::notImplemented);
        
        Function<MessageId, Stream<CompletedMailboxMessage>> loadMessages = loadMessage(mailboxSession);
        Function<CompletedMailboxMessage, Message> convertToJmapMessage = toJmapMessage(mailboxSession);
        
        List<Message> result = getMessagesRequest.getIds().stream()
            .flatMap(loadMessages)
            .map(convertToJmapMessage)
            .collect(Collectors.toImmutableList());

        return GetMessagesResponse.builder().messages(result).expectedMessageIds(getMessagesRequest.getIds()).build();
    }

    private static void notImplemented(String input) {
        throw new NotImplementedException();
    }

    
    private Function<CompletedMailboxMessage, Message> toJmapMessage(MailboxSession mailboxSession) {
        return (completedMailboxMessage) -> Message.fromMailboxMessage(
                completedMailboxMessage.mailboxMessage, 
                completedMailboxMessage.attachments, 
                uid -> new MessageId(mailboxSession.getUser(), completedMailboxMessage.mailboxPath , uid));
    }

    private Function<MessageId, Stream<CompletedMailboxMessage>> 
                loadMessage(MailboxSession mailboxSession) {

        return Throwing
                .function((MessageId messageId) -> {
                     MailboxPath mailboxPath = messageId.getMailboxPath();
                     MessageMapper messageMapper = messageMapperFactory.getMessageMapper(mailboxSession);
                     Mailbox mailbox = mailboxMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(mailboxPath);
                     return Pair.with(
                             messageMapper.findInMailbox(mailbox, MessageRange.one(messageId.getUid()), MessageMapper.FetchType.Full, 1),
                             mailboxPath
                             );
                })
                .andThen(Throwing.function((pair) -> retrieveCompleteMailboxMessages(pair, mailboxSession)));
    }
    
    private Stream<CompletedMailboxMessage> retrieveCompleteMailboxMessages(Pair<Iterator<MailboxMessage>, MailboxPath> value, MailboxSession mailboxSession) throws MailboxException {
        Iterable<MailboxMessage> iterable = () -> value.getValue0();
        Stream<MailboxMessage> targetStream = StreamSupport.stream(iterable.spliterator(), false);

        Function<List<AttachmentId>, List<Attachment>> retrieveAttachments = retrieveAttachments(attachmentMapperFactory.getAttachmentMapper(mailboxSession));

        MailboxPath mailboxPath = value.getValue1();
        return targetStream
                .map(message -> CompletedMailboxMessage.builder().mailboxMessage(message).attachmentIds(message.getAttachmentsIds()))
                .map(builder -> builder.mailboxPath(mailboxPath))
                .map(builder -> builder.retrieveAttachments(retrieveAttachments))
                .map(builder -> builder.build()); 
    }

    private static class CompletedMailboxMessage {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private MailboxMessage mailboxMessage;
            private List<AttachmentId> attachmentIds;
            private MailboxPath mailboxPath;
            private Function<List<AttachmentId>, List<Attachment>> retrieveAttachments;

            private Builder() {
            }

            public Builder mailboxMessage(MailboxMessage mailboxMessage) {
                Preconditions.checkArgument(mailboxMessage != null);
                this.mailboxMessage = mailboxMessage;
                return this;
            }

            public Builder attachmentIds(List<AttachmentId> attachmentIds) {
                Preconditions.checkArgument(attachmentIds != null);
                this.attachmentIds = attachmentIds;
                return this;
            }

            public Builder mailboxPath(MailboxPath mailboxPath) {
                Preconditions.checkArgument(mailboxPath != null);
                this.mailboxPath = mailboxPath;
                return this;
            }

            public Builder retrieveAttachments(Function<List<AttachmentId>, List<Attachment>> retrieveAttachments) {
                Preconditions.checkArgument(retrieveAttachments != null);
                this.retrieveAttachments = retrieveAttachments;
                return this;
            }

            public CompletedMailboxMessage build() {
                Preconditions.checkState(mailboxMessage != null);
                Preconditions.checkState(attachmentIds != null);
                Preconditions.checkState(mailboxPath != null);
                Preconditions.checkState(retrieveAttachments != null);
                return new CompletedMailboxMessage(mailboxMessage, retrieveAttachments.apply(attachmentIds), mailboxPath);
            }
        }

        private final MailboxMessage mailboxMessage;
        private final List<Attachment> attachments;
        private final MailboxPath mailboxPath;

        public CompletedMailboxMessage(MailboxMessage mailboxMessage, List<Attachment> attachments, MailboxPath mailboxPath) {
            this.mailboxMessage = mailboxMessage;
            this.attachments = attachments;
            this.mailboxPath = mailboxPath;
        }
    }

    private Function<List<AttachmentId>, List<Attachment>> retrieveAttachments(AttachmentMapper attachmentMapper) {
        return (attachmentsIds) -> {
            return attachmentsIds.stream()
                    .map(Throwing.function(id -> attachmentMapper.getAttachment(id)))
                    .collect(Collectors.toImmutableList());
        };
    }
}
