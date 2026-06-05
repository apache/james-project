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
import org.apache.james.jwt.OidcJwtTokenVerifier;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.ForbiddenDelegationException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UserDoesNotExistException;
import org.apache.james.protocols.api.sasl.BearerTokenSaslAuthenticationService;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImapBearerTokenSaslAuthenticationService implements BearerTokenSaslAuthenticationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImapBearerTokenSaslAuthenticationService.class);

    private final MailboxManager mailboxManager;
    private final ImapSaslSessionContext context;

    public ImapBearerTokenSaslAuthenticationService(MailboxManager mailboxManager, ImapSaslSessionContext context) {
        this.mailboxManager = mailboxManager;
        this.context = context;
    }

    @Override
    public SaslAuthenticationResult authenticate(String token, Username authorizationId) {
        return context.session().oidcSaslConfiguration()
            .flatMap(configuration -> new OidcJwtTokenVerifier(configuration).validateToken(token))
            .map(authenticationId -> authenticateOidcUser(authenticationId, authorizationId))
            .orElseGet(() -> {
                String reason = "OAuth authentication failed.";
                context.recordFailureDetails(new ImapSaslSessionContext.FailureDetails(HumanReadableText.AUTHENTICATION_FAILED,
                    Optional.empty(), Optional.of(authorizationId), reason));
                return new SaslAuthenticationResult.Failure(reason);
            });
    }

    private SaslAuthenticationResult authenticateOidcUser(Username authenticationId, Username authorizationId) {
        if (!authorizationId.equals(authenticationId)) {
            return authenticateOidcDelegation(authenticationId, authorizationId);
        }

        context.authenticationSucceeded(mailboxManager.createSystemSession(authenticationId));
        return new SaslAuthenticationResult.Success(new SaslIdentity(authenticationId, authorizationId));
    }

    private SaslAuthenticationResult authenticateOidcDelegation(Username authenticationId, Username authorizationId) {
        try {
            MailboxSession mailboxSession = mailboxManager
                .withExtraAuthorizator(context.delegationAuthorizator())
                .authenticate(authenticationId)
                .as(authorizationId);
            context.authenticationSucceeded(mailboxSession);
            return new SaslAuthenticationResult.Success(new SaslIdentity(authenticationId, authorizationId));
        } catch (BadCredentialsException e) {
            String reason = "Password authentication with delegation failed because of bad credentials.";
            context.recordFailureDetails(new ImapSaslSessionContext.FailureDetails(HumanReadableText.INVALID_CREDENTIALS,
                Optional.of(authenticationId), Optional.of(authorizationId), reason));
            return new SaslAuthenticationResult.Failure(reason);
        } catch (UserDoesNotExistException e) {
            String reason = "Delegation target user does not exist.";
            context.recordFailureDetails(new ImapSaslSessionContext.FailureDetails(HumanReadableText.USER_DOES_NOT_EXIST,
                Optional.of(authenticationId), Optional.of(authorizationId), reason));
            return new SaslAuthenticationResult.Failure(reason);
        } catch (ForbiddenDelegationException e) {
            String reason = "Requested delegation is forbidden.";
            context.recordFailureDetails(new ImapSaslSessionContext.FailureDetails(HumanReadableText.DELEGATION_FORBIDDEN,
                Optional.of(authenticationId), Optional.of(authorizationId), reason));
            return new SaslAuthenticationResult.Failure(reason);
        } catch (MailboxException e) {
            LOGGER.info("Authentication failed", e);
            context.processingFailed();
            return new SaslAuthenticationResult.Failure("Authentication failed.");
        }
    }
}
