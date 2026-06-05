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

import org.apache.james.imap.processor.sasl.ImapSaslSessionContext;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslIdentity;

public class ExampleTokenSaslAuthenticationService {
    private final MailboxManager mailboxManager;
    private final ImapSaslSessionContext context;
    private final ExampleTokenSaslConfiguration configuration;

    public ExampleTokenSaslAuthenticationService(MailboxManager mailboxManager, ImapSaslSessionContext context, ExampleTokenSaslConfiguration configuration) {
        this.mailboxManager = mailboxManager;
        this.context = context;
        this.configuration = configuration;
    }

    public SaslAuthenticationResult authenticate(String token) {
        if (!configuration.expectedToken().equals(token)) {
            return new SaslAuthenticationResult.Failure("EXAMPLE-TOKEN authentication failed.");
        }

        MailboxSession mailboxSession = mailboxManager.createSystemSession(configuration.authorizedUser());
        context.authenticationSucceeded(mailboxSession);
        return new SaslAuthenticationResult.Success(new SaslIdentity(configuration.authorizedUser(), configuration.authorizedUser()));
    }
}
