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
package org.apache.james.smtpserver;

import java.util.Optional;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPConfiguration;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.AbstractSenderAuthIdentifyVerificationHook;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.StreamUtils;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InternetDomainName;

/**
 * Handler which check if the authenticated user is incorrect
 */
public class SenderAuthIdentifyVerificationHook extends AbstractSenderAuthIdentifyVerificationHook implements JamesMessageHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(SenderAuthIdentifyVerificationHook.class);

    private final DomainList domains;
    private final UsersRepository users;
    private final CanSendFrom canSendFrom;

    @Inject
    public SenderAuthIdentifyVerificationHook(DomainList domains, UsersRepository users, CanSendFrom canSendFrom) {
        this.domains = domains;
        this.users = users;
        this.canSendFrom = canSendFrom;
    }

    @Override
    public HookResult doCheck(SMTPSession session, MaybeSender sender) {
        ExtendedSMTPSession nSession = (ExtendedSMTPSession) session;
        if (nSession.verifyIdentity() == SMTPConfiguration.SenderVerificationMode.STRICT) {
            return super.doCheck(session, sender);
        } else if (nSession.verifyIdentity() == SMTPConfiguration.SenderVerificationMode.RELAXED) {
            return doCheckRelaxed(session, sender);
        } else {
            return HookResult.DECLINED;
        }
    }

    private HookResult doCheckRelaxed(SMTPSession session, MaybeSender sender) {
        if (senderDoesNotMatchAuthUser(session, sender)) {
            return INVALID_AUTH;
        } else if (unauthenticatedSenderIsLocalUser(session, sender)) {
            if (mxHeuristic(session)) {
                return HookResult.DECLINED;
            } else {
                return AUTH_REQUIRED;
            }
        } else {
            return HookResult.DECLINED;
        }
    }

    private boolean mxHeuristic(SMTPSession session) {
        Optional<String> helo = session.getAttachment(SMTPSession.CURRENT_HELO_NAME, ProtocolSession.State.Connection);

        return helo.filter(InternetDomainName::isValid)
            .map(name -> name.contains("."))
            .orElse(false);
    }

    @Override
    protected boolean isLocalDomain(Domain domain) {
        try {
            return domains.containsDomain(domain);
        } catch (DomainListException e) {
            return false;
        }
    }

    @Override
    protected Username getUser(MailAddress mailAddress) {
        try {
            return users.getUsername(mailAddress);
        } catch (UsersRepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean isSenderAllowed(Username connectedUser, Username sender) {
        boolean allowed = canSendFrom.userCanSendFrom(connectedUser, sender);
        if (allowed) {
            LOGGER.debug("{} is allowed to send a mail using {} identity", connectedUser.asString(), sender.asString());
        } else {
            LOGGER.info("{} is not allowed to send a mail using {} identity", connectedUser.asString(), sender.asString());
        }
        return allowed;
    }

    @Override
    public HookResult onMessage(SMTPSession session, Mail mail) {
        ExtendedSMTPSession nSession = (ExtendedSMTPSession) session;
        boolean shouldCheck = nSession.verifyIdentity() == SMTPConfiguration.SenderVerificationMode.STRICT ||
            (nSession.verifyIdentity() == SMTPConfiguration.SenderVerificationMode.RELAXED && session.getUsername() != null);
        if (shouldCheck) {
            try {
                return StreamUtils.ofNullable(mail.getMessage().getFrom())
                    .distinct()
                    .flatMap(address -> doCheckMessage(session, address))
                    .findFirst()
                    .orElse(HookResult.DECLINED);
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        } else {
            return HookResult.DECLINED;
        }
    }

    private Stream<HookResult> doCheckMessage(SMTPSession session, Address from) {
        if (fromDoesNotMatchAuthUser(session, from)) {
            return Stream.of(INVALID_AUTH);
        } else {
            return Stream.empty();
        }
    }

    private boolean fromDoesNotMatchAuthUser(SMTPSession session, Address from) {
        if (from instanceof InternetAddress internetAddress) {
            try {
                MailAddress mailAddress = new MailAddress(internetAddress.getAddress());
                return session.getUsername() != null &&
                    (!fromMatchSessionUser(mailAddress, session) || !belongsToLocalDomain(mailAddress));
            } catch (AddressException e) {
                // Never happens as valid InternetAddress are valid MailAddress
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    private boolean fromMatchSessionUser(MailAddress from, SMTPSession session) {
        Username authUser = session.getUsername();
        Username sender = getUser(from);
        return isSenderAllowed(authUser, sender);
    }
    
    private boolean belongsToLocalDomain(MailAddress from) {
        return isLocalDomain(from.getDomain());
    }
}
