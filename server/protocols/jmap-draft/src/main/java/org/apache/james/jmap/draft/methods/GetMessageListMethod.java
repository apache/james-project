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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.jmap.draft.model.Filter;
import org.apache.james.jmap.draft.model.FilterCondition;
import org.apache.james.jmap.draft.model.GetMessageListRequest;
import org.apache.james.jmap.draft.model.GetMessageListResponse;
import org.apache.james.jmap.draft.model.GetMessagesRequest;
import org.apache.james.jmap.draft.model.MethodCallId;
import org.apache.james.jmap.draft.model.Number;
import org.apache.james.jmap.draft.utils.FilterToCriteria;
import org.apache.james.jmap.draft.utils.SortConverter;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxId.Factory;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    public Flux<JmapResponse> process(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkArgument(request instanceof GetMessageListRequest);

        GetMessageListRequest messageListRequest = (GetMessageListRequest) request;

        return metricFactory.runPublishingTimerMetricLogP99(JMAP_PREFIX + METHOD_NAME.getName(),
            () -> process(methodCallId, mailboxSession, messageListRequest)
                .subscriberContext(context("GET_MESSAGE_LIST", mdc(messageListRequest))))
            .subscribeOn(Schedulers.elastic());
    }

    private MDCBuilder mdc(GetMessageListRequest messageListRequest) {
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
            .addContext("isCollapseThread", messageListRequest.isCollapseThreads());
    }

    private Flux<JmapResponse> process(MethodCallId methodCallId, MailboxSession mailboxSession, GetMessageListRequest messageListRequest) {
        return getMessageListResponse(messageListRequest, mailboxSession)
                .flatMapMany(messageListResponse -> Flux.concat(
                    Mono.just(JmapResponse.builder().methodCallId(methodCallId)
                        .response(messageListResponse)
                        .responseName(RESPONSE_NAME)
                        .build()),
                    processGetMessages(messageListRequest, messageListResponse, methodCallId, mailboxSession)))
                .onErrorResume(NotImplementedException.class, e -> Mono.just(JmapResponse.builder()
                    .methodCallId(methodCallId)
                    .responseName(RESPONSE_NAME)
                    .error(ErrorResponse.builder()
                        .type("invalidArguments")
                        .description(e.getMessage())
                        .build())
                    .build()))
                .onErrorResume(Filter.TooDeepFilterHierarchyException.class, e -> Mono.just(JmapResponse.builder()
                    .methodCallId(methodCallId)
                    .responseName(RESPONSE_NAME)
                    .error(ErrorResponse.builder()
                        .type("invalidArguments")
                        .description(e.getMessage())
                        .build())
                    .build()));
    }

    private Mono<GetMessageListResponse> getMessageListResponse(GetMessageListRequest messageListRequest, MailboxSession mailboxSession) {
        Mono<MultimailboxesSearchQuery> searchQuery = Mono.fromCallable(() -> convertToSearchQuery(messageListRequest))
            .subscribeOn(Schedulers.parallel());
        Long positionValue = messageListRequest.getPosition().map(Number::asLong).orElse(DEFAULT_POSITION);
        long limit = positionValue + messageListRequest.getLimit().map(Number::asLong).orElse(maximumLimit);

        return searchQuery
            .flatMapMany(Throwing.function(query -> mailboxManager.search(query, mailboxSession, limit)))
            .skip(positionValue)
            .reduce(GetMessageListResponse.builder(), GetMessageListResponse.Builder::messageId)
            .map(GetMessageListResponse.Builder::build);
    }

    private MultimailboxesSearchQuery convertToSearchQuery(GetMessageListRequest messageListRequest) {
        if (messageListRequest.getFilter().map(this::containsNestedMailboxFilters).orElse(false)) {
            throw new NotImplementedException("'inMailboxes' and 'notInMailboxes' wrapped within Filter Operators are not " +
                "implemented. Review your search request.");
        }

        SearchQuery.Builder searchQueryBuilder = SearchQuery.builder();

            messageListRequest.getFilter()
                .map(filter -> new FilterToCriteria().convert(filter).collect(Guavate.toImmutableList()))
                .ifPresent(searchQueryBuilder::andCriteria);
        Set<MailboxId> inMailboxes = buildFilterMailboxesSet(messageListRequest.getFilter(), FilterCondition::getInMailboxes);
        Set<MailboxId> notInMailboxes = buildFilterMailboxesSet(messageListRequest.getFilter(), FilterCondition::getNotInMailboxes);
        List<SearchQuery.Sort> sorts = SortConverter.convertToSorts(messageListRequest.getSort());
        if (!sorts.isEmpty()) {
            searchQueryBuilder.sorts(sorts);
        }
        return MultimailboxesSearchQuery
                .from(searchQueryBuilder.build())
                .inMailboxes(inMailboxes)
                .notInMailboxes(notInMailboxes)
                .build();
    }

    private boolean containsNestedMailboxFilters(Filter filter) {
        if (filter instanceof FilterCondition) {
            // The condition is not nested
            return false;
        }
        return containsMailboxFilters(filter);
    }

    private boolean containsMailboxFilters(Filter filter) {
        return filter.breadthFirstVisit()
            .stream()
            .anyMatch(this::hasMailboxClause);
    }

    private boolean hasMailboxClause(FilterCondition condition) {
        return condition.getInMailboxes().isPresent() || condition.getInMailboxes().isPresent();
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
    
    private Flux<JmapResponse> processGetMessages(GetMessageListRequest messageListRequest, GetMessageListResponse messageListResponse, MethodCallId methodCallId, MailboxSession mailboxSession) {
        if (shouldChainToGetMessages(messageListRequest)) {
            GetMessagesRequest getMessagesRequest = GetMessagesRequest.builder()
                    .ids(messageListResponse.getMessageIds())
                    .properties(messageListRequest.getFetchMessageProperties())
                    .build();
            return getMessagesMethod.process(getMessagesRequest, methodCallId, mailboxSession);
        }
        return Flux.empty();
    }

    private boolean shouldChainToGetMessages(GetMessageListRequest messageListRequest) {
        return messageListRequest.isFetchMessages().orElse(false) 
                && !messageListRequest.isFetchThreads().orElse(false);
    }

}
