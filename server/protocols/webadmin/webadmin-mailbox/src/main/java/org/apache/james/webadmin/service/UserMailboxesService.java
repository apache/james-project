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

import static org.apache.james.mailbox.MailboxManager.MailboxSearchFetchType.Minimal;

import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.task.Task;
import org.apache.james.task.Task.Result;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.dto.MailboxResponse;
import org.apache.james.webadmin.utils.MailboxHaveChildrenException;
import org.apache.james.webadmin.validation.MailboxName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class UserMailboxesService {
    public enum Options {
        Force,
        Check
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(UserMailboxesService.class);

    private final MailboxManager mailboxManager;
    private final UsersRepository usersRepository;

    @Inject
    public UserMailboxesService(MailboxManager mailboxManager, UsersRepository usersRepository) {
        this.mailboxManager = mailboxManager;
        this.usersRepository = usersRepository;
    }

    public void createMailbox(Username username, MailboxName mailboxName, Options options) throws MailboxException, UsersRepositoryException {
        usernamePreconditions(username, options);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        try {
            MailboxPath mailboxPath = MailboxPath.forUser(username, mailboxName.asString())
                .assertAcceptable(mailboxSession.getPathDelimiter());
            mailboxManager.createMailbox(mailboxPath, mailboxSession);
        } catch (MailboxExistsException e) {
            LOGGER.info("Attempt to create mailbox {} for user {} that already exists", mailboxName, username);
        }
    }

    public void deleteMailboxes(Username username, Options options) throws UsersRepositoryException {
        usernamePreconditions(username, options);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        listUserMailboxes(mailboxSession)
            .map(MailboxMetaData::getPath)
            .forEach(Throwing.consumer(mailboxPath -> deleteMailbox(mailboxSession, mailboxPath)));
    }

    public List<MailboxResponse> listMailboxes(Username username, Options options) throws UsersRepositoryException {
        usernamePreconditions(username, options);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        return listUserMailboxes(mailboxSession)
            .map(mailboxMetaData -> new MailboxResponse(mailboxMetaData.getPath().getName(), mailboxMetaData.getId()))
            .collect(ImmutableList.toImmutableList());
    }

    public boolean testMailboxExists(Username username, MailboxName mailboxName, Options options) throws MailboxException, UsersRepositoryException {
        usernamePreconditions(username, options);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MailboxPath mailboxPath = MailboxPath.forUser(username, mailboxName.asString())
            .assertAcceptable(mailboxSession.getPathDelimiter());
        return Mono.from(mailboxManager.mailboxExists(mailboxPath, mailboxSession))
            .block();
    }


    public Mono<Result> clearMailboxContent(Username username, MailboxName mailboxName, ClearMailboxContentTask.Context context) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);

        return Mono.from(mailboxManager.getMailboxReactive(MailboxPath.forUser(username, mailboxName.asString()), mailboxSession))
            .flatMapMany(messageManager -> Flux.from(messageManager.listMessagesMetadata(MessageRange.all(), mailboxSession))
                .map(metaData -> metaData.getComposedMessageId().getUid())
                .concatMap(messageUid -> deleteMessage(messageManager, messageUid, mailboxSession, context)))
            .onErrorResume(e -> {
                LOGGER.error("Error when clear mailbox content. Mailbox {} for user {}", mailboxName.asString(), username, e);
                context.incrementMessageFails();
                return Mono.just(Result.PARTIAL);
            })
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Result.COMPLETED));
    }

    private Mono<Result> deleteMessage(MessageManager messageManager, MessageUid messageUid, MailboxSession mailboxSession, ClearMailboxContentTask.Context context) {
        return Mono.fromCallable(() -> {
                try {
                    messageManager.delete(List.of(messageUid), mailboxSession);
                    context.incrementSuccesses();
                    return Result.COMPLETED;
                } catch (MailboxException e) {
                    context.incrementMessageFails();
                    return Result.PARTIAL;
                }
            })
            .subscribeOn(Schedulers.elastic());
    }

    public void deleteMailbox(Username username, MailboxName mailboxName, Options options) throws MailboxException, UsersRepositoryException, MailboxHaveChildrenException {
        usernamePreconditions(username, options);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MailboxPath mailboxPath = MailboxPath.forUser(username, mailboxName.asString())
            .assertAcceptable(mailboxSession.getPathDelimiter());
        listChildren(mailboxPath, mailboxSession)
            .forEach(Throwing.consumer(path -> deleteMailbox(mailboxSession, path)));
    }

    public long messageCount(Username username, MailboxName mailboxName, Options options) throws UsersRepositoryException, MailboxException {
        usernamePreconditions(username, options);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        return mailboxManager.getMailbox(MailboxPath.forUser(username, mailboxName.asString()), mailboxSession).getMessageCount(mailboxSession);
    }

    public long unseenMessageCount(Username username, MailboxName mailboxName, Options options) throws UsersRepositoryException, MailboxException {
        usernamePreconditions(username, options);
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        return mailboxManager.getMailbox(MailboxPath.forUser(username, mailboxName.asString()), mailboxSession)
            .getMailboxCounters(mailboxSession)
            .getUnseen();
    }

    private Stream<MailboxPath> listChildren(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        return listUserMailboxes(mailboxSession)
            .map(MailboxMetaData::getPath)
            .filter(path -> path.getHierarchyLevels(mailboxSession.getPathDelimiter()).contains(mailboxPath));
    }

    private void deleteMailbox(MailboxSession mailboxSession, MailboxPath mailboxPath) throws MailboxException {
        try {
            mailboxManager.deleteMailbox(mailboxPath, mailboxSession);
        } catch (MailboxNotFoundException e) {
            LOGGER.info("Attempt to delete mailbox {} for user {} that does not exists", mailboxPath.getName(), mailboxPath.getUser());
        }
    }

    public void usernamePreconditions(Username username, Options options) throws UsersRepositoryException {
        Preconditions.checkState(options == Options.Force || usersRepository.contains(username), "User does not exist");
    }

    public void mailboxExistPreconditions(Username username, MailboxName mailboxName) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MailboxPath mailboxPath = MailboxPath.forUser(username, mailboxName.asString())
            .assertAcceptable(mailboxSession.getPathDelimiter());
        Preconditions.checkState(Boolean.TRUE.equals(Mono.from(mailboxManager.mailboxExists(mailboxPath, mailboxSession)).block()),
            "Mailbox does not exist. " + mailboxPath.asString());
    }

    private Stream<MailboxMetaData> listUserMailboxes(MailboxSession mailboxSession) {
        return mailboxManager.search(
            MailboxQuery.privateMailboxesBuilder(mailboxSession).build(),
            Minimal, mailboxSession)
            .toStream();
    }

}
