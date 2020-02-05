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

package org.apache.james.webadmin.service;

import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.dto.MailboxResponse;
import org.apache.james.webadmin.utils.MailboxHaveChildrenException;
import org.apache.james.webadmin.validation.MailboxName;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public class UserMailboxesService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserMailboxesService.class);

    private final MailboxManager mailboxManager;
    private final UsersRepository usersRepository;
    private final MailQueueFactory<? extends ManageableMailQueue> mailQueueFactory;

    @Inject
    public UserMailboxesService(MailboxManager mailboxManager, UsersRepository usersRepository,
            MailQueueFactory<? extends ManageableMailQueue> mailQueueFactory) {
        this.mailboxManager = mailboxManager;
        this.usersRepository = usersRepository;
        this.mailQueueFactory = mailQueueFactory;
    }

    public void createMailbox(String username, MailboxName mailboxName)
            throws MailboxException, UsersRepositoryException {
        usernamePreconditions(username);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        try {
            mailboxManager.createMailbox(convertToMailboxPath(username, mailboxName.asString(), mailboxSession),
                    mailboxSession);
        } catch (MailboxExistsException e) {
            LOGGER.info("Attempt to create mailbox {} for user {} that already exists", mailboxName, username);
        }
    }

    public void deleteMailboxes(String username) throws MailboxException, UsersRepositoryException {
        usernamePreconditions(username);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        listUserMailboxes(mailboxSession).map(MailboxMetaData::getPath)
                .forEach(Throwing.consumer(mailboxPath -> deleteMailbox(mailboxSession, mailboxPath)));
    }

    public List<MailboxResponse> listMailboxes(String username) throws MailboxException, UsersRepositoryException {
        usernamePreconditions(username);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        return listUserMailboxes(mailboxSession)
                .map(mailboxMetaData -> new MailboxResponse(mailboxMetaData.getPath().getName()))
                .collect(Guavate.toImmutableList());
    }

    public boolean testMailboxExists(String username, MailboxName mailboxName)
            throws MailboxException, UsersRepositoryException {
        usernamePreconditions(username);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        return mailboxManager.mailboxExists(convertToMailboxPath(username, mailboxName.asString(), mailboxSession),
                mailboxSession);
    }

    public void deleteMailbox(String username, MailboxName mailboxName)
            throws MailboxException, UsersRepositoryException, MailboxHaveChildrenException {
        usernamePreconditions(username);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MailboxPath mailboxPath = convertToMailboxPath(username, mailboxName.asString(), mailboxSession);
        listChildren(mailboxPath, mailboxSession)
                .forEach(Throwing.consumer(path -> deleteMailbox(mailboxSession, path)));
    }

    public List<MessageResult> listMailsFromMailbox(String username, MailboxName mailboxName, long offset, long limit)
            throws UsersRepositoryException, BadCredentialsException, MailboxException {
        usernamePreconditions(username);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MailboxPath mailboxPath = convertToMailboxPath(username, mailboxName.asString(), mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);

        List<MessageResult> list = Lists.newArrayList();

        MessageRange range = MessageRange.range(MessageUid.of(MessageUid.MIN_VALUE.asLong() + offset),
                MessageUid.of(MessageUid.MIN_VALUE.asLong() + offset + limit));
        FetchGroup group = FetchGroupImpl.MINIMAL;
        MessageResultIterator iterator = messageManager.getMessages(range, group, mailboxSession);

        // Adds each item to the list
        iterator.forEachRemaining(list::add);

        return list;
    }

    public MessageResult getMail(String username, MailboxName mailboxName, MessageUid messageId)
            throws UsersRepositoryException, BadCredentialsException, MailboxException {
        usernamePreconditions(username);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MailboxPath mailboxPath = convertToMailboxPath(username, mailboxName.asString(), mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);

        FetchGroup group = FetchGroupImpl.FULL_CONTENT;

        MessageResultIterator iterator = messageManager.getMessages(MessageRange.one(messageId), group, mailboxSession);

        if (iterator.hasNext()) {
            return iterator.next();
        } else {
            return null;
        }
    }

    public void deleteMail(String username, MailboxName mailboxName, MessageUid messageId)
            throws UsersRepositoryException, BadCredentialsException, MailboxException {
        usernamePreconditions(username);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MailboxPath mailboxPath = convertToMailboxPath(username, mailboxName.asString(), mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);

        messageManager.delete(Lists.newArrayList(messageId), mailboxSession);
    }

    public void sendMail(Mail mail)
            throws UsersRepositoryException, BadCredentialsException, MailboxException, MailQueueException {
        ManageableMailQueue queue = this.mailQueueFactory.getQueue(MailQueueFactory.SPOOL).get();
        queue.enQueue(mail);
        queue.flush(); // Not really necessary!
    }

    private Stream<MailboxPath> listChildren(MailboxPath mailboxPath, MailboxSession mailboxSession)
            throws MailboxException {
        return listUserMailboxes(mailboxSession).map(MailboxMetaData::getPath)
                .filter(path -> path.getHierarchyLevels(mailboxSession.getPathDelimiter()).contains(mailboxPath));
    }

    private void deleteMailbox(MailboxSession mailboxSession, MailboxPath mailboxPath) throws MailboxException {
        try {
            mailboxManager.deleteMailbox(mailboxPath, mailboxSession);
        } catch (MailboxNotFoundException e) {
            LOGGER.info("Attempt to delete mailbox {} for user {} that does not exists", mailboxPath.getName(),
                    mailboxPath.getUser());
        }
    }

    private void usernamePreconditions(String username) throws UsersRepositoryException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(username));
        Preconditions.checkState(usersRepository.contains(username));
    }

    private MailboxPath convertToMailboxPath(String username, String mailboxName, MailboxSession mailboxSession) {
        return MailboxPath.forUser(username, mailboxName);
    }

    private Stream<MailboxMetaData> listUserMailboxes(MailboxSession mailboxSession) throws MailboxException {
        return mailboxManager.search(MailboxQuery.privateMailboxesBuilder(mailboxSession).build(), mailboxSession)
                .stream();
    }

}
