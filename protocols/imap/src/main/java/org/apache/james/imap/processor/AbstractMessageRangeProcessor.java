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

import java.util.ArrayList;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionUtils;
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
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.collect.ImmutableList;

public abstract class AbstractMessageRangeProcessor<M extends AbstractMessageRangeRequest> extends AbstractMailboxProcessor<M> {

    public AbstractMessageRangeProcessor(Class<M> acceptableClass, ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(acceptableClass, next, mailboxManager, factory, metricFactory);
    }

    abstract protected List<MessageRange> process(final MailboxPath targetMailbox,
                                         final SelectedMailbox currentMailbox,
                                         final MailboxSession mailboxSession,
                                         final MailboxManager mailboxManager,
                                         MessageRange messageSet) throws MailboxException;

    abstract protected String getOperationName();

    @Override
    protected void doProcess(M request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final MailboxPath targetMailbox = PathConverter.forSession(session).buildFullPath(request.getMailboxName());
        final IdRange[] idSet = request.getIdSet();
        final boolean useUids = request.isUseUids();
        final SelectedMailbox currentMailbox = session.getSelected();
        try {
            final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);
            final MailboxManager mailboxManager = getMailboxManager();
            final boolean mailboxExists = mailboxManager.mailboxExists(targetMailbox, mailboxSession);

            if (!mailboxExists) {
                no(command, tag, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX, StatusResponse.ResponseCode.tryCreate());
            } else {

                final MessageManager mailbox = mailboxManager.getMailbox(targetMailbox, mailboxSession);

                List<IdRange> resultRanges = new ArrayList<>();
                for (IdRange range : idSet) {
                    MessageRange messageSet = messageRange(currentMailbox, range, useUids);
                    if (messageSet != null) {
                        List<MessageRange> processedUids = process(
                            targetMailbox, currentMailbox, mailboxSession,
                            mailboxManager, messageSet);
                        for (MessageRange mr : processedUids) {
                            // Set recent flag on copied message as this SHOULD be
                            // done.
                            // See RFC 3501 6.4.7. COPY Command
                            // See IMAP-287
                            //
                            // Disable this as this is now done directly in the scope of the copy operation.
                            // See MAILBOX-85
                            //mailbox.setFlags(new Flags(Flags.Flag.RECENT), true, false, mr, mailboxSession);
                            resultRanges.add(new IdRange(mr.getUidFrom().asLong(), mr.getUidTo().asLong()));
                        }
                    }
                }
                IdRange[] resultUids = IdRange.mergeRanges(resultRanges).toArray(new IdRange[0]);

                // get folder UIDVALIDITY
                Long uidValidity = mailbox.getMetaData(false, mailboxSession, MessageManager.MetaData.FetchGroup.NO_UNSEEN).getUidValidity();

                unsolicitedResponses(session, responder, useUids);
                okComplete(command, tag, StatusResponse.ResponseCode.copyUid(uidValidity, idSet, resultUids), responder);
            }
        } catch (MessageRangeException e) {
            if (session.getLog().isDebugEnabled()) {
                session.getLog().debug(getOperationName() + " failed from mailbox " + currentMailbox.getPath() + " to " + targetMailbox + " for invalid sequence-set " + ImmutableList.copyOf(idSet), e);
            }
            taggedBad(command, tag, responder, HumanReadableText.INVALID_MESSAGESET);
        } catch (MailboxException e) {
            session.getLog().error(getOperationName() + " failed from mailbox " + currentMailbox.getPath() + " to " + targetMailbox + " for sequence-set " + ImmutableList.copyOf(idSet), e);
            no(command, tag, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }
}
