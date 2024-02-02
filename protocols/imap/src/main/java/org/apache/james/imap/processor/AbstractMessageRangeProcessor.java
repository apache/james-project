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

package org.apache.james.imap.processor;

import java.util.Objects;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.AbstractMessageRangeRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractMessageRangeProcessor<R extends AbstractMessageRangeRequest> extends AbstractMailboxProcessor<R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMessageRangeProcessor.class);

    public AbstractMessageRangeProcessor(Class<R> acceptableClass, MailboxManager mailboxManager, StatusResponseFactory factory,
                                         MetricFactory metricFactory) {
        super(acceptableClass, mailboxManager, factory, metricFactory);
    }

    protected abstract Flux<MessageRange> process(MailboxId targetMailbox,
                                                  SelectedMailbox currentMailbox,
                                                  MailboxSession mailboxSession,
                                                  MessageRange messageSet);

    protected abstract String getOperationName();

    @Override
    protected Mono<Void> processRequestReactive(R request, ImapSession session, Responder responder) {
        MailboxPath targetMailbox = PathConverter.forSession(session).buildFullPath(request.getMailboxName());
        MailboxSession mailboxSession = session.getMailboxSession();

        return Mono.from(getMailboxManager().mailboxExists(targetMailbox, mailboxSession))
            .flatMap(targetExists -> {
                if (!targetExists) {
                    no(request, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX, StatusResponse.ResponseCode.tryCreate());
                    return Mono.empty();
                } else {
                    return handleRanges(request, session, targetMailbox, mailboxSession)
                        .flatMap(code -> unsolicitedResponses(session, responder, request.isUseUids())
                            .then(Mono.fromRunnable(() -> okComplete(request, code, responder))));
                }
            })
            .onErrorResume(MessageRangeException.class, e -> {
                taggedBad(request, responder, HumanReadableText.INVALID_MESSAGESET);
                return ReactorUtils.logAsMono(() -> LOGGER.debug("{} failed from mailbox {} to {} for invalid sequence-set {}",
                    getOperationName(), session.getSelected().getMailboxId(), targetMailbox, request.getIdSet(), e));
            })
            .onErrorResume(OverQuotaException.class, e -> {
                no(request, responder, HumanReadableText.FAILURE_OVERQUOTA, StatusResponse.ResponseCode.overQuota());
                return ReactorUtils.logAsMono(() -> LOGGER.info("{} failed from mailbox {} to {} due to quota restriction",
                    getOperationName(), session.getSelected().getMailboxId(), targetMailbox, e));
            })
            .onErrorResume(OverQuotaException.class, e -> {
                no(request, responder, HumanReadableText.FAILURE_OVERQUOTA, StatusResponse.ResponseCode.overQuota());
                return ReactorUtils.logAsMono(() -> LOGGER.info("{} failed: quota exceeded from mailbox {} to {} for sequence-set {}",
                    getOperationName(), session.getSelected().getMailboxId(), targetMailbox, request.getIdSet(), e.getCause()));
            })
            .onErrorResume(MailboxException.class, e -> {
                no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
                return ReactorUtils.logAsMono(() -> LOGGER.error("{} failed from mailbox {} to {} for sequence-set {}",
                    getOperationName(), session.getSelected().getMailboxId(), targetMailbox, request.getIdSet(), e.getCause()));
            }).then();
    }

    private Mono<StatusResponse.ResponseCode> handleRanges(R request, ImapSession session, MailboxPath targetMailbox, MailboxSession mailboxSession) {
        return Mono.from(getMailboxManager()
            .getMailboxReactive(targetMailbox, mailboxSession))
            .flatMap(target -> {
                try {
                    UidValidity uidValidity = target.getMailboxEntity().getUidValidity();
                    return Flux.fromArray(request.getIdSet())
                        .map(Throwing.<IdRange, MessageRange>function(
                            range -> messageRange(session.getSelected(), range, request.isUseUids())
                                .orElseThrow(() -> new MessageRangeException(range.getFormattedString() + " is an invalid range")))
                            .sneakyThrow())
                        .filter(Objects::nonNull)
                        .concatMap(range -> process(target.getId(), session.getSelected(), mailboxSession, range)
                            .map(IdRange::from))
                        .collect(ImmutableList.<IdRange>toImmutableList())
                        .map(IdRange::mergeRanges)
                        .map(ranges -> StatusResponse.ResponseCode.copyUid(uidValidity, request.getIdSet(), ranges.toArray(IdRange[]::new)));
                } catch (MailboxException e) {
                    return Mono.error(e);
                }
            });
    }
}
