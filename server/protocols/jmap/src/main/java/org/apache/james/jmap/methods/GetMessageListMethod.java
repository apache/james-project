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

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.Filter;
import org.apache.james.jmap.model.FilterCondition;
import org.apache.james.jmap.model.GetMessageListRequest;
import org.apache.james.jmap.model.GetMessageListResponse;
import org.apache.james.jmap.model.GetMessagesRequest;
import org.apache.james.jmap.utils.FilterToSearchQuery;
import org.apache.james.jmap.utils.SortConverter;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxId.Factory;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.util.MDCBuilder;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

public class GetMessageListMethod implements Method {

    public static final String MAXIMUM_LIMIT = "maximumLimit";
    public static final int DEFAULT_MAXIMUM_LIMIT = 256;

    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMessageList");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messageList");

    private final MailboxManager mailboxManager;
    private final int maximumLimit;
    private final GetMessagesMethod getMessagesMethod;
    private final Factory mailboxIdFactory;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting public GetMessageListMethod(MailboxManager mailboxManager,
            @Named(MAXIMUM_LIMIT) int maximumLimit, GetMessagesMethod getMessagesMethod, MailboxId.Factory mailboxIdFactory,
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
    public Stream<JmapResponse> process(JmapRequest request, ClientId clientId, MailboxSession mailboxSession) {
        Preconditions.checkArgument(request instanceof GetMessageListRequest);
        TimeMetric timeMetric = metricFactory.timer(JMAP_PREFIX + METHOD_NAME.getName());
        
        GetMessageListRequest messageListRequest = (GetMessageListRequest) request;
        GetMessageListResponse messageListResponse = getMessageListResponse(messageListRequest, mailboxSession);
        try (Closeable closeable =
                 MDCBuilder.create()
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
                     .build()) {
            Stream<JmapResponse> jmapResponse = Stream.of(JmapResponse.builder().clientId(clientId)
                    .response(messageListResponse)
                    .responseName(RESPONSE_NAME)
                    .build());
            return Stream.concat(jmapResponse,
                    processGetMessages(messageListRequest, messageListResponse, clientId, mailboxSession));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private GetMessageListResponse getMessageListResponse(GetMessageListRequest messageListRequest, MailboxSession mailboxSession) {
        GetMessageListResponse.Builder builder = GetMessageListResponse.builder();
        try {
            MultimailboxesSearchQuery searchQuery = convertToSearchQuery(messageListRequest);
            mailboxManager.search(searchQuery,
                mailboxSession,
                messageListRequest.getLimit().orElse(maximumLimit) + messageListRequest.getPosition())
                .stream()
                .skip(messageListRequest.getPosition())
                .forEach(builder::messageId);
            return builder.build();
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
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
    
    private Stream<JmapResponse> processGetMessages(GetMessageListRequest messageListRequest, GetMessageListResponse messageListResponse, ClientId clientId, MailboxSession mailboxSession) {
        if (shouldChainToGetMessages(messageListRequest)) {
            GetMessagesRequest getMessagesRequest = GetMessagesRequest.builder()
                    .ids(messageListResponse.getMessageIds())
                    .properties(messageListRequest.getFetchMessageProperties())
                    .build();
            return getMessagesMethod.process(getMessagesRequest, clientId, mailboxSession);
        }
        return Stream.empty();
    }

    private boolean shouldChainToGetMessages(GetMessageListRequest messageListRequest) {
        return messageListRequest.isFetchMessages().orElse(false) 
                && !messageListRequest.isFetchThreads().orElse(false);
    }

}
