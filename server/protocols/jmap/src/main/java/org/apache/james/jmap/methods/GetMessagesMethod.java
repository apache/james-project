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
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessageProperties;
import org.apache.james.jmap.model.MessageProperties.HeaderProperty;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.javatuples.Triplet;

import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class GetMessagesMethod implements Method {

    public static final String HEADERS_FILTER = "headersFilter";
    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMessages");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messages");
    private final MailboxManager mailboxManager;
    private final MessageFactory messageFactory;

    @Inject
    @VisibleForTesting GetMessagesMethod(
            MailboxManager mailboxManager,
            MessageFactory messageFactory) {
        this.mailboxManager = mailboxManager;
        this.messageFactory = messageFactory;
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
        
        Function<MessageId, Stream<CompletedMessageResult>> loadMessages = loadMessage(mailboxSession);
        Function<CompletedMessageResult, Message> convertToJmapMessage = toJmapMessage(mailboxSession);
        
        List<Message> result = getMessagesRequest.getIds().stream()
            .flatMap(loadMessages)
            .map(convertToJmapMessage)
            .collect(Guavate.toImmutableList());

        return GetMessagesResponse.builder().messages(result).expectedMessageIds(getMessagesRequest.getIds()).build();
    }

    private static void notImplemented(String input) {
        throw new NotImplementedException();
    }

    
    private Function<CompletedMessageResult, Message> toJmapMessage(MailboxSession mailboxSession) {
        ThrowingFunction<CompletedMessageResult, Message> function = (completedMessageResult) -> messageFactory.fromMessageResult(
                completedMessageResult.messageResult,
                completedMessageResult.attachments,
                completedMessageResult.mailboxId,
                uid -> new MessageId(mailboxSession.getUser(), completedMessageResult.mailboxPath , uid));
        return Throwing.function(function).sneakyThrow();
    }

    private Function<MessageId, Stream<CompletedMessageResult>> 
                loadMessage(MailboxSession mailboxSession) {

        return Throwing
                .function((MessageId messageId) -> {
                     MailboxPath mailboxPath = messageId.getMailboxPath();
                     MessageManager messageManager = mailboxManager.getMailbox(messageId.getMailboxPath(), mailboxSession);
                     return Triplet.with(
                             messageManager.getMessages(messageId.getUidAsRange(), FetchGroupImpl.FULL_CONTENT, mailboxSession),
                             mailboxPath,
                             messageManager.getId()
                             );
                })
                .andThen(Throwing.function((triplet) -> retrieveCompleteMessageResults(triplet, mailboxSession)));
    }
    
    private Stream<CompletedMessageResult> retrieveCompleteMessageResults(Triplet<MessageResultIterator, MailboxPath, MailboxId> value, MailboxSession mailboxSession) throws MailboxException {
        Iterable<MessageResult> iterable = () -> value.getValue0();
        Stream<MessageResult> targetStream = StreamSupport.stream(iterable.spliterator(), false);

        MailboxPath mailboxPath = value.getValue1();
        MailboxId mailboxId = value.getValue2();
        return targetStream
                .map(Throwing.function(this::initializeBuilder).sneakyThrow())
                .map(builder -> builder.mailboxId(mailboxId))
                .map(builder -> builder.mailboxPath(mailboxPath))
                .map(builder -> builder.build()); 
    }
    
    private CompletedMessageResult.Builder initializeBuilder(MessageResult message) throws MailboxException {
        return CompletedMessageResult.builder()
                .messageResult(message)
                .attachments(message.getAttachments());
    }

    private static class CompletedMessageResult {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private MessageResult messageResult;
            private List<MessageAttachment> attachments;
            private MailboxPath mailboxPath;
            private MailboxId mailboxId;

            private Builder() {
            }

            public Builder messageResult(MessageResult messageResult) {
                Preconditions.checkArgument(messageResult != null);
                this.messageResult = messageResult;
                return this;
            }

            public Builder attachments(List<MessageAttachment> attachments) {
                Preconditions.checkArgument(attachments != null);
                this.attachments = attachments;
                return this;
            }

            public Builder mailboxPath(MailboxPath mailboxPath) {
                Preconditions.checkArgument(mailboxPath != null);
                this.mailboxPath = mailboxPath;
                return this;
            }

            public Builder mailboxId(MailboxId mailboxId) {
                Preconditions.checkArgument(mailboxId != null);
                this.mailboxId = mailboxId;
                return this;
            }

            public CompletedMessageResult build() {
                Preconditions.checkState(messageResult != null);
                Preconditions.checkState(attachments != null);
                Preconditions.checkState(mailboxPath != null);
                Preconditions.checkState(mailboxId != null);
                return new CompletedMessageResult(messageResult, attachments, mailboxPath, mailboxId);
            }
        }

        private final MessageResult messageResult;
        private final List<MessageAttachment> attachments;
        private final MailboxPath mailboxPath;
        private final MailboxId mailboxId;

        public CompletedMessageResult(MessageResult messageResult, List<MessageAttachment> attachments, MailboxPath mailboxPath, MailboxId mailboxId) {
            this.messageResult = messageResult;
            this.attachments = attachments;
            this.mailboxPath = mailboxPath;
            this.mailboxId = mailboxId;
        }
    }
}
