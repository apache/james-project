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

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.jmap.model.FilterCondition;
import org.apache.james.jmap.model.GetMessageListRequest;
import org.apache.james.jmap.model.GetMessageListResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

public class GetMessageListMethod<Id extends MailboxId> implements Method {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetMailboxesMethod.class);
    private static final Method.Name METHOD_NAME = Method.name("getMessageList");

    private final MailboxManager mailboxManager;

    @Inject
    @VisibleForTesting public GetMessageListMethod(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    @Override
    public Name methodName() {
        return METHOD_NAME;
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
            .map(mailboxPath -> getMailbox(mailboxPath, mailboxSession))
            .map(messageManager -> getMessageIds(messageManager.get(), mailboxSession))
            .flatMap(List::stream)
            .map(String::valueOf)
            .forEach(builder::messageId);

        return builder.build();
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

    private Optional<MessageManager> getMailbox(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        try {
            return Optional.of(mailboxManager.getMailbox(mailboxPath, mailboxSession));
        } catch (MailboxException e) {
            LOGGER.warn("Error retrieveing mailbox :" + mailboxPath, e);
            return Optional.empty();
        }
    }

    private List<Long> getMessageIds(MessageManager messageManager, MailboxSession mailboxSession) {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.all());
        try {
            return ImmutableList.copyOf(messageManager.search(searchQuery, mailboxSession));
        } catch (MailboxException e) {
            LOGGER.warn("Error when searching messages for query :" + searchQuery, e);
            return ImmutableList.of();
        }
    }
}
