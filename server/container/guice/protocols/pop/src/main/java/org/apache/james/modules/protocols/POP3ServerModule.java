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

package org.apache.james.modules.protocols;

import org.apache.james.ProtocolConfigurationSanitizer;
import org.apache.james.RunArguments;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.ConfigurationSanitizer;
import org.apache.james.pop3server.mailbox.DefaultMailboxAdapterFactory;
import org.apache.james.pop3server.mailbox.MailboxAdapterFactory;
import org.apache.james.pop3server.netty.POP3ServerFactory;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.KeystoreCreator;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class POP3ServerModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(POP3ServerFactory.class).in(Scopes.SINGLETON);
        bind(DefaultMailboxAdapterFactory.class).in(Scopes.SINGLETON);

        bind(MailboxAdapterFactory.class).to(DefaultMailboxAdapterFactory.class);

        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(Pop3GuiceProbe.class);
    }

    @ProvidesIntoSet
    InitializationOperation configurePop3(ConfigurationProvider configurationProvider, POP3ServerFactory pop3ServerFactory) {
        return InitilizationOperationBuilder
            .forClass(POP3ServerFactory.class)
            .init(() -> {
                pop3ServerFactory.configure(configurationProvider.getConfiguration("pop3server"));
                pop3ServerFactory.init();
            });
    }

    @ProvidesIntoSet
    ConfigurationSanitizer configurationSanitizer(ConfigurationProvider configurationProvider, KeystoreCreator keystoreCreator,
                                                      FileSystem fileSystem, RunArguments runArguments) {
        return new ProtocolConfigurationSanitizer(configurationProvider, keystoreCreator, fileSystem, runArguments, "pop3server");
    }
}
