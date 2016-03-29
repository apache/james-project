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
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetMailboxesRequest;
import org.apache.james.jmap.model.GetMailboxesResponse;
import org.apache.james.jmap.model.MailboxProperty;
import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.jmap.model.mailbox.SortOrder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class GetMailboxesMethod<Id extends MailboxId> implements Method {

    private static final boolean DONT_RESET_RECENT = false;
    private static final Logger LOGGER = LoggerFactory.getLogger(GetMailboxesMethod.class);
    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMailboxes");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("mailboxes");

    private final MailboxManager mailboxManager; 
    private final MailboxMapperFactory<Id> mailboxMapperFactory;

    @Inject
    @VisibleForTesting public GetMailboxesMethod(MailboxManager mailboxManager, MailboxMapperFactory<Id> mailboxMapperFactory) {
        this.mailboxManager = mailboxManager;
        this.mailboxMapperFactory = mailboxMapperFactory;
    }

    @Override
    public Method.Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return GetMailboxesRequest.class;
    }

    public Stream<JmapResponse> process(JmapRequest request, ClientId clientId, MailboxSession mailboxSession) {
        Preconditions.checkArgument(request instanceof GetMailboxesRequest);
        GetMailboxesRequest mailboxesRequest = (GetMailboxesRequest) request;
        return Stream.of(
                JmapResponse.builder().clientId(clientId)
                .response(getMailboxesResponse(mailboxesRequest, mailboxSession))
                .properties(mailboxesRequest.getProperties().map(this::ensureContainsId))
                .responseName(RESPONSE_NAME)
                .build());
    }

    private Set<MailboxProperty> ensureContainsId(Set<MailboxProperty> input) {
        return Sets.union(input, ImmutableSet.of(MailboxProperty.ID)).immutableCopy();
    }

    private GetMailboxesResponse getMailboxesResponse(GetMailboxesRequest mailboxesRequest, MailboxSession mailboxSession) {
        GetMailboxesResponse.Builder builder = GetMailboxesResponse.builder();
        try {
            retrieveUserMailboxes(mailboxSession)
                .stream()
                .map(MailboxMetaData::getPath)
                .map(mailboxPath -> mailboxFromMailboxPath(mailboxPath, mailboxSession))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(filterMailboxesById(mailboxesRequest.getIds()))
                .sorted((m1, m2) -> m1.getSortOrder().compareTo(m2.getSortOrder()))
                .forEach(mailbox -> builder.add(mailbox));
            return builder.build();
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private Predicate<? super Mailbox> filterMailboxesById(Optional<ImmutableList<String>> ids) {
        return (mailbox -> ids.map(list -> list.contains(mailbox.getId())).orElse(true));
    }

    private List<MailboxMetaData> retrieveUserMailboxes(MailboxSession session) throws MailboxException {
        return mailboxManager.search(
                MailboxQuery.builder(session).privateUserMailboxes().build(),
                session);
    }

    private Optional<Mailbox> mailboxFromMailboxPath(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        try {
            Optional<Role> role = Role.from(mailboxPath.getName());
            MessageManager.MetaData mailboxMetaData = getMailboxMetaData(mailboxPath, mailboxSession);
            return Optional.ofNullable(Mailbox.builder()
                    .id(getMailboxId(mailboxPath, mailboxSession))
                    .name(mailboxPath.getName())
                    .role(role)
                    .unreadMessages(mailboxMetaData.getUnseenCount())
                    .totalMessages(mailboxMetaData.getMessageCount())
                    .sortOrder(SortOrder.getSortOrder(role))
                    .build());
        } catch (MailboxException e) {
            LOGGER.warn("Cannot find mailbox for :" + mailboxPath.getName(), e);
            return Optional.empty();
        }
    }

    private String getMailboxId(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        return mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(mailboxPath)
                .getMailboxId()
                .serialize();
    }

    private MessageManager.MetaData getMailboxMetaData(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        return mailboxManager.getMailbox(mailboxPath, mailboxSession)
                .getMetaData(DONT_RESET_RECENT, mailboxSession, FetchGroup.UNSEEN_COUNT);
    }

}
