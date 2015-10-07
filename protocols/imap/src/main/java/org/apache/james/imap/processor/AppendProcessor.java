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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.AppendRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxPath;
import org.slf4j.Logger;

public class AppendProcessor extends AbstractMailboxProcessor<AppendRequest> {

    public AppendProcessor(final ImapProcessor next, final MailboxManager mailboxManager, final StatusResponseFactory statusResponseFactory) {
        super(AppendRequest.class, next, mailboxManager, statusResponseFactory);
    }

    /**
     * @see
     * org.apache.james.imap.processor.AbstractMailboxProcessor#doProcess(org.apache.james.imap.api.message.request.ImapRequest,
     * org.apache.james.imap.api.process.ImapSession, java.lang.String,
     * org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.api.process.ImapProcessor.Responder)
     */
    protected void doProcess(AppendRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final String mailboxName = request.getMailboxName();
        final InputStream messageIn = request.getMessage();
        final Date datetime = request.getDatetime();
        final Flags flags = request.getFlags();
        final MailboxPath mailboxPath = buildFullPath(session, mailboxName);

        try {

            final MailboxManager mailboxManager = getMailboxManager();
            final MessageManager mailbox = mailboxManager.getMailbox(mailboxPath, ImapSessionUtils.getMailboxSession(session));
            appendToMailbox(messageIn, datetime, flags, session, tag, command, mailbox, responder, mailboxPath);
        } catch (MailboxNotFoundException e) {
            // consume message on exception
            consume(messageIn);

            session.getLog().debug("Append failed for mailbox " + mailboxPath, e);
            
            // Indicates that the mailbox does not exist
            // So TRY CREATE
            tryCreate(session, tag, command, responder, e);

        } catch (MailboxException e) {
            // consume message on exception
            consume(messageIn);
            
            session.getLog().info("Append failed for mailbox " + mailboxPath, e);
            
            // Some other issue
            no(command, tag, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);

        }

    }

    private void consume(InputStream in) {
        try {
            while (in.read() != -1)
                ; // NOPMD false positive
        } catch (IOException e1) { // NOPMD false positive
            // just consume
        }
    }

    /**
     * Issues a TRY CREATE response.
     * 
     * @param session
     *            not null
     * @param tag
     *            not null
     * @param command
     *            not null
     * @param responder
     *            not null
     * @param e
     *            not null
     */
    private void tryCreate(ImapSession session, String tag, ImapCommand command, Responder responder, MailboxNotFoundException e) {

        final Logger logger = session.getLog();
        if (logger.isDebugEnabled()) {
            logger.debug("Cannot open mailbox: ", e);
        }

        no(command, tag, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX, StatusResponse.ResponseCode.tryCreate());
    }

    private void appendToMailbox(final InputStream message, final Date datetime, final Flags flagsToBeSet, final ImapSession session, final String tag, final ImapCommand command, final MessageManager mailbox, Responder responder, final MailboxPath mailboxPath) {
        try {
            final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);
            final SelectedMailbox selectedMailbox = session.getSelected();
            final MailboxManager mailboxManager = getMailboxManager();
            final boolean isSelectedMailbox = selectedMailbox != null && selectedMailbox.getPath().equals(mailboxPath);
            final long uid = mailbox.appendMessage(message, datetime, mailboxSession, !isSelectedMailbox, flagsToBeSet);
            if (isSelectedMailbox) {
                selectedMailbox.addRecent(uid);
            }

            // get folder UIDVALIDITY
            Long uidValidity = mailboxManager.getMailbox(mailboxPath, mailboxSession).getMetaData(false, mailboxSession, FetchGroup.NO_UNSEEN).getUidValidity();

            unsolicitedResponses(session, responder, false);

            // in case of MULTIAPPEND support we will push more then one UID
            // here
            okComplete(command, tag, ResponseCode.appendUid(uidValidity, new IdRange[] { new IdRange(uid) }), responder);
        } catch (MailboxNotFoundException e) {
            // Indicates that the mailbox does not exist
            // So TRY CREATE
            tryCreate(session, tag, command, responder, e);
            /*
             * } catch (StorageException e) { taggedBad(command, tag, responder,
             * e.getKey());
             */
        } catch (MailboxException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("Unable to append message to mailbox " + mailboxPath, e);
            }
            // Some other issue
            no(command, tag, responder, HumanReadableText.SAVE_FAILED);
        }
    }

}
