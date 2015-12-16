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

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.jmap.model.GetMailboxesRequest;
import org.apache.james.jmap.model.GetMailboxesResponse;
import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.jmap.model.mailbox.SortOrder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

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
    public Method.Response.Name responseName() {
        return RESPONSE_NAME;
    }
    
    @Override
    public Class<? extends JmapRequest> requestType() {
        return GetMailboxesRequest.class;
    }
    
    @Override
    public GetMailboxesResponse process(JmapRequest request, MailboxSession mailboxSession) {
        Preconditions.checkArgument(request instanceof GetMailboxesRequest);
        try {
            return getMailboxesResponse(mailboxSession);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private GetMailboxesResponse getMailboxesResponse(MailboxSession mailboxSession) throws MailboxException {
        GetMailboxesResponse.Builder builder = GetMailboxesResponse.builder();

        mailboxManager.list(mailboxSession)
            .stream()
            .map(mailboxPath -> mailboxFromMailboxPath(mailboxPath, mailboxSession))
            .forEach(mailbox -> builder.add(mailbox.get()));

        return builder.build();
    }
    
    private Optional<Mailbox> mailboxFromMailboxPath(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        try {
            Optional<Role> role = Role.from(mailboxPath.getName());
            return Optional.ofNullable(Mailbox.builder()
                    .id(getMailboxId(mailboxPath, mailboxSession))
                    .name(mailboxPath.getName())
                    .role(role)
                    .unreadMessages(unreadMessages(mailboxPath, mailboxSession))
                    .sortOrder(SortOrder.getSortOrder(role))
                    .build());
        } catch (MailboxException e) {
            LOGGER.warn("Cannot find mailbox for :" + mailboxPath.getName(), e);
            return Optional.empty();
        }
    }

    private String getMailboxId(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException, MailboxNotFoundException {
        return mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(mailboxPath)
                .getMailboxId()
                .serialize();
    }

    private long unreadMessages(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        return mailboxManager.getMailbox(mailboxPath, mailboxSession)
                .getMetaData(DONT_RESET_RECENT, mailboxSession, FetchGroup.UNSEEN_COUNT)
                .getUnseenCount();
    }

}
