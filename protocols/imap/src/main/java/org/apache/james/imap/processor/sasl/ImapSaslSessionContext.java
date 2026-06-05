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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.protocols.api.sasl.SaslSessionContext;

public class ImapSaslSessionContext implements SaslSessionContext {
    public record FailureDetails(HumanReadableText text, Optional<Username> username, Optional<Username> assumedUser, String reason) {
    }

    private final ImapSession session;
    private final Authorizator delegationAuthorizator;
    private final Map<Class<?>, Object> services;
    private Optional<MailboxSession> mailboxSession;
    private Optional<FailureDetails> failureDetails;
    private boolean processingFailure;

    public ImapSaslSessionContext(ImapSession session) {
        this(session, (userId, otherUserId) -> Authorizator.AuthorizationState.FORBIDDEN);
    }

    public ImapSaslSessionContext(ImapSession session, Authorizator delegationAuthorizator) {
        this.session = session;
        this.delegationAuthorizator = delegationAuthorizator;
        this.services = new HashMap<>();
        this.mailboxSession = Optional.empty();
        this.failureDetails = Optional.empty();
    }

    @Override
    public <T> Optional<T> service(Class<T> serviceType) {
        return Optional.ofNullable(services.get(serviceType))
            .map(serviceType::cast);
    }

    @Override
    public <T> void register(Class<T> serviceType, T service) {
        services.put(serviceType, service);
    }

    public boolean isPlainAuthDisallowed() {
        return session.isPlainAuthDisallowed();
    }

    public boolean supportsOAuth() {
        return session.supportsOAuth();
    }

    public Authorizator delegationAuthorizator() {
        return delegationAuthorizator;
    }

    public ImapSession session() {
        return session;
    }

    public void authenticationSucceeded(MailboxSession mailboxSession) {
        this.mailboxSession = Optional.of(mailboxSession);
    }

    public void recordFailureDetails(FailureDetails failureDetails) {
        this.failureDetails = Optional.of(failureDetails);
    }

    public void processingFailed() {
        this.processingFailure = true;
    }

    public Optional<MailboxSession> mailboxSession() {
        return mailboxSession;
    }

    public Optional<FailureDetails> failureDetails() {
        return failureDetails;
    }

    public boolean hasProcessingFailure() {
        return processingFailure;
    }
}
