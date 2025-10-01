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

package org.apache.james.webadmin.data.jmap;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.List;

import jakarta.inject.Inject;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.jmap.mailet.filter.RuleMatcher;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.task.Task;
import org.apache.james.webadmin.validation.MailboxName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RunRulesOnMailboxService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunRulesOnMailboxService.class);

    private final MailboxManager mailboxManager;
    private final MailboxId.Factory mailboxIdFactory;
    private final MessageIdManager messageIdManager;

    @Inject
    public RunRulesOnMailboxService(MailboxManager mailboxManager, MailboxId.Factory mailboxIdFactory, MessageIdManager messageIdManager) {
        this.mailboxManager = mailboxManager;
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageIdManager = messageIdManager;
    }

    public Mono<Task.Result> runRulesOnMailbox(Username username, MailboxName mailboxName, Rules rules, RunRulesOnMailboxTask.Context context) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        RuleMatcher ruleMatcher = new RuleMatcher(rules.getRules());

        return Mono.from(mailboxManager.getMailboxReactive(MailboxPath.forUser(username, mailboxName.asString()), mailboxSession))
            .flatMapMany(messageManager -> Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.HEADERS, mailboxSession))
                .flatMap(Throwing.function(messageResult -> runRulesOnMessage(ruleMatcher, messageResult, mailboxSession, context)), DEFAULT_CONCURRENCY))
            .onErrorResume(e -> {
                LOGGER.error("Error when applying rules to mailbox. Mailbox {} for user {}", mailboxName.asString(), username, e);
                context.incrementFails();
                return Mono.just(Task.Result.PARTIAL);
            })
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Task.Result.COMPLETED))
            .doFinally(any -> mailboxManager.endProcessingRequest(mailboxSession));
    }

    private Flux<Task.Result> runRulesOnMessage(RuleMatcher ruleMatcher, MessageResult messageResult, MailboxSession mailboxSession, RunRulesOnMailboxTask.Context context) throws MailboxException {
        return Flux.fromStream(ruleMatcher.findApplicableRules(messageResult))
            .map(Rule::getAction)
            .concatMap(action -> applyActionOnMessage(messageResult, action, mailboxSession, context));
    }

    private Mono<Task.Result> applyActionOnMessage(MessageResult messageResult, Rule.Action action, MailboxSession mailboxSession, RunRulesOnMailboxTask.Context context) {
        actionOnMessagePreconditions(action);
        return appendInMailboxes(messageResult.getMessageId(), action, mailboxSession, context)
            .onErrorResume(e -> {
                LOGGER.error("Error when moving message to mailboxes. Message {} for user {}", messageResult.getMessageId(), mailboxSession.getUser().asString(), e);
                context.incrementFails();
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private void actionOnMessagePreconditions(Rule.Action action) {
        if (action.isMarkAsSeen() || action.isMarkAsImportant() || action.isReject()
            || action.getForward().isPresent() || !action.getWithKeywords().isEmpty()) {
            throw new NotImplementedException("Only action on moving messages is supported for now");
        }

        if (action.getAppendInMailboxes().getMailboxIds().isEmpty() && action.getMoveTo().isEmpty()) {
            throw new IllegalArgumentException("Move action should not be empty");
        }
    }

    private Mono<Task.Result> appendInMailboxes(MessageId messageId, Rule.Action action, MailboxSession mailboxSession, RunRulesOnMailboxTask.Context context) {
        ImmutableList.Builder<MailboxId> mailboxIdsBuilder = ImmutableList.builder();
        List<MailboxId> appendInMailboxIds = action.getAppendInMailboxes()
            .getMailboxIds()
            .stream()
            .map(mailboxIdFactory::fromString)
            .toList();
        mailboxIdsBuilder.addAll(appendInMailboxIds);

        return Mono.justOrEmpty(action.getMoveTo())
            .flatMap(moveTo -> getMailboxId(mailboxSession, MailboxPath.forUser(mailboxSession.getUser(), moveTo.getMailboxName())))
            .map(moveToMailboxId -> mailboxIdsBuilder.add(moveToMailboxId).build())
            .switchIfEmpty(Mono.fromCallable(mailboxIdsBuilder::build))
            .flatMap(mailboxIds -> {
                if (mailboxIds.isEmpty()) {
                    return Mono.just(Task.Result.COMPLETED);
                }

                return Mono.from(messageIdManager.setInMailboxesReactive(messageId, mailboxIds, mailboxSession))
                    .doOnSuccess(next -> context.incrementSuccesses())
                    .then(Mono.just(Task.Result.COMPLETED));
            });
    }

    private Mono<MailboxId> getMailboxId(MailboxSession mailboxSession, MailboxPath mailboxPath) {
        return Mono.from(mailboxManager.getMailboxReactive(mailboxPath, mailboxSession))
            .map(MessageManager::getId)
            .onErrorResume(MailboxNotFoundException.class, e -> Mono.from(mailboxManager.createMailboxReactive(mailboxPath, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, mailboxSession))
                .onErrorResume(MailboxExistsException.class, e2 -> {
                    LOGGER.info("Mailbox {} created concurrently", mailboxPath.asString());
                    return Mono.from(mailboxManager.getMailboxReactive(mailboxPath, mailboxSession))
                        .map(MessageManager::getId);
                }));
    }
}
