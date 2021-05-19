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
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
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

    @Override
    protected void processRequest(StatusRequest request, ImapSession session, Responder responder) {
        MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(request.getMailboxName());
        StatusDataItems statusDataItems = request.getStatusDataItems();
        MailboxSession mailboxSession = session.getMailboxSession();

        try {
            LOGGER.debug("Status called on mailbox named {}", mailboxPath);

            MessageManager.MailboxMetaData metaData = retrieveMetadata(mailboxPath, statusDataItems, mailboxSession);
            MailboxStatusResponse response = computeStatusResponse(request, statusDataItems, metaData);

            // Enable CONDSTORE as this is a CONDSTORE enabling command
            if (response.getHighestModSeq() != null) {
                condstoreEnablingCommand(session, responder, metaData, false); 
            }
            responder.respond(response);
            unsolicitedResponses(session, responder, false);

            okComplete(request, responder);
        } catch (MailboxException e) {
            LOGGER.error("Status failed for mailbox {}", mailboxPath, e);
            no(request, responder, HumanReadableText.SEARCH_FAILED);
        }
    }

    private MessageManager.MailboxMetaData retrieveMetadata(MailboxPath mailboxPath, StatusDataItems statusDataItems, MailboxSession mailboxSession) throws MailboxException {
        MessageManager mailbox = getMailboxManager().getMailbox(mailboxPath, mailboxSession);
        MessageManager.MailboxMetaData.FetchGroup fetchGroup = computeFetchGroup(statusDataItems);
        return mailbox.getMetaData(false, mailboxSession, fetchGroup);
    }

    private MailboxStatusResponse computeStatusResponse(StatusRequest request, StatusDataItems statusDataItems, MessageManager.MailboxMetaData metaData) {
        Long messages = messages(statusDataItems, metaData);
        Long recent = recent(statusDataItems, metaData);
        MessageUid uidNext = uidNext(statusDataItems, metaData);
        UidValidity uidValidity = uidValidity(statusDataItems, metaData);
        Long unseen = unseen(statusDataItems, metaData);
        ModSeq highestModSeq = highestModSeq(statusDataItems, metaData);
        return new MailboxStatusResponse(messages, recent, uidNext, highestModSeq, uidValidity, unseen, request.getMailboxName());
    }

    private MessageManager.MailboxMetaData.FetchGroup computeFetchGroup(StatusDataItems statusDataItems) {
        if (statusDataItems.isUnseen()) {
            return MessageManager.MailboxMetaData.FetchGroup.UNSEEN_COUNT;
        } else {
            return MessageManager.MailboxMetaData.FetchGroup.NO_UNSEEN;
        }
    }

    private Long unseen(StatusDataItems statusDataItems, MessageManager.MailboxMetaData metaData) {
        if (statusDataItems.isUnseen()) {
            return metaData.getUnseenCount();
        } else {
            return null;
        }
    }

    private UidValidity uidValidity(StatusDataItems statusDataItems, MessageManager.MailboxMetaData metaData) {
        if (statusDataItems.isUidValidity()) {
            return metaData.getUidValidity();
        } else {
            return null;
        }
    }

    private ModSeq highestModSeq(StatusDataItems statusDataItems, MessageManager.MailboxMetaData metaData) {
        if (statusDataItems.isHighestModSeq()) {
            return metaData.getHighestModSeq();
        } else {
            return null;
        }
    }
    
    private MessageUid uidNext(StatusDataItems statusDataItems, MessageManager.MailboxMetaData metaData) {
        if (statusDataItems.isUidNext()) {
            return metaData.getUidNext();
        } else {
            return null;
        }
    }

    private Long recent(StatusDataItems statusDataItems, MessageManager.MailboxMetaData metaData) {
        if (statusDataItems.isRecent()) {
            return metaData.countRecent();
        } else {
            return null;
        }
    }

    private Long messages(StatusDataItems statusDataItems, MessageManager.MailboxMetaData metaData) {
        if (statusDataItems.isMessages()) {
           return metaData.getMessageCount();
        } else {
            return null;
        }
    }

    @Override
    protected Closeable addContextToMDC(StatusRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "STATUS")
            .addToContext("mailbox", request.getMailboxName())
            .addToContext("parameters", request.getStatusDataItems().toString())
            .build();
    }
}
