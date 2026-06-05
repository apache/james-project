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

package org.apache.james.imap.processor.sasl;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.ForbiddenDelegationException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UserDoesNotExistException;
import org.apache.james.protocols.api.sasl.PasswordSaslAuthenticationService;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImapPasswordSaslAuthenticationService implements PasswordSaslAuthenticationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImapPasswordSaslAuthenticationService.class);

    private final MailboxManager mailboxManager;
    private final ImapSaslSessionContext context;

    public ImapPasswordSaslAuthenticationService(MailboxManager mailboxManager, ImapSaslSessionContext context) {
        this.mailboxManager = mailboxManager;
        this.context = context;
    }

    @Override
    public SaslAuthenticationResult authenticate(Username authenticationId, Optional<Username> authorizationId, String password) {
        Username authorizedUser = authorizationId.orElse(authenticationId);
        if (authorizedUser.equals(authenticationId)) {
            return authenticateWithoutDelegation(authenticationId, password);
        }
        return authenticateWithDelegation(authenticationId, password, authorizedUser);
    }

    private SaslAuthenticationResult authenticateWithoutDelegation(Username authenticationId, String password) {
        try {
            MailboxSession mailboxSession = mailboxManager
                .authenticate(authenticationId, password)
                .withoutDelegation();
            context.authenticationSucceeded(mailboxSession);
            return new SaslAuthenticationResult.Success(new SaslIdentity(authenticationId, authenticationId));
        } catch (BadCredentialsException e) {
            String reason = "Password authentication failed because of bad credentials.";
            context.recordFailureDetails(new ImapSaslSessionContext.FailureDetails(HumanReadableText.INVALID_CREDENTIALS,
                Optional.of(authenticationId), Optional.empty(), reason));
            return new SaslAuthenticationResult.Failure(reason);
        } catch (MailboxException e) {
            LOGGER.error("Authentication failed", e);
            context.processingFailed();
            return new SaslAuthenticationResult.Failure("Authentication failed.");
        }
    }

    private SaslAuthenticationResult authenticateWithDelegation(Username authenticationId, String password, Username authorizedUser) {
        try {
            MailboxSession mailboxSession = mailboxManager
                .withExtraAuthorizator(context.delegationAuthorizator())
                .authenticate(authenticationId, password)
                .as(authorizedUser);
            context.authenticationSucceeded(mailboxSession);
            return new SaslAuthenticationResult.Success(new SaslIdentity(authenticationId, authorizedUser));
        } catch (BadCredentialsException e) {
            String reason = "Password authentication with delegation failed because of bad credentials.";
            context.recordFailureDetails(new ImapSaslSessionContext.FailureDetails(HumanReadableText.INVALID_CREDENTIALS,
                Optional.of(authenticationId), Optional.of(authorizedUser), reason));
            return new SaslAuthenticationResult.Failure(reason);
        } catch (UserDoesNotExistException e) {
            String reason = "Delegation target user does not exist.";
            context.recordFailureDetails(new ImapSaslSessionContext.FailureDetails(HumanReadableText.USER_DOES_NOT_EXIST,
                Optional.of(authenticationId), Optional.of(authorizedUser), reason));
            return new SaslAuthenticationResult.Failure(reason);
        } catch (ForbiddenDelegationException e) {
            String reason = "Requested delegation is forbidden.";
            context.recordFailureDetails(new ImapSaslSessionContext.FailureDetails(HumanReadableText.DELEGATION_FORBIDDEN,
                Optional.of(authenticationId), Optional.of(authorizedUser), reason));
            return new SaslAuthenticationResult.Failure(reason);
        } catch (MailboxException e) {
            LOGGER.info("Authentication failed", e);
            context.processingFailed();
            return new SaslAuthenticationResult.Failure("Authentication failed.");
        }
    }
}
