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

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.mail.Flags;

import org.apache.commons.io.IOUtils;
import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.AppendRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.MetaData.FetchGroup;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppendProcessor extends AbstractMailboxProcessor<AppendRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppendProcessor.class);

    public AppendProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory statusResponseFactory,
            MetricFactory metricFactory) {
        super(AppendRequest.class, next, mailboxManager, statusResponseFactory, metricFactory);
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
        final MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(mailboxName);

        try {

            final MailboxManager mailboxManager = getMailboxManager();
            final MessageManager mailbox = mailboxManager.getMailbox(mailboxPath, ImapSessionUtils.getMailboxSession(session));
            appendToMailbox(messageIn, datetime, flags, session, tag, command, mailbox, responder, mailboxPath);
        } catch (MailboxNotFoundException e) {
            // consume message on exception
            consume(messageIn);

            LOGGER.debug("Append failed for mailbox {}", mailboxPath, e);
            
            // Indicates that the mailbox does not exist
            // So TRY CREATE
            tryCreate(session, tag, command, responder, e);

        } catch (MailboxException e) {
            // consume message on exception
            consume(messageIn);
            
            LOGGER.error("Append failed for mailbox {}", mailboxPath, e);
            
            // Some other issue
            no(command, tag, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);

        }

    }

    private void consume(InputStream in) {
        try {
            // IOUtils.copy() buffers the input internally, so there is no need
            // to use a BufferedInputStream.
            IOUtils.copy(in, NULL_OUTPUT_STREAM);
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
        LOGGER.debug("Cannot open mailbox: ", e);

        no(command, tag, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX, StatusResponse.ResponseCode.tryCreate());
    }

    private void appendToMailbox(InputStream message, Date datetime, Flags flagsToBeSet, ImapSession session, String tag, ImapCommand command, MessageManager mailbox, Responder responder, MailboxPath mailboxPath) {
        try {
            final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);
            final SelectedMailbox selectedMailbox = session.getSelected();
            final MailboxManager mailboxManager = getMailboxManager();
            final boolean isSelectedMailbox = selectedMailbox != null && selectedMailbox.getPath().equals(mailboxPath);
            final ComposedMessageId messageId = mailbox.appendMessage(message, datetime, mailboxSession, !isSelectedMailbox, flagsToBeSet);
            if (isSelectedMailbox) {
                selectedMailbox.addRecent(messageId.getUid());
            }

            // get folder UIDVALIDITY
            Long uidValidity = mailboxManager.getMailbox(mailboxPath, mailboxSession).getMetaData(false, mailboxSession, FetchGroup.NO_COUNT).getUidValidity();

            unsolicitedResponses(session, responder, false);

            // in case of MULTIAPPEND support we will push more then one UID here
            okComplete(command, tag, ResponseCode.appendUid(uidValidity, new UidRange[] { new UidRange(messageId.getUid()) }), responder);
        } catch (MailboxNotFoundException e) {
            // Indicates that the mailbox does not exist
            // So TRY CREATE
            tryCreate(session, tag, command, responder, e);
        } catch (MailboxException e) {
            LOGGER.error("Unable to append message to mailbox {}", mailboxPath, e);
            // Some other issue
            no(command, tag, responder, HumanReadableText.SAVE_FAILED);
        }
    }

    @Override
    protected Closeable addContextToMDC(AppendRequest message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "APPEND")
            .addContext("mailbox", message.getMailboxName())
            .build();
    }
}
