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
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.CreateRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.model.MailboxPath;

public class CreateProcessor extends AbstractMailboxProcessor<CreateRequest> {

    public CreateProcessor(final ImapProcessor next, final MailboxManager mailboxManager, final StatusResponseFactory factory) {
        super(CreateRequest.class, next, mailboxManager, factory);
    }

    /**
     * @see
     * org.apache.james.imap.processor.AbstractMailboxProcessor#doProcess(org.apache.james.imap.api.message.request.ImapRequest,
     * org.apache.james.imap.api.process.ImapSession, java.lang.String,
     * org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.api.process.ImapProcessor.Responder)
     */
    protected void doProcess(CreateRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final MailboxPath mailboxPath = buildFullPath(session, request.getMailboxName());
        try {
            final MailboxManager mailboxManager = getMailboxManager();
            mailboxManager.createMailbox(mailboxPath, ImapSessionUtils.getMailboxSession(session));
            unsolicitedResponses(session, responder, false);
            okComplete(command, tag, responder);
        } catch (MailboxExistsException e) {
            if (session.getLog().isDebugEnabled()) {
                session.getLog().debug("Create failed for mailbox " + mailboxPath + " as it already exists", e);
            }
            no(command, tag, responder, HumanReadableText.MAILBOX_EXISTS);
        } catch (MailboxException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("Create failed for mailbox " + mailboxPath, e);
            }
            no(command, tag, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }
}
