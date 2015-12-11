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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.jmap.methods.Method.Response.Name;
import org.apache.james.jmap.model.FilterCondition;
import org.apache.james.jmap.model.GetMessageListRequest;
import org.apache.james.jmap.model.GetMessageListResponse;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.utils.SortToComparatorConvertor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

public class GetMessageListMethod<Id extends MailboxId> implements Method {

    public static final String MAXIMUM_LIMIT = "maximumLimit";
    public static final int DEFAULT_MAXIMUM_LIMIT = 256;

    private static final Logger LOGGER = LoggerFactory.getLogger(GetMailboxesMethod.class);
    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMessageList");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messageList");
    private static final int NO_LIMIT = -1;

    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory;
    private final int maximumLimit;

    @Inject
    @VisibleForTesting public GetMessageListMethod(MailboxManager mailboxManager, MailboxSessionMapperFactory<Id> mailboxSessionMapperFactory,
            @Named(MAXIMUM_LIMIT) int maximumLimit) {

        this.mailboxManager = mailboxManager;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.maximumLimit = maximumLimit;
    }

    @Override
    public Method.Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Method.Response.Name responseName() {
        return RESPONSE_NAME;
    }
    
    @Override
    public Class<? extends JmapRequest> requestType() {
        return GetMessageListRequest.class;
    }

    @Override
    public GetMessageListResponse process(JmapRequest request, MailboxSession mailboxSession) {
        Preconditions.checkArgument(request instanceof GetMessageListRequest);
        try {
            return getMessageListResponse((GetMessageListRequest) request, mailboxSession);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private GetMessageListResponse getMessageListResponse(GetMessageListRequest jmapRequest, MailboxSession mailboxSession) throws MailboxException {
        GetMessageListResponse.Builder builder = GetMessageListResponse.builder();

        mailboxManager.list(mailboxSession)
            .stream()
            .filter(mailboxPath -> isMailboxRequested(jmapRequest, mailboxPath))
            .flatMap(mailboxPath -> listMessages(mailboxPath, mailboxSession, jmapRequest))
            .skip(jmapRequest.getPosition())
            .limit(limit(jmapRequest.getLimit()))
            .map(MessageId::serialize)
            .forEach(builder::messageId);

        return builder.build();
    }

    private Stream<MessageId> listMessages(MailboxPath mailboxPath, MailboxSession mailboxSession, GetMessageListRequest jmapRequest) {
        return getMessages(mailboxPath, mailboxSession).stream()
                .sorted(comparatorFor(jmapRequest))
                .map(message -> new MessageId(mailboxSession.getUser(), mailboxPath, message.getUid()));
    }
    
    private long limit(Optional<Integer> limit) {
        return limit.orElse(maximumLimit);
    }

    private Comparator<Message<Id>> comparatorFor(GetMessageListRequest jmapRequest) {
        return SortToComparatorConvertor.comparatorFor(jmapRequest.getSort());
    }

    private boolean isMailboxRequested(GetMessageListRequest jmapRequest, MailboxPath mailboxPath) {
        if (jmapRequest.getFilter().isPresent()) {
            return jmapRequest.getFilter()
                .filter(FilterCondition.class::isInstance)
                .map(FilterCondition.class::cast)
                .map(FilterCondition::getInMailboxes)
                .filter(list -> isMailboxInList(mailboxPath, list))
                .isPresent();
        }
        return true;
    }

    private boolean isMailboxInList(MailboxPath mailboxPath, List<String> inMailboxes) {
        return inMailboxes.contains(mailboxPath.getName());
    }

    private Optional<MessageManager> getMessageManager(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        try {
            return Optional.of(mailboxManager.getMailbox(mailboxPath, mailboxSession));
        } catch (MailboxException e) {
            LOGGER.warn("Error retrieveing mailbox :" + mailboxPath, e);
            return Optional.empty();
        }
    }

    private List<Message<Id>> getMessages(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.all());
        try {
            MessageMapper<Id> messageMapper = mailboxSessionMapperFactory.getMessageMapper(mailboxSession);
            Optional<MessageManager> messageManager = getMessageManager(mailboxPath, mailboxSession);
            return ImmutableList.copyOf(messageManager.get().search(searchQuery, mailboxSession))
                    .stream()
                    .map(Throwing.function(messageId -> getMessage(mailboxPath, mailboxSession, messageMapper, messageId)))
                    .collect(Collectors.toList());
        } catch (MailboxException e) {
            LOGGER.warn("Error when searching messages for query :" + searchQuery, e);
            return ImmutableList.of();
        }
    }

    private Message<Id> getMessage(MailboxPath mailboxPath, MailboxSession mailboxSession, MessageMapper<Id> messageMapper, long messageId) throws MailboxException {
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

    private Optional<Mailbox<Id>> getMailbox(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        try {
            return Optional.of(mailboxSessionMapperFactory.getMailboxMapper(mailboxSession)
                    .findMailboxByPath(mailboxPath));
        } catch (MailboxException e) {
            LOGGER.warn("Error retrieveing mailboxId :" + mailboxPath, e);
            return Optional.empty();
        }
    }
}
