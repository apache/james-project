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

package org.apache.james.examples.imap.sasl;

import java.util.Optional;

import org.apache.james.imap.processor.sasl.ImapSaslSessionContext;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.protocols.api.sasl.SaslAuthenticationServiceFactory;
import org.apache.james.protocols.api.sasl.SaslProtocol;
import org.apache.james.protocols.api.sasl.SaslSessionContext;

public class ExampleTokenSaslAuthenticationServiceFactory implements SaslAuthenticationServiceFactory<ExampleTokenSaslAuthenticationService> {
    private final MailboxManager mailboxManager;
    private final ExampleTokenSaslConfiguration configuration;

    public ExampleTokenSaslAuthenticationServiceFactory(MailboxManager mailboxManager, ExampleTokenSaslConfiguration configuration) {
        this.mailboxManager = mailboxManager;
        this.configuration = configuration;
    }

    @Override
    public SaslProtocol protocol() {
        return SaslProtocol.IMAP;
    }

    @Override
    public Class<ExampleTokenSaslAuthenticationService> serviceType() {
        return ExampleTokenSaslAuthenticationService.class;
    }

    @Override
    public Optional<ExampleTokenSaslAuthenticationService> create(SaslSessionContext context) {
        if (context instanceof ImapSaslSessionContext imapContext) {
            return Optional.of(new ExampleTokenSaslAuthenticationService(mailboxManager, imapContext, configuration));
        }
        return Optional.empty();
    }
}
