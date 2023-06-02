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

package org.apache.james.rspamd.task;

import static org.apache.james.rspamd.task.FeedSpamToRspamdTask.SPAM_MAILBOX_NAME;
import static org.apache.james.task.Task.LOGGER;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class GetMailboxMessagesService {
    private static final int UNLIMITED = -1;
    private static final String TRASH_MAILBOX_NAME = "Trash";

    private final MailboxManager mailboxManager;
    private final UsersRepository userRepository;
    private final MailboxSessionMapperFactory mapperFactory;
    private final MessageIdManager messageIdManager;

    public GetMailboxMessagesService(MailboxManager mailboxManager, UsersRepository userRepository, MailboxSessionMapperFactory mapperFactory, MessageIdManager messageIdManager) {
        this.mailboxManager = mailboxManager;
        this.userRepository = userRepository;
        this.mapperFactory = mapperFactory;
        this.messageIdManager = messageIdManager;
    }

    public Flux<Pair<Username, MessageResult>> getMailboxMessagesOfAllUser(String mailboxName, Optional<Date> afterDate, RunningOptions runningOptions,
                                                                           FeedSpamToRspamdTask.Context context) {
        return Flux.from(userRepository.listReactive())
            .flatMap(username -> getMailboxMessagesOfAUser(username, mailboxName, afterDate, runningOptions, context)
                .map(result -> Pair.of(username, result)), 2);
    }

    public Flux<Pair<Username, MessageResult>> getHamMessagesOfAllUser(Optional<Date> afterDate, RunningOptions runningOptions,
                                                                       FeedHamToRspamdTask.Context context) {
        return Flux.from(userRepository.listReactive())
            .flatMap(Throwing.function(username ->
                Flux.from(mailboxManager.search(MailboxQuery.privateMailboxesBuilder(mailboxManager.createSystemSession(username)).build(),
                        mailboxManager.createSystemSession(username)))
                    .filter(mbxMetadata -> hamMailboxesPredicate(mbxMetadata.getPath()))
                    .flatMap(mbxMetadata -> getMailboxMessagesOfAUser(username, mbxMetadata, afterDate, runningOptions, context), 2)
                    .map(result -> Pair.of(username, result))
            ), ReactorUtils.DEFAULT_CONCURRENCY);
    }

    private Flux<MessageResult> getMailboxMessagesOfAUser(Username username, String mailboxName, Optional<Date> afterDate,
                                                          RunningOptions runningOptions, FeedSpamToRspamdTask.Context context) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);

        return Mono.from(mailboxManager.getMailboxReactive(MailboxPath.forUser(username, mailboxName), mailboxSession))
            .onErrorResume(MailboxNotFoundException.class, e -> {
                LOGGER.info("Missing Spam mailbox {}", e.getMessage());
                return Mono.empty();
            })
            .map(Throwing.function(MessageManager::getMailboxEntity))
            .flatMapMany(Throwing.function(mailbox -> mapperFactory.getMessageMapper(mailboxSession).findInMailboxReactive(mailbox, MessageRange.all(), MessageMapper.FetchType.METADATA, UNLIMITED)))
            .filter(mailboxMessageMetaData -> afterDate.map(date -> mailboxMessageMetaData.getSaveDate()
                    .orElse(mailboxMessageMetaData.getInternalDate())
                    .after(date))
                .orElse(true))
            .doOnNext(mailboxMessageMetaData -> context.incrementSpamMessageCount())
            .filter(message -> randomBooleanWithProbability(runningOptions))
            .flatMap(message -> messageIdManager.getMessagesReactive(List.of(message.getMessageId()), FetchGroup.FULL_CONTENT, mailboxSession), ReactorUtils.DEFAULT_CONCURRENCY)
            .filter(runningOptions.correspondingClassificationFilter())
            .doFinally(any -> mailboxManager.endProcessingRequest(mailboxSession));
    }

    private Flux<MessageResult> getMailboxMessagesOfAUser(Username username, MailboxMetaData mailboxMetaData, Optional<Date> afterDate,
                                                          RunningOptions runningOptions, FeedHamToRspamdTask.Context context) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);

        return Mono.from(mailboxManager.getMailboxReactive(mailboxMetaData.getId(), mailboxSession))
            .map(Throwing.function(MessageManager::getMailboxEntity))
            .flatMapMany(Throwing.function(mailbox -> mapperFactory.getMessageMapper(mailboxSession).findInMailboxReactive(mailbox, MessageRange.all(), MessageMapper.FetchType.METADATA, UNLIMITED)))
            .filter(mailboxMessageMetaData -> afterDate.map(date -> mailboxMessageMetaData.getSaveDate()
                    .orElse(mailboxMessageMetaData.getInternalDate())
                    .after(date))
                .orElse(true))
            .doOnNext(mailboxMessageMetaData -> context.incrementHamMessageCount())
            .filter(message -> randomBooleanWithProbability(runningOptions))
            .flatMap(message -> messageIdManager.getMessagesReactive(List.of(message.getMessageId()), FetchGroup.FULL_CONTENT, mailboxSession), ReactorUtils.DEFAULT_CONCURRENCY)
            .filter(runningOptions.correspondingClassificationFilter())
            .doFinally(any -> mailboxManager.endProcessingRequest(mailboxSession));
    }

    public static boolean randomBooleanWithProbability(RunningOptions runningOptions) {
        if (runningOptions.getSamplingProbability() == 1.0) {
            return true;
        }
        return Math.random() < runningOptions.getSamplingProbability();
    }

    private boolean hamMailboxesPredicate(MailboxPath mailboxPath) {
        return !mailboxPath.getName().equals(SPAM_MAILBOX_NAME) && !mailboxPath.getName().equals(TRASH_MAILBOX_NAME);
    }
}
