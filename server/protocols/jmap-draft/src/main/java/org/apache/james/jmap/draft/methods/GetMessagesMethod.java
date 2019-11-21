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
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.jmap.draft.JmapFieldNotSupportedException;
import org.apache.james.jmap.draft.json.FieldNamePropertyFilter;
import org.apache.james.jmap.draft.model.GetMessagesRequest;
import org.apache.james.jmap.draft.model.GetMessagesResponse;
import org.apache.james.jmap.draft.model.MessageProperties;
import org.apache.james.jmap.draft.model.MessageProperties.HeaderProperty;
import org.apache.james.jmap.draft.model.MethodCallId;
import org.apache.james.jmap.draft.model.message.view.MessageFullView;
import org.apache.james.jmap.draft.model.message.view.MessageFullViewFactory;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroupImpl;
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
    private final MessageFullViewFactory messageFullViewFactory;
    private final MessageIdManager messageIdManager;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting GetMessagesMethod(
            MessageFullViewFactory messageFullViewFactory,
            MessageIdManager messageIdManager,
            MetricFactory metricFactory) {
        this.messageFullViewFactory = messageFullViewFactory;
        this.messageIdManager = messageIdManager;
        this.metricFactory = metricFactory;
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
                        .flatMap(toMessageViews())
                        .collect(Guavate.toImmutableList()))
                .expectedMessageIds(getMessagesRequest.getIds())
                .build();
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    private Function<Collection<MessageResult>, Stream<MessageFullView>> toMessageViews() {
        return messageResults -> {
            try {
                return Stream.of(messageFullViewFactory.fromMessageResults(messageResults));
            } catch (Exception e) {
                LOGGER.error("Can not convert MessageResults to Message for {}", messageResults.iterator().next().getMessageId().serialize(), e);
                return Stream.of();
            }
        };
    }

    private static void notImplemented(String field) {
        throw new JmapFieldNotSupportedException(ISSUER, field);
    }
}
