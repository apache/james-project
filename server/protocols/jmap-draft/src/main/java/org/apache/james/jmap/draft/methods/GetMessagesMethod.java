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

import static org.apache.james.util.ReactorUtils.context;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.jmap.draft.exceptions.JmapFieldNotSupportedException;
import org.apache.james.jmap.draft.json.FieldNamePropertyFilter;
import org.apache.james.jmap.draft.model.GetMessagesRequest;
import org.apache.james.jmap.draft.model.GetMessagesResponse;
import org.apache.james.jmap.methods.JmapRequest;
import org.apache.james.jmap.methods.JmapResponse;
import org.apache.james.jmap.methods.Method;
import org.apache.james.jmap.model.MessageProperties;
import org.apache.james.jmap.model.MessageProperties.HeaderProperty;
import org.apache.james.jmap.model.MethodCallId;
import org.apache.james.jmap.model.Property;
import org.apache.james.jmap.model.message.view.MessageView;
import org.apache.james.jmap.model.message.view.MessageViewFactory;
import org.apache.james.jmap.model.message.view.MetaMessageViewFactory;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class GetMessagesMethod implements Method {

    public static final String HEADERS_FILTER = "headersFilter";
    private static final String ISSUER = "GetMessagesMethod";
    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMessages");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messages");
    private final MetaMessageViewFactory messageViewFactory;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting GetMessagesMethod(MetaMessageViewFactory messageViewFactory, MetricFactory metricFactory) {
        this.messageViewFactory = messageViewFactory;
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
    public Flux<JmapResponse> process(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(mailboxSession);
        Preconditions.checkArgument(request instanceof GetMessagesRequest);

        GetMessagesRequest getMessagesRequest = (GetMessagesRequest) request;
        MessageProperties outputProperties = getMessagesRequest.getProperties().toOutputProperties();
        Optional<Pair<? extends Set<? extends Property>, SimpleFilterProvider>> integerSimpleFilterProviderPair = buildOptionalHeadersFilteringFilterProvider(outputProperties);

        return Flux.from(metricFactory.decoratePublisherWithTimerMetric(JMAP_PREFIX + METHOD_NAME.getName(),
            Flux.from(getMessagesResponse(mailboxSession, getMessagesRequest)
                .map(response -> JmapResponse.builder().methodCallId(methodCallId)
                    .response(response)
                    .responseName(RESPONSE_NAME)
                    .properties(outputProperties.getOptionalMessageProperties())
                    .filterProvider(integerSimpleFilterProviderPair)
                    .build()))
            .contextWrite(context("GET_MESSAGES", mdc(getMessagesRequest)))));
    }

    private MDCBuilder mdc(GetMessagesRequest getMessagesRequest) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "GET_MESSAGES")
            .addToContextIfPresent("accountId", getMessagesRequest.getAccountId())
            .addToContext("ids", getMessagesRequest.getIds()
                .stream()
                .map(MessageId::serialize)
                .collect(Collectors.joining(", ")))
            .addToContext("properties", getMessagesRequest.getProperties().asFieldList()
                .collect(Collectors.joining(", ")));
    }

    private Optional<Pair<? extends Set<? extends Property>, SimpleFilterProvider>> buildOptionalHeadersFilteringFilterProvider(MessageProperties properties) {
        return properties.getOptionalHeadersProperties()
            .map(headerProperties -> Pair.of(headerProperties, new SimpleFilterProvider()
                .addFilter(HEADERS_FILTER, buildHeadersPropertyFilter(headerProperties))));
    }
    
    private PropertyFilter buildHeadersPropertyFilter(ImmutableSet<HeaderProperty> headerProperties) {
        return new FieldNamePropertyFilter((fieldName) -> headerProperties.contains(HeaderProperty.fromFieldName(fieldName)));
    }

    private Mono<GetMessagesResponse> getMessagesResponse(MailboxSession mailboxSession, GetMessagesRequest getMessagesRequest) {
        getMessagesRequest.getAccountId().ifPresent(input -> notImplemented("accountId"));

        MessageProperties.ReadProfile readProfile = getMessagesRequest.getProperties().computeReadLevel();
        MessageViewFactory<? extends MessageView> factory = messageViewFactory.getFactory(readProfile);
        Mono<? extends Set<? extends MessageView>> messageViewsMono = factory.fromMessageIds(getMessagesRequest.getIds(), mailboxSession)
            .collect(ImmutableSet.toImmutableSet());

        return messageViewsMono.map(messageViews ->
            GetMessagesResponse.builder()
                .messages(messageViews)
                .expectedMessageIds(getMessagesRequest.getIds())
                .build());
    }

    private static void notImplemented(String field) {
        throw new JmapFieldNotSupportedException(ISSUER, field);
    }
}
