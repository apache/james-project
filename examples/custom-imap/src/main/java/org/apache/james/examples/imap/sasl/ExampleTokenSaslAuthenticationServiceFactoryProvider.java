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

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.modules.protocols.ImapSaslAuthenticationServiceFactoryProvider;
import org.apache.james.protocols.api.sasl.SaslAuthenticationServiceFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class ExampleTokenSaslAuthenticationServiceFactoryProvider implements ImapSaslAuthenticationServiceFactoryProvider {
    private final MailboxManager mailboxManager;

    @Inject
    public ExampleTokenSaslAuthenticationServiceFactoryProvider(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    @Override
    public ImmutableList<SaslAuthenticationServiceFactory<?>> provide(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        return ImmutableList.of(new ExampleTokenSaslAuthenticationServiceFactory(mailboxManager, ExampleTokenSaslConfiguration.from(configuration)));
    }
}
