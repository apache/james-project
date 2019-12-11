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

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.RenameRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenameProcessor extends AbstractMailboxProcessor<RenameRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenameProcessor.class);

    public RenameProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(RenameRequest.class, next, mailboxManager, factory, metricFactory);
    }

    @Override
    protected void processRequest(RenameRequest request, ImapSession session, Responder responder) {
        PathConverter pathConverter = PathConverter.forSession(session);
        MailboxPath existingPath = pathConverter.buildFullPath(request.getExistingName());
        MailboxPath newPath = pathConverter.buildFullPath(request.getNewName());
        try {
            final MailboxManager mailboxManager = getMailboxManager();
            MailboxSession mailboxsession = session.getMailboxSession();
            mailboxManager.renameMailbox(existingPath, newPath, mailboxsession);

            if (existingPath.getName().equalsIgnoreCase(ImapConstants.INBOX_NAME) && !mailboxManager.mailboxExists(existingPath, mailboxsession)) {
                mailboxManager.createMailbox(existingPath, mailboxsession);
            }
            okComplete(request, responder);
            unsolicitedResponses(session, responder, false);
        } catch (MailboxExistsException e) {
            LOGGER.debug("Rename from {} to {} failed because the target mailbox exists", existingPath, newPath, e);
            no(request, responder, HumanReadableText.FAILURE_MAILBOX_EXISTS);
        } catch (MailboxNotFoundException e) {
            LOGGER.debug("Rename from {} to {} failed because the source mailbox doesn't exist", existingPath, newPath, e);
            no(request, responder, HumanReadableText.MAILBOX_NOT_FOUND);
        } catch (TooLongMailboxNameException e) {
            LOGGER.debug("The mailbox name length is over limit: {}", newPath.getName(), e);
            taggedBad(request, responder, HumanReadableText.FAILURE_MAILBOX_NAME);
        } catch (MailboxException e) {
            LOGGER.error("Rename from {} to {} failed", existingPath, newPath, e);
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    @Override
    protected Closeable addContextToMDC(RenameRequest request) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "RENAME")
            .addContext("existingName", request.getExistingName())
            .addContext("newName", request.getNewName())
            .build();
    }
}
