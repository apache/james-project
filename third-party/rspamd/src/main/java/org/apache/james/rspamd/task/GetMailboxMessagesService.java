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

import java.util.Date;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.streams.Iterators;

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

    public Flux<MessageResult> getMailboxMessagesOfAllUser(String mailboxName, Optional<Date> afterDate, double samplingProbability,
                                                           FeedSpamToRspamdTask.Context context) throws UsersRepositoryException {
        return Iterators.toFlux(userRepository.list())
            .flatMap(username -> getMailboxMessagesOfAUser(username, mailboxName, afterDate, samplingProbability, context), ReactorUtils.DEFAULT_CONCURRENCY);
    }

    public Flux<MessageResult> getHamMessagesOfAllUser(Optional<Date> afterDate, double samplingProbability,
                                                       FeedHamToRspamdTask.Context context) throws UsersRepositoryException {
        return Iterators.toFlux(userRepository.list())
            .flatMap(Throwing.function(username -> Flux.fromIterable(mailboxManager.list(mailboxManager.createSystemSession(username)))
                .filter(this::hamMailboxesPredicate)
                .flatMap(mailboxPath -> getMailboxMessagesOfAUser(username, mailboxPath, afterDate, samplingProbability, context), 2)), ReactorUtils.DEFAULT_CONCURRENCY);
    }

    private Flux<MessageResult> getMailboxMessagesOfAUser(Username username, String mailboxName, Optional<Date> afterDate,
                                                          double samplingProbability, FeedSpamToRspamdTask.Context context) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);

        return Mono.from(mailboxManager.getMailboxReactive(MailboxPath.forUser(username, mailboxName), mailboxSession))
            .map(Throwing.function(MessageManager::getMailboxEntity))
            .flatMapMany(Throwing.function(mailbox -> mapperFactory.getMessageMapper(mailboxSession).findInMailboxReactive(mailbox, MessageRange.all(), MessageMapper.FetchType.METADATA, UNLIMITED)))
            .doOnNext(mailboxMessageMetaData -> context.incrementSpamMessageCount())
            .filter(mailboxMessageMetaData -> afterDate.map(date -> mailboxMessageMetaData.getInternalDate().after(date)).orElse(true))
            .filter(message -> randomBooleanWithProbability(samplingProbability))
            .map(Message::getMessageId)
            .collectList()
            .flatMapMany(messageIds -> messageIdManager.getMessagesReactive(messageIds, FetchGroup.FULL_CONTENT, mailboxSession));
    }

    private Flux<MessageResult> getMailboxMessagesOfAUser(Username username, MailboxPath mailboxPath, Optional<Date> afterDate,
                                                          double samplingProbability, FeedHamToRspamdTask.Context context) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);

        return Mono.from(mailboxManager.getMailboxReactive(mailboxPath, mailboxSession))
            .map(Throwing.function(MessageManager::getMailboxEntity))
            .flatMapMany(Throwing.function(mailbox -> mapperFactory.getMessageMapper(mailboxSession).findInMailboxReactive(mailbox, MessageRange.all(), MessageMapper.FetchType.METADATA, UNLIMITED)))
            .doOnNext(mailboxMessageMetaData -> context.incrementHamMessageCount())
            .filter(mailboxMessageMetaData -> afterDate.map(date -> mailboxMessageMetaData.getInternalDate().after(date)).orElse(true))
            .filter(message -> randomBooleanWithProbability(samplingProbability))
            .map(Message::getMessageId)
            .collectList()
            .flatMapMany(messageIds -> messageIdManager.getMessagesReactive(messageIds, FetchGroup.FULL_CONTENT, mailboxSession));
    }

    public static boolean randomBooleanWithProbability(double probability) {
        if (probability == 1.0) {
            return true;
        }
        return Math.random() < probability;
    }

    private boolean hamMailboxesPredicate(MailboxPath mailboxPath) {
        return !mailboxPath.getName().equals(SPAM_MAILBOX_NAME) && !mailboxPath.getName().equals(TRASH_MAILBOX_NAME);
    }
}
