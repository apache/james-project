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

package org.apache.james.jmap.draft.methods;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.jmap.draft.JmapFieldNotSupportedException;
import org.apache.james.jmap.draft.json.FieldNamePropertyFilter;
import org.apache.james.jmap.draft.model.GetMessagesRequest;
import org.apache.james.jmap.draft.model.GetMessagesResponse;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.model.Message;
import org.apache.james.jmap.draft.model.MessageFactory;
import org.apache.james.jmap.draft.model.MessageFactory.MetaDataWithContent;
import org.apache.james.jmap.draft.model.MessageProperties;
import org.apache.james.jmap.draft.model.MessageProperties.HeaderProperty;
import org.apache.james.jmap.draft.model.MethodCallId;
import org.apache.james.jmap.draft.utils.KeywordsCombiner;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class GetMessagesMethod implements Method {

    public static final String HEADERS_FILTER = "headersFilter";
    private static final String ISSUER = "GetMessagesMethod";
    private static final Logger LOGGER = LoggerFactory.getLogger(GetMessagesMethod.class);
    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMessages");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messages");
    private static final KeywordsCombiner ACCUMULATOR = new KeywordsCombiner();
    private final MessageFactory messageFactory;
    private final MessageIdManager messageIdManager;
    private final MetricFactory metricFactory;
    private final Keywords.KeywordsFactory keywordsFactory;

    @Inject
    @VisibleForTesting GetMessagesMethod(
            MessageFactory messageFactory,
            MessageIdManager messageIdManager,
            MetricFactory metricFactory) {
        this.messageFactory = messageFactory;
        this.messageIdManager = messageIdManager;
        this.metricFactory = metricFactory;
        this.keywordsFactory = Keywords.lenientFactory();
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
    public Stream<JmapResponse> process(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(mailboxSession);
        Preconditions.checkArgument(request instanceof GetMessagesRequest);

        GetMessagesRequest getMessagesRequest = (GetMessagesRequest) request;
        MessageProperties outputProperties = getMessagesRequest.getProperties().toOutputProperties();

        return metricFactory.runPublishingTimerMetric(JMAP_PREFIX + METHOD_NAME.getName(),
            MDCBuilder.create()
                .addContext(MDCBuilder.ACTION, "GET_MESSAGES")
                .addContext("accountId", getMessagesRequest.getAccountId())
                .addContext("ids", getMessagesRequest.getIds())
                .addContext("properties", getMessagesRequest.getProperties())
                .wrapArround(
                    () -> Stream.of(JmapResponse.builder().methodCallId(methodCallId)
                        .response(getMessagesResponse(mailboxSession, getMessagesRequest))
                        .responseName(RESPONSE_NAME)
                        .properties(outputProperties.getOptionalMessageProperties())
                        .filterProvider(buildOptionalHeadersFilteringFilterProvider(outputProperties))
                        .build())));
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
        getMessagesRequest.getAccountId().ifPresent((input) -> notImplemented("accountId"));

        try {
            MessageProperties.ReadLevel readLevel = getMessagesRequest.getProperties().computeReadLevel();
            return GetMessagesResponse.builder()
                .messages(
                    messageIdManager.getMessages(getMessagesRequest.getIds(), FetchGroupImpl.FULL_CONTENT, mailboxSession)
                        .stream()
                        .collect(Guavate.toImmutableListMultimap(MessageResult::getMessageId))
                        .asMap()
                        .values()
                        .stream()
                        .filter(collection -> !collection.isEmpty())
                        .flatMap(toMetaDataWithContent())
                        .flatMap(toMessage())
                        .collect(Guavate.toImmutableList()))
                .expectedMessageIds(getMessagesRequest.getIds())
                .build();
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    private Function<MetaDataWithContent, Stream<Message>> toMessage() {
        return metaDataWithContent -> {
            try {
                return Stream.of(messageFactory.fromMetaDataWithContent(metaDataWithContent));
            } catch (Exception e) {
                LOGGER.error("Can not convert metaData with content to Message for {}", metaDataWithContent.getMessageId(), e);
                return Stream.of();
            }
        };
    }

    private Function<Collection<MessageResult>, Stream<MetaDataWithContent>> toMetaDataWithContent() {
        return messageResults -> {
            MessageResult firstMessageResult = messageResults.iterator().next();
            List<MailboxId> mailboxIds = messageResults.stream()
                .map(MessageResult::getMailboxId)
                .distinct()
                .collect(Guavate.toImmutableList());
            try {
                Keywords keywords = messageResults.stream()
                    .map(MessageResult::getFlags)
                    .map(keywordsFactory::fromFlags)
                    .reduce(ACCUMULATOR)
                    .get();
                return Stream.of(
                    MetaDataWithContent.builderFromMessageResult(firstMessageResult)
                        .messageId(firstMessageResult.getMessageId())
                        .mailboxIds(mailboxIds)
                        .keywords(keywords)
                        .build());
            } catch (Exception e) {
                LOGGER.error("Can not convert MessageResults to MetaData with content for messageId {} in {}", firstMessageResult.getMessageId(), mailboxIds, e);
                return Stream.of();
            }
        };
    }

    private static void notImplemented(String field) {
        throw new JmapFieldNotSupportedException(ISSUER, field);
    }
}
