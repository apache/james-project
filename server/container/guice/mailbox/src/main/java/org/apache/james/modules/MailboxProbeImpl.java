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

package org.apache.james.modules;

import static org.apache.james.mailbox.store.MailboxReactorUtils.block;

import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.Wildcard;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.utils.GuiceProbe;

import reactor.core.publisher.Flux;

public class MailboxProbeImpl implements GuiceProbe, MailboxProbe {
    private final MailboxManager mailboxManager;
    private final SubscriptionManager subscriptionManager;

    @Inject
    private MailboxProbeImpl(MailboxManager mailboxManager,
                             SubscriptionManager subscriptionManager) {
        this.mailboxManager = mailboxManager;
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public MailboxId createMailbox(String namespace, String user, String name) {
        return createMailbox(new MailboxPath(namespace, Username.of(user), name));
    }

    public MailboxId createMailbox(MailboxPath mailboxPath) {
        MailboxSession mailboxSession = null;
        try {
            mailboxSession = mailboxManager.createSystemSession(mailboxPath.getUser());
            mailboxManager.startProcessingRequest(mailboxSession);
            return mailboxManager.createMailbox(mailboxPath, mailboxSession)
                    .orElseThrow(() -> new MailboxException("mailbox name is probably empty"));
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        } finally {
            closeSession(mailboxSession);
        }
    }

    @Override
    public MailboxId getMailboxId(String namespace, String user, String name) {
        Username username = Username.of(user);
        MailboxSession mailboxSession = null;
        try {
            mailboxSession = mailboxManager.createSystemSession(username);
            MailboxPath path = new MailboxPath(namespace, username, name);
            return mailboxManager.getMailbox(path, mailboxSession)
                .getId();
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        } finally {
            closeSession(mailboxSession);
        }
    }

    private void closeSession(MailboxSession session) {
        if (session != null) {
            mailboxManager.endProcessingRequest(session);
            mailboxManager.logout(session);
        }
    }

    @Override
    public Collection<String> listUserMailboxes(String user) {
        MailboxSession mailboxSession = null;
        try {
            mailboxSession = mailboxManager.createSystemSession(Username.of(user));
            mailboxManager.startProcessingRequest(mailboxSession);
            return block(searchUserMailboxes(mailboxSession)
                    .map(MailboxMetaData::getPath)
                    .map(MailboxPath::getName)
                    .collect(Collectors.toList()));
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        } finally {
            closeSession(mailboxSession);
        }
    }

    private Flux<MailboxMetaData> searchUserMailboxes(MailboxSession session) {
        return mailboxManager.search(
            MailboxQuery.privateMailboxesBuilder(session)
                .expression(Wildcard.INSTANCE)
                .build(),
            session);
    }

    @Override
    public void deleteMailbox(String namespace, String user, String name) {
        MailboxSession mailboxSession = null;
        Username username = Username.of(user);
        try {
            mailboxSession = mailboxManager.createSystemSession(username);
            mailboxManager.startProcessingRequest(mailboxSession);
            mailboxManager.deleteMailbox(new MailboxPath(namespace, username, name), mailboxSession);
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        } finally {
            closeSession(mailboxSession);
        }
    }

    @Override
    public ComposedMessageId appendMessage(String username, MailboxPath mailboxPath, InputStream message, Date internalDate, boolean isRecent, Flags flags)
            throws MailboxException {

        MailboxSession mailboxSession = mailboxManager.createSystemSession(Username.of(username));
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        return messageManager.appendMessage(message, internalDate, mailboxSession, isRecent, flags).getId();
    }

    public ComposedMessageId appendMessage(String username, MailboxPath mailboxPath, MessageManager.AppendCommand appendCommand)
            throws MailboxException {

        MailboxSession mailboxSession = mailboxManager.createSystemSession(Username.of(username));
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        return messageManager.appendMessage(appendCommand, mailboxSession).getId();
    }

    @Override
    public Collection<String> listSubscriptions(String user) throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(Username.of(user));
        return subscriptionManager.subscriptions(mailboxSession);
    }
}