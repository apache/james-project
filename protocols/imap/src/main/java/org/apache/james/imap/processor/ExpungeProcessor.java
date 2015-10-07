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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.ExpungeRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.MessageRange;

public class ExpungeProcessor extends AbstractMailboxProcessor<ExpungeRequest> implements CapabilityImplementingProcessor {

    private final static List<String> UIDPLUS = Collections.unmodifiableList(Arrays.asList("UIDPLUS"));

    public ExpungeProcessor(final ImapProcessor next, final MailboxManager mailboxManager, final StatusResponseFactory factory) {
        super(ExpungeRequest.class, next, mailboxManager, factory);
    }

    protected void doProcess(ExpungeRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        try {
            final MessageManager mailbox = getSelectedMailbox(session);
            final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);

            int expunged = 0;
            MetaData mdata = mailbox.getMetaData(false, mailboxSession, FetchGroup.NO_COUNT);

            if (!mdata.isWriteable()) {
                no(command, tag, responder, HumanReadableText.MAILBOX_IS_READ_ONLY);
            } else {
                IdRange[] ranges = request.getUidSet();
                if (ranges == null) {
                   expunged = expunge(mailbox, MessageRange.all(), session, mailboxSession);
                } else {
                    // Handle UID EXPUNGE which is part of UIDPLUS
                    // See http://tools.ietf.org/html/rfc4315
                    for (int i = 0; i < ranges.length; i++) {
                        MessageRange mRange = messageRange(session.getSelected(), ranges[i], true);
                        if (mRange != null) {
                            expunged += expunge(mailbox, mRange, session, mailboxSession);
                        }

                    }

                }
                unsolicitedResponses(session, responder, false);
                
                
                // Check if QRESYNC was enabled and at least one message was expunged. If so we need to respond with an OK response that contain the HIGHESTMODSEQ
                //
                // See RFC5162 3.3 EXPUNGE Command 3.5. UID EXPUNGE Command
                if (EnableProcessor.getEnabledCapabilities(session).contains(ImapConstants.SUPPORTS_QRESYNC)  && expunged > 0) {
                    okComplete(command, tag, ResponseCode.highestModSeq(mdata.getHighestModSeq()), responder);
                } else {
                    okComplete(command, tag, responder);
                }
            }
        } catch (MessageRangeException e) {
            if (session.getLog().isDebugEnabled()) {
                session.getLog().debug("Expunge failed", e);
            }
            taggedBad(command, tag, responder, HumanReadableText.INVALID_MESSAGESET);
        } catch (MailboxException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("Expunge failed for mailbox " + session.getSelected().getPath(), e);
            }
            no(command, tag, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    private int expunge(MessageManager mailbox, MessageRange range, ImapSession session, MailboxSession mailboxSession) throws MailboxException {
        final Iterator<Long> it = mailbox.expunge(range, mailboxSession);
        final SelectedMailbox selected = session.getSelected();
        int expunged = 0;
        if (mailboxSession != null) {
            while (it.hasNext()) {
                final long uid = it.next();
                selected.removeRecent(uid);
                expunged++;
            }
        }
        return expunged;
    }

    /**
     * @see org.apache.james.imap.processor.CapabilityImplementingProcessor
     * #getImplementedCapabilities(org.apache.james.imap.api.process.ImapSession)
     */
    public List<String> getImplementedCapabilities(ImapSession session) {
        return UIDPLUS;
    }
}
