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

import java.util.Optional;

import org.apache.james.core.Username;
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
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.OptionalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public abstract class AbstractAuthProcessor<R extends ImapRequest> extends AbstractMailboxProcessor<R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAuthProcessor.class);

    private static final String ATTRIBUTE_NUMBER_OF_FAILURES = "org.apache.james.imap.processor.imap4rev1.NUMBER_OF_FAILURES";

    // TODO: this should be configurable
    private static final int MAX_FAILURES = 3;
    
    public AbstractAuthProcessor(Class<R> acceptableClass, ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
                                 MetricFactory metricFactory) {
        super(acceptableClass, next, mailboxManager, factory, metricFactory);
    }

    protected void doAuth(AuthenticationAttempt authenticationAttempt, ImapSession session, ImapRequest request, Responder responder, HumanReadableText failed) {
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
                    session.setMailboxSession(mailboxSession);
                    provisionInbox(session, mailboxManager, mailboxSession);
                    okComplete(request, responder);
                } catch (BadCredentialsException e) {
                    authFailure = true;
                }
            }
            if (authFailure) {
                manageFailureCount(session, request, responder, failed);
            }
        } catch (MailboxException e) {
            LOGGER.error("Error encountered while login", e);
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    protected void doAuthWithDelegation(AuthenticationAttempt authenticationAttempt, ImapSession session, ImapRequest request, Responder responder, HumanReadableText failed) {
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
                    session.setMailboxSession(mailboxSession);
                    provisionInbox(session, mailboxManager, mailboxSession);
                    okComplete(request, responder);
                } catch (BadCredentialsException e) {
                    authFailure = true;
                }
            }
            if (authFailure) {
                manageFailureCount(session, request, responder, failed);
            }
        } catch (UserDoesNotExistException e) {
            LOGGER.info("User {} does not exist", authenticationAttempt.getAuthenticationId(), e);
            no(request, responder, HumanReadableText.USER_DOES_NOT_EXIST);
        } catch (NotAdminException e) {
            LOGGER.info("User {} is not an admin", authenticationAttempt.getDelegateUserName(), e);
            no(request, responder, HumanReadableText.NOT_AN_ADMIN);
        } catch (MailboxException e) {
            LOGGER.info("Login failed", e);
            no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
        }
    }

    private void provisionInbox(ImapSession session, MailboxManager mailboxManager, MailboxSession mailboxSession) throws MailboxException {
        final MailboxPath inboxPath = PathConverter.forSession(session).buildFullPath(MailboxConstants.INBOX);
        if (Mono.from(mailboxManager.mailboxExists(inboxPath, mailboxSession)).block()) {
            LOGGER.debug("INBOX exists. No need to create it.");
        } else {
            try {
                Optional<MailboxId> mailboxId = mailboxManager.createMailbox(inboxPath, mailboxSession);
                OptionalUtils.executeIfEmpty(mailboxId, () -> LOGGER.warn("Provisioning INBOX successful. But no MailboxId have been returned."))
                    .ifPresent(id -> LOGGER.info("Provisioning INBOX. {} created.", id));
            } catch (MailboxExistsException e) {
                LOGGER.warn("Mailbox INBOX created by concurrent call. Safe to ignore this exception.");
            }
        }
    }

    protected void manageFailureCount(ImapSession session, ImapRequest request, Responder responder, HumanReadableText failed) {
        final Integer currentNumberOfFailures = (Integer) session.getAttribute(ATTRIBUTE_NUMBER_OF_FAILURES);
        final int failures;
        if (currentNumberOfFailures == null) {
            failures = 1;
        } else {
            failures = currentNumberOfFailures + 1;
        }
        if (failures < MAX_FAILURES) {
            session.setAttribute(ATTRIBUTE_NUMBER_OF_FAILURES, failures);
            no(request, responder, failed);
        } else {
            LOGGER.info("Too many authentication failures. Closing connection.");
            bye(responder, HumanReadableText.TOO_MANY_FAILURES);
            session.logout();
        }
    }

    protected static AuthenticationAttempt delegation(Username authorizeId, Username authenticationId, String password) {
        return new AuthenticationAttempt(Optional.of(authorizeId), authenticationId, password);
    }

    protected static AuthenticationAttempt noDelegation(Username authenticationId, String password) {
        return new AuthenticationAttempt(Optional.empty(), authenticationId, password);
    }

    protected static class AuthenticationAttempt {
        private final Optional<Username> delegateUserName;
        private final Username authenticationId;
        private final String password;

        public AuthenticationAttempt(Optional<Username> delegateUserName, Username authenticationId, String password) {
            this.delegateUserName = delegateUserName;
            this.authenticationId = authenticationId;
            this.password = password;
        }

        public boolean isDelegation() {
            return delegateUserName.isPresent() && !delegateUserName.get().equals(authenticationId);
        }

        public Optional<Username> getDelegateUserName() {
            return delegateUserName;
        }

        public Username getAuthenticationId() {
            return authenticationId;
        }

        public String getPassword() {
            return password;
        }
    }
}
