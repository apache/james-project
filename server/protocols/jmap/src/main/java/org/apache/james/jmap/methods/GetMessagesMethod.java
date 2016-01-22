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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetMessagesRequest;
import org.apache.james.jmap.model.GetMessagesResponse;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageHeaderProperty;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessageProperty;
import org.apache.james.jmap.model.Property;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.util.streams.Collectors;
import org.javatuples.Pair;

import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class GetMessagesMethod<Id extends MailboxId> implements Method {

    public static final Set<MessageProperty> MANDATORY_PROPERTIES = ImmutableSet.of(MessageProperty.id, MessageProperty.threadId, MessageProperty.mailboxIds);
    public static final String HEADERS_FILTER = "headersFilter";
    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMessages");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messages");
    private final MessageMapperFactory<Id> messageMapperFactory;
    private final MailboxMapperFactory<Id> mailboxMapperFactory;

    @Inject
    @VisibleForTesting GetMessagesMethod(
            MessageMapperFactory<Id> messageMapperFactory, 
            MailboxMapperFactory<Id> mailboxMapperFactory) {
        this.messageMapperFactory = messageMapperFactory;
        this.mailboxMapperFactory = mailboxMapperFactory;
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
        Optional<ImmutableSet<MessageProperty>> requestedProperties = getMessagesRequest.getProperties();
        Optional<ImmutableSet<MessageHeaderProperty>> headerProperties = getMessagesRequest.getHeaderProperties();
        return Stream.of(JmapResponse.builder().clientId(clientId)
                            .response(getMessagesResponse(mailboxSession, getMessagesRequest, requestedProperties))
                            .responseName(RESPONSE_NAME)
                            .properties(handleSpecificProperties(requestedProperties, headerProperties))
                            .filterProvider(Optional.of(buildFilteringHeadersFilterProvider(headerProperties)))
                            .build());
    }

    private Optional<Set<? extends Property>> handleSpecificProperties(Optional<ImmutableSet<MessageProperty>> requestedProperties, Optional<ImmutableSet<MessageHeaderProperty>> headerProperties) {
        Set<MessageProperty> toAdd = Sets.newHashSet();
        Set<MessageProperty> toRemove = Sets.newHashSet();
        toAdd.addAll(ensureContainsMandatoryFields(requestedProperties));
        handleBody(requestedProperties, toAdd, toRemove);
        handleHeadersProperties(headerProperties, toAdd, toRemove);
        ImmutableSet<MessageProperty> resultProperties = Sets.union(
                    Sets.difference(requestedProperties.isPresent() ? requestedProperties.get() : ImmutableSet.of(), toRemove)
                    , toAdd)
                .immutableCopy();
        if (resultProperties.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(resultProperties);
    }

    private void handleHeadersProperties(Optional<ImmutableSet<MessageHeaderProperty>> headerProperties, Set<MessageProperty> toAdd, Set<MessageProperty> toRemove) {
        if (headerProperties.isPresent() && !headerProperties.get().isEmpty()) {
            toAdd.add(MessageProperty.headers);
        }
    }

    private Set<MessageProperty> ensureContainsMandatoryFields(Optional<ImmutableSet<MessageProperty>> requestedProperties) {
        return MANDATORY_PROPERTIES.stream()
            .filter(mandatoryProperty -> propertyToAdd(mandatoryProperty, requestedProperties))
            .collect(Collectors.toImmutableSet());
    }

    private boolean propertyToAdd(MessageProperty property, Optional<ImmutableSet<MessageProperty>> requestedProperties) {
        return requestedProperties.isPresent() && 
                !requestedProperties
                    .filter(properties -> properties.contains(property))
                    .flatMap(Optional::of)
                    .isPresent();
    }

    private void handleBody(Optional<ImmutableSet<MessageProperty>> requestedProperties, Set<MessageProperty> toAdd, Set<MessageProperty> toRemove) {
        if (requestedProperties.isPresent() && requestedProperties.get().contains(MessageProperty.body)) {
            toAdd.add(MessageProperty.textBody);
            toRemove.add(MessageProperty.body);
        }
    }

    private SimpleFilterProvider buildFilteringHeadersFilterProvider(Optional<ImmutableSet<MessageHeaderProperty>> headerProperties) {
        return new SimpleFilterProvider()
                .addFilter(HEADERS_FILTER, buildPropertyFilter(headerProperties))
                .addFilter(JmapResponseWriterImpl.PROPERTIES_FILTER, SimpleBeanPropertyFilter.serializeAll());
    }
    
    private SimpleBeanPropertyFilter buildPropertyFilter(Optional<ImmutableSet<MessageHeaderProperty>> headerProperties) {
        if (!headerProperties.isPresent()) {
            return SimpleBeanPropertyFilter.serializeAll();
        } else {
            return new IncludeMessagePropertyPropertyFilter(headerProperties.get());
        }
    }
    
    private static class IncludeMessagePropertyPropertyFilter extends SimpleBeanPropertyFilter {
        private final Set<MessageHeaderProperty> propertiesToInclude;

        public IncludeMessagePropertyPropertyFilter(Set<MessageHeaderProperty> propertiesToInclude) {
            this.propertiesToInclude = propertiesToInclude;
        }
        
        @Override
        protected boolean include(PropertyWriter writer) {
            String currentProperty = writer.getName();
            return propertiesToInclude.contains(MessageHeaderProperty.fromField(currentProperty));
        }
    }
    
    private GetMessagesResponse getMessagesResponse(MailboxSession mailboxSession, GetMessagesRequest getMessagesRequest, Optional<ImmutableSet<MessageProperty>> requestedProperties) {
        getMessagesRequest.getAccountId().ifPresent(GetMessagesMethod::notImplemented);
        
        Function<MessageId, Stream<Pair<MailboxMessage<Id>, MailboxPath>>> loadMessages = loadMessage(mailboxSession);
        Function<Pair<MailboxMessage<Id>, MailboxPath>, Message> convertToJmapMessage = toJmapMessage(mailboxSession);
        
        List<Message> result = getMessagesRequest.getIds().stream()
            .flatMap(loadMessages)
            .map(convertToJmapMessage)
            .collect(Collectors.toImmutableList());

        return GetMessagesResponse.builder().messages(result).expectedMessageIds(getMessagesRequest.getIds()).build();
    }

    private static void notImplemented(String input) {
        throw new NotImplementedException();
    }

    
    private Function<Pair<MailboxMessage<Id>, MailboxPath>, Message> toJmapMessage(MailboxSession mailboxSession) {
        return (value) -> {
            MailboxMessage<Id> messageResult = value.getValue0();
            MailboxPath mailboxPath = value.getValue1();
            return Message.fromMailboxMessage(messageResult, uid -> new MessageId(mailboxSession.getUser(), mailboxPath , uid));
        };
    }

    private Function<MessageId, Stream<
                                    Pair<MailboxMessage<Id>,
                                         MailboxPath>>> 
                loadMessage(MailboxSession mailboxSession) {

        return Throwing
                .function((MessageId messageId) -> {
                     MailboxPath mailboxPath = messageId.getMailboxPath(mailboxSession);
                     MessageMapper<Id> messageMapper = messageMapperFactory.getMessageMapper(mailboxSession);
                     Mailbox<Id> mailbox = mailboxMapperFactory.getMailboxMapper(mailboxSession).findMailboxByPath(mailboxPath);
                     return Pair.with(
                             messageMapper.findInMailbox(mailbox, MessageRange.one(messageId.getUid()), MessageMapper.FetchType.Full, 1),
                             mailboxPath
                             );
         })
                .andThen(this::iteratorToStream);
    }
    
    private Stream<Pair<MailboxMessage<Id>, MailboxPath>> iteratorToStream(Pair<Iterator<MailboxMessage<Id>>, MailboxPath> value) {
        Iterable<MailboxMessage<Id>> iterable = () -> value.getValue0();
        Stream<MailboxMessage<Id>> targetStream = StreamSupport.stream(iterable.spliterator(), false);
        
        MailboxPath mailboxPath = value.getValue1();
        return targetStream.map(x -> Pair.with(x, mailboxPath));
    }
}
