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

import java.io.Closeable;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.StatusDataItems;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.StatusRequest;
import org.apache.james.imap.message.response.MailboxStatusResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatusProcessor extends AbstractMailboxProcessor<StatusRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusProcessor.class);

    public StatusProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(StatusRequest.class, next, mailboxManager, factory, metricFactory);
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
        final MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(request.getMailboxName());
        final StatusDataItems statusDataItems = request.getStatusDataItems();
        final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);

        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Status called on mailbox named " + mailboxPath);
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
            final MessageUid uidNext = uidNext(statusDataItems, metaData);
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
            LOGGER.error("Status failed for mailbox " + mailboxPath, e);
            no(command, tag, responder, HumanReadableText.SEARCH_FAILED);
        }
    }

    private Long unseen(StatusDataItems statusDataItems, MessageManager.MetaData metaData) throws MailboxException {
        final Long unseen;
        if (statusDataItems.isUnseen()) {
            unseen = metaData.getUnseenCount();
        } else {
            unseen = null;
        }
        return unseen;
    }

    private Long uidValidity(StatusDataItems statusDataItems, MessageManager.MetaData metaData) throws MailboxException {
        final Long uidValidity;
        if (statusDataItems.isUidValidity()) {
            uidValidity = metaData.getUidValidity();
        } else {
            uidValidity = null;
        }
        return uidValidity;
    }


    private Long highestModSeq(StatusDataItems statusDataItems, MessageManager.MetaData metaData) throws MailboxException {
        final Long highestModSeq;
        if (statusDataItems.isHighestModSeq()) {
            highestModSeq = metaData.getHighestModSeq();
        } else {
            highestModSeq = null;
        }
        return highestModSeq;
    }

    
    private MessageUid uidNext(StatusDataItems statusDataItems, MessageManager.MetaData metaData) throws MailboxException {
        final MessageUid uidNext;
        if (statusDataItems.isUidNext()) {
            uidNext = metaData.getUidNext();
        } else {
            uidNext = null;
        }
        return uidNext;
    }

    private Long recent(StatusDataItems statusDataItems, MessageManager.MetaData metaData) throws MailboxException {
        final Long recent;
        if (statusDataItems.isRecent()) {
            recent = metaData.countRecent();
        } else {
            recent = null;
        }
        return recent;
    }

    private Long messages(StatusDataItems statusDataItems, MessageManager.MetaData metaData) throws MailboxException {
        final Long messages;
        if (statusDataItems.isMessages()) {
            messages = metaData.getMessageCount();
        } else {
            messages = null;
        }
        return messages;
    }

    @Override
    protected Closeable addContextToMDC(StatusRequest message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "STATUS")
            .addContext("mailbox", message.getMailboxName())
            .addContext("parameters", message.getStatusDataItems())
            .build();
    }
}
