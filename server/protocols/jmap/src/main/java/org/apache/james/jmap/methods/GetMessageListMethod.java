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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.FilterCondition;
import org.apache.james.jmap.model.GetMessageListRequest;
import org.apache.james.jmap.model.GetMessageListResponse;
import org.apache.james.jmap.model.GetMessagesRequest;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.utils.MailboxUtils;
import org.apache.james.jmap.utils.SortToComparatorConvertor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.util.streams.ImmutableCollectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

public class GetMessageListMethod implements Method {

    public static final String MAXIMUM_LIMIT = "maximumLimit";
    public static final int DEFAULT_MAXIMUM_LIMIT = 256;

    private static final Logger LOGGER = LoggerFactory.getLogger(GetMailboxesMethod.class);
    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMessageList");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messageList");
    private static final int NO_LIMIT = -1;

    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final int maximumLimit;
    private final GetMessagesMethod getMessagesMethod;
    private final MailboxUtils mailboxUtils;

    @Inject
    @VisibleForTesting public GetMessageListMethod(MailboxManager mailboxManager, MailboxSessionMapperFactory mailboxSessionMapperFactory,
            @Named(MAXIMUM_LIMIT) int maximumLimit, GetMessagesMethod getMessagesMethod, MailboxUtils mailboxUtils) {

        this.mailboxManager = mailboxManager;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.maximumLimit = maximumLimit;
        this.getMessagesMethod = getMessagesMethod;
        this.mailboxUtils = mailboxUtils;
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
        GetMessageListRequest messageListRequest = (GetMessageListRequest) request;
        GetMessageListResponse messageListResponse = getMessageListResponse(messageListRequest, clientId, mailboxSession);
 
        Stream<JmapResponse> jmapResponse = Stream.of(JmapResponse.builder().clientId(clientId)
                .response(messageListResponse)
                .responseName(RESPONSE_NAME)
                .build());
        return Stream.<JmapResponse> concat(jmapResponse, 
                processGetMessages(messageListRequest, messageListResponse, clientId, mailboxSession));
    }

    private GetMessageListResponse getMessageListResponse(GetMessageListRequest messageListRequest, ClientId clientId, MailboxSession mailboxSession) {
        GetMessageListResponse.Builder builder = GetMessageListResponse.builder();
        try {
            List<MailboxPath> mailboxPaths = getUserPrivateMailboxes(mailboxSession);
            listRequestedMailboxes(messageListRequest, mailboxPaths, mailboxSession)
                .stream()
                .flatMap(mailboxPath -> listMessages(mailboxPath, mailboxSession, messageListRequest))
                .skip(messageListRequest.getPosition())
                .limit(limit(messageListRequest.getLimit()))
                .forEach(builder::messageId);

            return builder.build();
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private List<MailboxPath> getUserPrivateMailboxes(MailboxSession mailboxSession) throws MailboxException {
        MailboxQuery userMailboxesQuery = MailboxQuery
                .builder(mailboxSession)
                .privateUserMailboxes()
                .build();
        return mailboxManager.search(userMailboxesQuery, mailboxSession)
                .stream()
                .map(MailboxMetaData::getPath)
                .collect(ImmutableCollectors.toImmutableList());
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

    private Stream<MessageId> listMessages(MailboxPath mailboxPath, MailboxSession mailboxSession, GetMessageListRequest messageListRequest) {
        return getMessages(mailboxPath, mailboxSession).stream()
                .sorted(comparatorFor(messageListRequest))
                .map(message -> new MessageId(mailboxSession.getUser(), mailboxPath, message.getUid()));
    }
    
    private long limit(Optional<Integer> limit) {
        return limit.orElse(maximumLimit);
    }

    private Comparator<MailboxMessage> comparatorFor(GetMessageListRequest messageListRequest) {
        return SortToComparatorConvertor.comparatorFor(messageListRequest.getSort());
    }

    private List<MailboxPath> listRequestedMailboxes(GetMessageListRequest messageListRequest, List<MailboxPath> mailboxPaths, MailboxSession session) {
        return messageListRequest.getFilter()
                .filter(FilterCondition.class::isInstance)
                .map(FilterCondition.class::cast)
                .map(filterCondition -> filterMailboxPaths(mailboxPaths, session, filterCondition))
                .orElse(mailboxPaths);
    }

    private List<MailboxPath> filterMailboxPaths(List<MailboxPath> mailboxPaths, MailboxSession session, FilterCondition filterCondition) {
        Predicate<MailboxPath> inMailboxesPredicate = filterCondition.getInMailboxes()
                .map(list -> mailboxIdsToMailboxPaths(list, session))
                .<Predicate<MailboxPath>>map(list -> mailboxPath -> list.contains(mailboxPath))
                .orElse(x -> true);
        Predicate<MailboxPath> notInMailboxesPredicate = filterCondition.getNotInMailboxes()
                .map(list -> mailboxIdsToMailboxPaths(list, session))
                .<Predicate<MailboxPath>>map(list -> mailboxPath -> !list.contains(mailboxPath))
                .orElse(x -> true);
        return mailboxPaths.stream()
                .filter(inMailboxesPredicate)
                .filter(notInMailboxesPredicate)
                .collect(ImmutableCollectors.toImmutableList());
    }

    private List<MailboxPath> mailboxIdsToMailboxPaths(List<String> mailboxIds, MailboxSession session) {
        return mailboxIds.stream()
            .map(id -> mailboxUtils.mailboxPathFromMailboxId(id, session))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(ImmutableCollectors.toImmutableList());
    }
    
    private Optional<MessageManager> getMessageManager(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        try {
            return Optional.of(mailboxManager.getMailbox(mailboxPath, mailboxSession));
        } catch (MailboxException e) {
            LOGGER.warn("Error retrieveing mailbox :" + mailboxPath, e);
            return Optional.empty();
        }
    }

    private List<MailboxMessage> getMessages(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.all());
        try {
            MessageMapper messageMapper = mailboxSessionMapperFactory.getMessageMapper(mailboxSession);
            Optional<MessageManager> messageManager = getMessageManager(mailboxPath, mailboxSession);
            return ImmutableList.copyOf(messageManager.get().search(searchQuery, mailboxSession))
                    .stream()
                    .map(Throwing.function(messageId -> getMessage(mailboxPath, mailboxSession, messageMapper, messageId)))
                    .collect(ImmutableCollectors.toImmutableList());
        } catch (MailboxException e) {
            LOGGER.warn("Error when searching messages for query :" + searchQuery, e);
            return ImmutableList.of();
        }
    }

    private MailboxMessage getMessage(MailboxPath mailboxPath, MailboxSession mailboxSession, MessageMapper messageMapper, long messageId) throws MailboxException {
        try {
            return ImmutableList.copyOf(messageMapper.findInMailbox(
                        getMailbox(mailboxPath, mailboxSession).get(), 
                        MessageRange.one(messageId),
                        FetchType.Metadata,
                        NO_LIMIT))
                    .stream()
                    .findFirst()
                    .get();
        } catch (MailboxException e) {
            LOGGER.warn("Error retrieveing message :" + messageId, e);
            throw e;
        }
    }

    private Optional<Mailbox> getMailbox(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        try {
            return Optional.of(mailboxSessionMapperFactory.getMailboxMapper(mailboxSession)
                    .findMailboxByPath(mailboxPath));
        } catch (MailboxException e) {
            LOGGER.warn("Error retrieveing mailboxId :" + mailboxPath, e);
            return Optional.empty();
        }
    }
}
