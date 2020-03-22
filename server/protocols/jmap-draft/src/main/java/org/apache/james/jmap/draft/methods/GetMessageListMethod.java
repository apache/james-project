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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.jmap.draft.model.Filter;
import org.apache.james.jmap.draft.model.FilterCondition;
import org.apache.james.jmap.draft.model.GetMessageListRequest;
import org.apache.james.jmap.draft.model.GetMessageListResponse;
import org.apache.james.jmap.draft.model.GetMessagesRequest;
import org.apache.james.jmap.draft.model.MethodCallId;
import org.apache.james.jmap.draft.model.Number;
import org.apache.james.jmap.draft.utils.FilterToSearchQuery;
import org.apache.james.jmap.draft.utils.SortConverter;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxId.Factory;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class GetMessageListMethod implements Method {

    private static final long DEFAULT_POSITION = 0;
    public static final String MAXIMUM_LIMIT = "maximumLimit";
    public static final long DEFAULT_MAXIMUM_LIMIT = 256;

    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMessageList");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messageList");

    private final MailboxManager mailboxManager;
    private final long maximumLimit;
    private final GetMessagesMethod getMessagesMethod;
    private final Factory mailboxIdFactory;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting public GetMessageListMethod(MailboxManager mailboxManager,
            @Named(MAXIMUM_LIMIT) long maximumLimit, GetMessagesMethod getMessagesMethod, MailboxId.Factory mailboxIdFactory,
            MetricFactory metricFactory) {

        this.mailboxManager = mailboxManager;
        this.maximumLimit = maximumLimit;
        this.getMessagesMethod = getMessagesMethod;
        this.mailboxIdFactory = mailboxIdFactory;
        this.metricFactory = metricFactory;
    }

    @Override
    public Method.Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return GetMessageListRequest.class;
    }

    @Override
    public Stream<JmapResponse> processToStream(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkArgument(request instanceof GetMessageListRequest);

        GetMessageListRequest messageListRequest = (GetMessageListRequest) request;

        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "GET_MESSAGE_LIST")
            .addContext("accountId", messageListRequest.getAccountId())
            .addContext("limit", messageListRequest.getLimit())
            .addContext("anchor", messageListRequest.getAnchor())
            .addContext("offset", messageListRequest.getAnchorOffset())
            .addContext("properties", messageListRequest.getFetchMessageProperties())
            .addContext("position", messageListRequest.getPosition())
            .addContext("filters", messageListRequest.getFilter())
            .addContext("sorts", messageListRequest.getSort())
            .addContext("isFetchMessage", messageListRequest.isFetchMessages())
            .addContext("isCollapseThread", messageListRequest.isCollapseThreads())
            .wrapArround(
                () -> metricFactory.runPublishingTimerMetricLogP99(JMAP_PREFIX + METHOD_NAME.getName(),
                    () -> process(methodCallId, mailboxSession, messageListRequest)))
            .get();
    }

    private Stream<JmapResponse> process(MethodCallId methodCallId, MailboxSession mailboxSession, GetMessageListRequest messageListRequest) {
        GetMessageListResponse messageListResponse = getMessageListResponse(messageListRequest, mailboxSession);
        Stream<JmapResponse> jmapResponse = Stream.of(JmapResponse.builder().methodCallId(methodCallId)
            .response(messageListResponse)
            .responseName(RESPONSE_NAME)
            .build());
        return Stream.concat(jmapResponse,
            processGetMessages(messageListRequest, messageListResponse, methodCallId, mailboxSession));
    }

    private GetMessageListResponse getMessageListResponse(GetMessageListRequest messageListRequest, MailboxSession mailboxSession) {
        GetMessageListResponse.Builder builder = GetMessageListResponse.builder();
        try {
            MultimailboxesSearchQuery searchQuery = convertToSearchQuery(messageListRequest);
            Long postionValue = messageListRequest.getPosition().map(Number::asLong).orElse(DEFAULT_POSITION);
            mailboxManager.search(searchQuery,
                mailboxSession,
                postionValue + messageListRequest.getLimit().map(Number::asLong).orElse(maximumLimit))
                .stream()
                .skip(postionValue)
                .forEach(builder::messageId);
            return builder.build();
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    private MultimailboxesSearchQuery convertToSearchQuery(GetMessageListRequest messageListRequest) {
        SearchQuery searchQuery = messageListRequest.getFilter()
                .map(filter -> new FilterToSearchQuery().convert(filter))
                .orElse(new SearchQuery());
        Set<MailboxId> inMailboxes = buildFilterMailboxesSet(messageListRequest.getFilter(), FilterCondition::getInMailboxes);
        Set<MailboxId> notInMailboxes = buildFilterMailboxesSet(messageListRequest.getFilter(), FilterCondition::getNotInMailboxes);
        List<SearchQuery.Sort> sorts = SortConverter.convertToSorts(messageListRequest.getSort());
        if (!sorts.isEmpty()) {
            searchQuery.setSorts(sorts);
        }
        return MultimailboxesSearchQuery
                .from(searchQuery)
                .inMailboxes(inMailboxes)
                .notInMailboxes(notInMailboxes)
                .build();
    }

    private Set<MailboxId> buildFilterMailboxesSet(Optional<Filter> maybeFilter, Function<FilterCondition, Optional<List<String>>> mailboxListExtractor) {
        return filterToFilterCondition(maybeFilter)
            .flatMap(condition -> Guavate.stream(mailboxListExtractor.apply(condition)))
            .flatMap(List::stream)
            .map(mailboxIdFactory::fromString)
            .collect(Guavate.toImmutableSet());
    }
    
    private Stream<FilterCondition> filterToFilterCondition(Optional<Filter> maybeCondition) {
        return Guavate.stream(maybeCondition)
                .flatMap(c -> {
                    if (c instanceof FilterCondition) {
                        return Stream.of((FilterCondition)c);
                    }
                    return Stream.of();
                });
    }
    
    private Stream<JmapResponse> processGetMessages(GetMessageListRequest messageListRequest, GetMessageListResponse messageListResponse, MethodCallId methodCallId, MailboxSession mailboxSession) {
        if (shouldChainToGetMessages(messageListRequest)) {
            GetMessagesRequest getMessagesRequest = GetMessagesRequest.builder()
                    .ids(messageListResponse.getMessageIds())
                    .properties(messageListRequest.getFetchMessageProperties())
                    .build();
            return getMessagesMethod.processToStream(getMessagesRequest, methodCallId, mailboxSession);
        }
        return Stream.empty();
    }

    private boolean shouldChainToGetMessages(GetMessageListRequest messageListRequest) {
        return messageListRequest.isFetchMessages().orElse(false) 
                && !messageListRequest.isFetchThreads().orElse(false);
    }

}
