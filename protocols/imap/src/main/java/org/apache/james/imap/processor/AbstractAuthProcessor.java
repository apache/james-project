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
import org.apache.james.imap.main.PathConverter;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.NotAdminException;
import org.apache.james.mailbox.exception.UserDoesNotExistException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public abstract class AbstractAuthProcessor<M extends ImapRequest> extends AbstractMailboxProcessor<M>{

    private static final String ATTRIBUTE_NUMBER_OF_FAILURES = "org.apache.james.imap.processor.imap4rev1.NUMBER_OF_FAILURES";

    // TODO: this should be configurable
    private static final int MAX_FAILURES = 3;
    
    public AbstractAuthProcessor(Class<M> acceptableClass, ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(acceptableClass, next, mailboxManager, factory, metricFactory);
    }

    protected void doAuth(AuthenticationAttempt authenticationAttempt, ImapSession session, String tag, ImapCommand command, Responder responder, HumanReadableText failed) {
        Preconditions.checkArgument(!authenticationAttempt.isDelegation());
        try {
            boolean authFailure = false;
            if (authenticationAttempt.getAuthenticationId() == null) {
                authFailure = true;
            }
            if (!authFailure) {
                final MailboxManager mailboxManager = getMailboxManager();
                try {
                    final MailboxSession mailboxSession = mailboxManager.login(authenticationAttempt.getAuthenticationId(),
                        authenticationAttempt.getPassword());
                    session.authenticated();
                    session.setAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY, mailboxSession);
                    provisionInbox(session, mailboxManager, mailboxSession);
                    okComplete(command, tag, responder);
                } catch (BadCredentialsException e) {
                    authFailure = true;
                }
            }
            if (authFailure) {
                manageFailureCount(session, tag, command, responder, failed);
            }
        } catch (MailboxException e) {
            session.getLog().error("Error encountered while login", e);
            no(command, tag, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    protected void doAuthWithDelegation(AuthenticationAttempt authenticationAttempt, ImapSession session, String tag, ImapCommand command, Responder responder, HumanReadableText failed) {
        Preconditions.checkArgument(authenticationAttempt.isDelegation());
        try {
            boolean authFailure = false;
            if (authenticationAttempt.getAuthenticationId() == null) {
                authFailure = true;
            }
            if (!authFailure) {
                final MailboxManager mailboxManager = getMailboxManager();
                try {
                    final MailboxSession mailboxSession = mailboxManager.loginAsOtherUser(authenticationAttempt.getAuthenticationId(),
                        authenticationAttempt.getPassword(),
                        authenticationAttempt.getDelegateUserName().get());
                    session.authenticated();
                    session.setAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY, mailboxSession);
                    provisionInbox(session, mailboxManager, mailboxSession);
                    okComplete(command, tag, responder);
                } catch (BadCredentialsException e) {
                    authFailure = true;
                }
            }
            if (authFailure) {
                manageFailureCount(session, tag, command, responder, failed);
            }
        } catch (UserDoesNotExistException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("User " + authenticationAttempt.getAuthenticationId() + " does not exist", e);
            }
            no(command, tag, responder, HumanReadableText.USER_DOES_NOT_EXIST);
        } catch (NotAdminException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("User " + authenticationAttempt.getDelegateUserName() + " is not an admin", e);
            }
            no(command, tag, responder, HumanReadableText.NOT_AN_ADMIN);
        } catch (MailboxException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("Login failed", e);
            }
            no(command, tag, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    private void provisionInbox(ImapSession session, MailboxManager mailboxManager, MailboxSession mailboxSession) throws MailboxException {
        final MailboxPath inboxPath = PathConverter.forSession(session).buildFullPath(MailboxConstants.INBOX);
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
    }

    protected void manageFailureCount(ImapSession session, String tag, ImapCommand command, Responder responder, HumanReadableText failed) {
        final Integer currentNumberOfFailures = (Integer) session.getAttribute(ATTRIBUTE_NUMBER_OF_FAILURES);
        final int failures;
        if (currentNumberOfFailures == null) {
            failures = 1;
        } else {
            failures = currentNumberOfFailures + 1;
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

    protected static AuthenticationAttempt delegation(String authorizeId, String authenticationId, String password) {
        return new AuthenticationAttempt(Optional.of(authorizeId), authenticationId, password);
    }

    protected static AuthenticationAttempt noDelegation(String authenticationId, String password) {
        return new AuthenticationAttempt(Optional.<String>absent(), authenticationId, password);
    }

    protected static class AuthenticationAttempt {
        private final Optional<String> delegateUserName;
        private final String authenticationId;
        private final String password;

        public AuthenticationAttempt(Optional<String> delegateUserName, String authenticationId, String password) {
            this.delegateUserName = delegateUserName;
            this.authenticationId = authenticationId;
            this.password = password;
        }

        public boolean isDelegation() {
            return delegateUserName.isPresent() && !delegateUserName.get().equals(authenticationId);
        }

        public Optional<String> getDelegateUserName() {
            return delegateUserName;
        }

        public String getAuthenticationId() {
            return authenticationId;
        }

        public String getPassword() {
            return password;
        }
    }
}
