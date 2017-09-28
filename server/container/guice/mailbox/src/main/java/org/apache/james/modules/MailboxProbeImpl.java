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

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.probe.MailboxProbe;
import org.apache.james.utils.GuiceProbe;

import com.google.common.base.Throwables;

public class MailboxProbeImpl implements GuiceProbe, MailboxProbe {
    private final MailboxManager mailboxManager;
    private final MailboxMapperFactory mailboxMapperFactory;
    private final SubscriptionManager subscriptionManager;
    public static final boolean RECENT = true;

    @Inject
    private MailboxProbeImpl(MailboxManager mailboxManager, MailboxMapperFactory mailboxMapperFactory,
                             SubscriptionManager subscriptionManager) {
        this.mailboxManager = mailboxManager;
        this.mailboxMapperFactory = mailboxMapperFactory;
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public MailboxId createMailbox(String namespace, String user, String name) {
        MailboxSession mailboxSession = null;
        try {
            mailboxSession = mailboxManager.createSystemSession(user);
            mailboxManager.startProcessingRequest(mailboxSession);
            return mailboxManager.createMailbox(new MailboxPath(namespace, user, name), mailboxSession)
                    .orElseThrow(() -> new MailboxException("mailbox name is probably empty"));
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        } finally {
            closeSession(mailboxSession);
        }
    }


    @Override
    public Mailbox getMailbox(String namespace, String user, String name) {
        MailboxSession mailboxSession = null;
        try {
            mailboxSession = mailboxManager.createSystemSession(user);
            MailboxMapper mailboxMapper = mailboxMapperFactory.getMailboxMapper(mailboxSession);
            return mailboxMapper.findMailboxByPath(new MailboxPath(namespace, user, name));
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        } finally {
            closeSession(mailboxSession);
        }
    }

    private void closeSession(MailboxSession session) {
        if (session != null) {
            mailboxManager.endProcessingRequest(session);
            try {
                mailboxManager.logout(session, true);
            } catch (MailboxException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public Collection<String> listUserMailboxes(String user) {
        MailboxSession mailboxSession = null;
        try {
            mailboxSession = mailboxManager.createSystemSession(user);
            mailboxManager.startProcessingRequest(mailboxSession);
            return searchUserMailboxes(user, mailboxSession)
                    .stream()
                    .map(MailboxMetaData::getPath)
                    .map(MailboxPath::getName)
                    .collect(Collectors.toList());
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        } finally {
            closeSession(mailboxSession);
        }
    }

    private List<MailboxMetaData> searchUserMailboxes(String username, MailboxSession session) throws MailboxException {
        return mailboxManager.search(
            MailboxQuery.builder()
                .base(MailboxPath.forUser(username, ""))
                .expression("*")
                .pathDelimiter(session.getPathDelimiter())
                .build(),
            session);
    }


    @Override
    public void deleteMailbox(String namespace, String user, String name) {
        MailboxSession mailboxSession = null;
        try {
            mailboxSession = mailboxManager.createSystemSession(user);
            mailboxManager.startProcessingRequest(mailboxSession);
            mailboxManager.deleteMailbox(new MailboxPath(namespace, user, name), mailboxSession);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        } finally {
            closeSession(mailboxSession);
        }
    }

    @Override
    public void importEmlFileToMailbox(String namespace, String user, String name, String emlPath) throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(user);
        mailboxManager.startProcessingRequest(mailboxSession);

        MessageManager messageManager = mailboxManager.getMailbox(new MailboxPath(namespace, user, name), mailboxSession);
        InputStream emlFileAsStream = new FileInputStream(emlPath);
        messageManager.appendMessage(emlFileAsStream, new Date(), mailboxSession, RECENT, new Flags());

        mailboxManager.endProcessingRequest(mailboxSession);
        mailboxSession.close();
    }

    @Override
    public ComposedMessageId appendMessage(String username, MailboxPath mailboxPath, InputStream message, Date internalDate, boolean isRecent, Flags flags)
            throws MailboxException {

        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        return messageManager.appendMessage(message, internalDate, mailboxSession, isRecent, flags);
    }

    @Override
    public void copyMailbox(String srcBean, String dstBean) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void deleteUserMailboxesNames(String user) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void reIndexMailbox(String namespace, String user, String name) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void reIndexAll() throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public Collection<String> listSubscriptions(String user) throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(user);
        return subscriptionManager.subscriptions(mailboxSession);
    }

}