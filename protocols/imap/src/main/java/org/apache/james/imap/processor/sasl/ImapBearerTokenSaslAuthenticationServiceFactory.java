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

import jakarta.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.protocols.api.sasl.BearerTokenSaslAuthenticationService;
import org.apache.james.protocols.api.sasl.SaslAuthenticationServiceFactory;
import org.apache.james.protocols.api.sasl.SaslProtocol;
import org.apache.james.protocols.api.sasl.SaslSessionContext;

public class ImapBearerTokenSaslAuthenticationServiceFactory implements SaslAuthenticationServiceFactory<BearerTokenSaslAuthenticationService> {
    private final MailboxManager mailboxManager;

    @Inject
    public ImapBearerTokenSaslAuthenticationServiceFactory(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    @Override
    public SaslProtocol protocol() {
        return SaslProtocol.IMAP;
    }

    @Override
    public Class<BearerTokenSaslAuthenticationService> serviceType() {
        return BearerTokenSaslAuthenticationService.class;
    }

    @Override
    public Optional<BearerTokenSaslAuthenticationService> create(SaslSessionContext context) {
        if (context instanceof ImapSaslSessionContext imapContext && imapContext.supportsOAuth()) {
            return Optional.of(new ImapBearerTokenSaslAuthenticationService(mailboxManager, imapContext));
        }
        return Optional.empty();
    }
}
