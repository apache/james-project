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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.AbstractMessageRangeRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;

public abstract class AbstractMessageRangeProcessor<R extends AbstractMessageRangeRequest> extends AbstractMailboxProcessor<R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMessageRangeProcessor.class);

    public AbstractMessageRangeProcessor(Class<R> acceptableClass, ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
                                         MetricFactory metricFactory) {
        super(acceptableClass, next, mailboxManager, factory, metricFactory);
    }

    protected abstract List<MessageRange> process(MailboxPath targetMailbox,
                                                  SelectedMailbox currentMailbox,
                                                  MailboxSession mailboxSession,
                                                  MessageRange messageSet) throws MailboxException;

    protected abstract String getOperationName();

    @Override
    protected void processRequest(R request, ImapSession session, Responder responder) {
        MailboxPath targetMailbox = PathConverter.forSession(session).buildFullPath(request.getMailboxName());

        try {
            MailboxSession mailboxSession = session.getMailboxSession();

            if (!getMailboxManager().mailboxExists(targetMailbox, mailboxSession)) {
                no(request, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX, StatusResponse.ResponseCode.tryCreate());
            } else {
                StatusResponse.ResponseCode code = handleRanges(request, session, targetMailbox, mailboxSession);
                unsolicitedResponses(session, responder, request.isUseUids());
                okComplete(request, code, responder);
            }
        } catch (MessageRangeException e) {
            LOGGER.debug("{} failed from mailbox {} to {} for invalid sequence-set {}",
                    getOperationName(), session.getSelected().getMailboxId(), targetMailbox, request.getIdSet(), e);
            taggedBad(request, responder, HumanReadableText.INVALID_MESSAGESET);
        } catch (MailboxException e) {
            LOGGER.error("{} failed from mailbox {} to {} for sequence-set {}",
                    getOperationName(), session.getSelected().getMailboxId(), targetMailbox, request.getIdSet(), e);
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    private StatusResponse.ResponseCode handleRanges(R request, ImapSession session, MailboxPath targetMailbox, MailboxSession mailboxSession) throws MailboxException {
        MessageManager mailbox = getMailboxManager().getMailbox(targetMailbox, mailboxSession);

        IdRange[] resultUids = IdRange.mergeRanges(Arrays.stream(request.getIdSet())
            .map(Throwing.<IdRange, MessageRange>function(
                range -> messageRange(session.getSelected(), range, request.isUseUids()))
                .sneakyThrow())
            .filter(Objects::nonNull)
            .flatMap(Throwing.<MessageRange, Stream<MessageRange>>function(
                range -> process(targetMailbox, session.getSelected(), mailboxSession, range)
                    .stream())
                .sneakyThrow())
            .map(IdRange::from)
            .collect(Guavate.toImmutableList()))
            .toArray(new IdRange[0]);

        // get folder UIDVALIDITY
        UidValidity uidValidity = mailbox.getMailboxEntity().getUidValidity();

        return StatusResponse.ResponseCode.copyUid(uidValidity, request.getIdSet(), resultUids);
    }
}
