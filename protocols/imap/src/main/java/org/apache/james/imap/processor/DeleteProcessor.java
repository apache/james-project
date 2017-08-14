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

public class DeleteProcessor extends AbstractMailboxProcessor<DeleteRequest> {

    public DeleteProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(DeleteRequest.class, next, mailboxManager, factory, metricFactory);
    }

    /**
     * @see
     * org.apache.james.imap.processor.AbstractMailboxProcessor#doProcess(org.apache.james.imap.api.message.request.ImapRequest,
     * org.apache.james.imap.api.process.ImapSession, java.lang.String,
     * org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.api.process.ImapProcessor.Responder)
     */
    protected void doProcess(DeleteRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(request.getMailboxName());
        try {
            final SelectedMailbox selected = session.getSelected();
            if (selected != null && selected.getPath().equals(mailboxPath)) {
                session.deselect();
            }
            final MailboxManager mailboxManager = getMailboxManager();
            mailboxManager.deleteMailbox(mailboxPath, ImapSessionUtils.getMailboxSession(session));
            unsolicitedResponses(session, responder, false);
            okComplete(command, tag, responder);
        } catch (MailboxNotFoundException e) {
            if (session.getLog().isDebugEnabled()) {
                session.getLog().debug("Delete failed for mailbox " + mailboxPath + " as it not exist", e);
            }
            no(command, tag, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX);
        } catch (TooLongMailboxNameException e) {
            if (session.getLog().isDebugEnabled()) {
                session.getLog().debug("The mailbox name length is over limit: " + mailboxPath.getName(), e);
            }
            taggedBad(command, tag, responder, HumanReadableText.FAILURE_MAILBOX_NAME);
        } catch (MailboxException e) {
            session.getLog().error("Delete failed for mailbox " + mailboxPath, e);
            no(command, tag, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    @Override
    protected Closeable addContextToMDC(DeleteRequest message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "DELETE")
            .addContext("mailbox", message.getMailboxName())
            .build();
    }
}
