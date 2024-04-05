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

import static jakarta.mail.Flags.Flag.DELETED;
import static org.apache.james.util.ReactorUtils.context;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.jmap.JMAPConfiguration;
import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.jmap.draft.model.Filter;
import org.apache.james.jmap.draft.model.FilterCondition;
import org.apache.james.jmap.draft.model.GetMessageListRequest;
import org.apache.james.jmap.draft.model.GetMessageListResponse;
import org.apache.james.jmap.draft.model.GetMessagesRequest;
import org.apache.james.jmap.draft.utils.FilterToCriteria;
import org.apache.james.jmap.draft.utils.SortConverter;
import org.apache.james.jmap.methods.ErrorResponse;
import org.apache.james.jmap.methods.JmapRequest;
import org.apache.james.jmap.methods.JmapResponse;
import org.apache.james.jmap.methods.Method;
import org.apache.james.jmap.model.MethodCallId;
import org.apache.james.jmap.model.Number;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxId.Factory;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.streams.Limit;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

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
    private final EmailQueryView emailQueryView;
    private final JMAPConfiguration configuration;
    private final MetricFactory metricFactory;

    @Inject
    private GetMessageListMethod(MailboxManager mailboxManager,
                                 @Named(MAXIMUM_LIMIT) long maximumLimit,
                                 GetMessagesMethod getMessagesMethod,
                                 Factory mailboxIdFactory,
                                 EmailQueryView emailQueryView,
                                 JMAPConfiguration configuration,
                                 MetricFactory metricFactory) {

        this.mailboxManager = mailboxManager;
        this.maximumLimit = maximumLimit;
        this.getMessagesMethod = getMessagesMethod;
        this.mailboxIdFactory = mailboxIdFactory;
        this.emailQueryView = emailQueryView;
        this.configuration = configuration;
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

        return Flux.from(metricFactory.decoratePublisherWithTimerMetric(JMAP_PREFIX + METHOD_NAME.getName(),
            process(methodCallId, mailboxSession, messageListRequest)
                .contextWrite(context("GET_MESSAGE_LIST", mdc(messageListRequest)))));
    }

    private MDCBuilder mdc(GetMessageListRequest messageListRequest) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "GET_MESSAGE_LIST")
            .addToContextIfPresent("accountId", messageListRequest.getAccountId())
            .addToContextIfPresent("limit", messageListRequest.getLimit()
                .map(Number::asLong).map(l -> Long.toString(l)))
            .addToContextIfPresent("anchor", messageListRequest.getAnchor())
            .addToContextIfPresent("offset", messageListRequest.getAnchorOffset()
                .map(Number::asLong).map(l -> Long.toString(l)))
            .addToContext("properties", Joiner.on(", ")
                .join(messageListRequest.getFetchMessageProperties()))
            .addToContextIfPresent("position", messageListRequest.getPosition()
                .map(Number::asLong).map(l -> Long.toString(l)))
            .addToContextIfPresent("filters", messageListRequest.getFilter().map(Objects::toString))
            .addToContext("sorts", Joiner.on(", ")
                .join(messageListRequest.getSort()))
            .addToContextIfPresent("isFetchMessage", messageListRequest.isFetchMessages().map(b -> Boolean.toString(b)))
            .addToContextIfPresent("isCollapseThread", messageListRequest.isCollapseThreads().map(b -> Boolean.toString(b)));
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
        long position = messageListRequest.getPosition().map(Number::asLong).orElse(DEFAULT_POSITION);
        long limit = position + messageListRequest.getLimit().map(Number::asLong).orElse(maximumLimit);

        if (isListingContentInMailboxQuery(messageListRequest)) {
            Filter filter = messageListRequest.getFilter().get();
            FilterCondition condition = (FilterCondition) filter;
            String mailboxIdAsString = condition.getInMailboxes().get().iterator().next();
            MailboxId mailboxId = mailboxIdFactory.fromString(mailboxIdAsString);
            Limit aLimit = Limit.from(Math.toIntExact(limit));

            return Mono.from(mailboxManager.getMailboxReactive(mailboxId, mailboxSession))
                .then(emailQueryView.listMailboxContentSortedBySentAt(mailboxId, aLimit)
                    .skip(position)
                    .take(limit)
                    .reduce(GetMessageListResponse.builder(), GetMessageListResponse.Builder::messageId)
                    .map(GetMessageListResponse.Builder::build))
                .onErrorResume(MailboxNotFoundException.class, e -> Mono.just(GetMessageListResponse.builder().build()));
        }
        if (isListingContentInMailboxAfterQuery(messageListRequest)) {
            Filter filter = messageListRequest.getFilter().get();
            FilterCondition condition = (FilterCondition) filter;
            String mailboxIdAsString = condition.getInMailboxes().get().iterator().next();
            MailboxId mailboxId = mailboxIdFactory.fromString(mailboxIdAsString);
            ZonedDateTime after = condition.getAfter().get();
            Limit aLimit = Limit.from(Math.toIntExact(limit));

            return Mono.from(mailboxManager.getMailboxReactive(mailboxId, mailboxSession))
                .then(emailQueryView.listMailboxContentSinceAfterSortedBySentAt(mailboxId, after, aLimit)
                    .skip(position)
                    .take(limit)
                    .reduce(GetMessageListResponse.builder(), GetMessageListResponse.Builder::messageId)
                    .map(GetMessageListResponse.Builder::build))
                .onErrorResume(MailboxNotFoundException.class, e -> Mono.just(GetMessageListResponse.builder().build()));
        }
        return querySearchBackend(messageListRequest, position, limit, mailboxSession);
    }

    private boolean isListingContentInMailboxQuery(GetMessageListRequest messageListRequest) {
        return configuration.isEmailQueryViewEnabled()
            && messageListRequest.getFilter().map(Filter::inMailboxFilterOnly).orElse(false)
            && messageListRequest.getSort().equals(ImmutableList.of("date desc"));
    }

    private boolean isListingContentInMailboxAfterQuery(GetMessageListRequest messageListRequest) {
        return configuration.isEmailQueryViewEnabled()
            && messageListRequest.getFilter().map(Filter::inMailboxAndAfterFiltersOnly).orElse(false)
            && messageListRequest.getSort().equals(ImmutableList.of("date desc"));
    }

    private Mono<GetMessageListResponse> querySearchBackend(GetMessageListRequest messageListRequest, long position, long limit, MailboxSession mailboxSession) {
        Mono<MultimailboxesSearchQuery> searchQuery = Mono.fromCallable(() -> convertToSearchQuery(messageListRequest))
            .subscribeOn(Schedulers.parallel());

        return searchQuery
            .flatMapMany(Throwing.function(query ->
                mailboxManager.search(query.addCriterion(SearchQuery.flagIsUnSet(DELETED)), mailboxSession, limit)))
            .skip(position)
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
            .map(filter -> new FilterToCriteria().convert(filter).collect(ImmutableList.toImmutableList()))
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
            .flatMap(condition -> mailboxListExtractor.apply(condition).stream())
            .flatMap(List::stream)
            .map(mailboxIdFactory::fromString)
            .collect(ImmutableSet.toImmutableSet());
    }
    
    private Stream<FilterCondition> filterToFilterCondition(Optional<Filter> maybeCondition) {
        return maybeCondition.stream()
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
