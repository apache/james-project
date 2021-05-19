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
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.DeleteRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteProcessor extends AbstractMailboxProcessor<DeleteRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteProcessor.class);

    public DeleteProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(DeleteRequest.class, next, mailboxManager, factory, metricFactory);
    }

    @Override
    protected void processRequest(DeleteRequest request, ImapSession session, Responder responder) {
        final MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(request.getMailboxName());
        try {
            final SelectedMailbox selected = session.getSelected();
            if (selected != null && selected.getPath().equals(mailboxPath)) {
                session.deselect();
            }
            final MailboxManager mailboxManager = getMailboxManager();
            mailboxManager.deleteMailbox(mailboxPath, session.getMailboxSession());
            unsolicitedResponses(session, responder, false);
            okComplete(request, responder);
        } catch (MailboxNotFoundException e) {
            LOGGER.debug("Delete failed for mailbox {} as it doesn't exist", mailboxPath, e);
            no(request, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX);
        } catch (TooLongMailboxNameException e) {
            LOGGER.debug("The mailbox name length is over limit: {}", mailboxPath.getName(), e);
            taggedBad(request, responder, HumanReadableText.FAILURE_MAILBOX_NAME);
        } catch (MailboxException e) {
            LOGGER.error("Delete failed for mailbox {}", mailboxPath, e);
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    @Override
    protected Closeable addContextToMDC(DeleteRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "DELETE")
            .addToContext("mailbox", request.getMailboxName())
            .build();
    }
}
