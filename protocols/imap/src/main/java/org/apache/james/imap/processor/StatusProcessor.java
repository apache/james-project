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

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.StatusDataItems;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.StatusRequest;
import org.apache.james.imap.message.response.MailboxStatusResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.slf4j.Logger;

public class StatusProcessor extends AbstractMailboxProcessor<StatusRequest> {

    public StatusProcessor(final ImapProcessor next, final MailboxManager mailboxManager, final StatusResponseFactory factory) {
        super(StatusRequest.class, next, mailboxManager, factory);
    }

    /**
     * @see
     * org.apache.james.imap.processor.AbstractMailboxProcessor
     * #doProcess(org.apache.james.imap.api.message.request.ImapRequest,
     * org.apache.james.imap.api.process.ImapSession, java.lang.String,
     * org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.api.process.ImapProcessor.Responder)
     */
    protected void doProcess(StatusRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final MailboxPath mailboxPath = buildFullPath(session, request.getMailboxName());
        final StatusDataItems statusDataItems = request.getStatusDataItems();
        final Logger logger = session.getLog();
        final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);

        try {
            if (logger != null && logger.isDebugEnabled()) {
                logger.debug("Status called on mailbox named " + mailboxPath);
            }

            final MailboxManager mailboxManager = getMailboxManager();
            final MessageManager mailbox = mailboxManager.getMailbox(mailboxPath, ImapSessionUtils.getMailboxSession(session));
            final MessageManager.MetaData.FetchGroup fetchGroup;
            if (statusDataItems.isUnseen()) {
                fetchGroup = MessageManager.MetaData.FetchGroup.UNSEEN_COUNT;
            } else {
                fetchGroup = MessageManager.MetaData.FetchGroup.NO_UNSEEN;
            }
            final MessageManager.MetaData metaData = mailbox.getMetaData(false, mailboxSession, fetchGroup);

            final Long messages = messages(statusDataItems, metaData);
            final Long recent = recent(statusDataItems, metaData);
            final Long uidNext = uidNext(statusDataItems, metaData);
            final Long uidValidity = uidValidity(statusDataItems, metaData);
            final Long unseen = unseen(statusDataItems, metaData);
            final Long highestModSeq = highestModSeq(statusDataItems, metaData);
            
            // Enable CONDSTORE as this is a CONDSTORE enabling command
            if (highestModSeq != null) {
                condstoreEnablingCommand(session, responder, metaData, false); 
            }
            final MailboxStatusResponse response = new MailboxStatusResponse(messages, recent, uidNext, highestModSeq, uidValidity, unseen, request.getMailboxName());
            responder.respond(response);
            unsolicitedResponses(session, responder, false);
            okComplete(command, tag, responder);

        } catch (MailboxException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("Status failed for mailbox " + mailboxPath, e);
            }
            no(command, tag, responder, HumanReadableText.SEARCH_FAILED);
        }
    }

    private Long unseen(final StatusDataItems statusDataItems, final MessageManager.MetaData metaData) throws MailboxException {
        final Long unseen;
        if (statusDataItems.isUnseen()) {
            unseen = metaData.getUnseenCount();
        } else {
            unseen = null;
        }
        return unseen;
    }

    private Long uidValidity(final StatusDataItems statusDataItems, final MessageManager.MetaData metaData) throws MailboxException {
        final Long uidValidity;
        if (statusDataItems.isUidValidity()) {
            final long uidValidityValue = metaData.getUidValidity();
            uidValidity = uidValidityValue;
        } else {
            uidValidity = null;
        }
        return uidValidity;
    }


    private Long highestModSeq(final StatusDataItems statusDataItems, final MessageManager.MetaData metaData) throws MailboxException {
        final Long highestModSeq;
        if (statusDataItems.isHighestModSeq()) {
            highestModSeq = metaData.getHighestModSeq();
        } else {
            highestModSeq = null;
        }
        return highestModSeq;
    }

    
    private Long uidNext(final StatusDataItems statusDataItems, final MessageManager.MetaData metaData) throws MailboxException {
        final Long uidNext;
        if (statusDataItems.isUidNext()) {
            final long uidNextValue = metaData.getUidNext();
            uidNext = uidNextValue;
        } else {
            uidNext = null;
        }
        return uidNext;
    }

    private Long recent(final StatusDataItems statusDataItems, final MessageManager.MetaData metaData) throws MailboxException {
        final Long recent;
        if (statusDataItems.isRecent()) {
            recent = metaData.countRecent();
        } else {
            recent = null;
        }
        return recent;
    }

    private Long messages(final StatusDataItems statusDataItems, final MessageManager.MetaData metaData) throws MailboxException {
        final Long messages;
        if (statusDataItems.isMessages()) {
            messages = metaData.getMessageCount();
        } else {
            messages = null;
        }
        return messages;
    }
}
