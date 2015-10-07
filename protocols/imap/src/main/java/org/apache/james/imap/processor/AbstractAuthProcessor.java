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
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;

public abstract class AbstractAuthProcessor<M extends ImapRequest> extends AbstractMailboxProcessor<M>{

    private static final String ATTRIBUTE_NUMBER_OF_FAILURES = "org.apache.james.imap.processor.imap4rev1.NUMBER_OF_FAILURES";

    // TODO: this should be configurable
    private static final int MAX_FAILURES = 3;
    
    public AbstractAuthProcessor(Class<M> acceptableClass, ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory) {
        super(acceptableClass, next, mailboxManager, factory);
    }

    protected void doAuth(String userid, String passwd, ImapSession session, String tag, ImapCommand command, Responder responder, HumanReadableText failed) {
        try {
            boolean authFailure = false;
            if (userid == null) {
                authFailure = true;
            }
            if (authFailure == false) {
                final MailboxManager mailboxManager = getMailboxManager();
                try {
                    final MailboxSession mailboxSession = mailboxManager.login(userid, passwd, session.getLog());
                    session.authenticated();
                    session.setAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY, mailboxSession);
                    final MailboxPath inboxPath = buildFullPath(session, MailboxConstants.INBOX);
                    if (mailboxManager.mailboxExists(inboxPath, mailboxSession)) {
                        if (session.getLog().isDebugEnabled()) {
                            session.getLog().debug("INBOX exists. No need to create it.");
                        }
                    } else {
                        try {
                            session.getLog().debug("INBOX does not exist. Creating it.");
                            mailboxManager.createMailbox(inboxPath, mailboxSession);
                        } catch (MailboxExistsException e) {
                            if (session.getLog().isDebugEnabled()) {
                                session.getLog().debug("Mailbox created by concurrent call. Safe to ignore this exception.");
                            }
                        }
                    }
                    okComplete(command, tag, responder);
                } catch (BadCredentialsException e) {
                    authFailure = true;
                }
            }
            if (authFailure) {
                final Integer currentNumberOfFailures = (Integer) session.getAttribute(ATTRIBUTE_NUMBER_OF_FAILURES);
                final int failures;
                if (currentNumberOfFailures == null) {
                    failures = 1;
                } else {
                    failures = currentNumberOfFailures.intValue() + 1;
                }
                if (failures < MAX_FAILURES) {
                    session.setAttribute(ATTRIBUTE_NUMBER_OF_FAILURES, failures);
                    no(command, tag, responder, failed);
                } else {
                    if (session.getLog().isInfoEnabled()) {
                        session.getLog().info("Too many authentication failures. Closing connection.");
                    }
                    bye(responder, HumanReadableText.TOO_MANY_FAILURES);
                    session.logout();
                }
            }
        } catch (MailboxException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("Login failed", e);
            }
            no(command, tag, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }
}
