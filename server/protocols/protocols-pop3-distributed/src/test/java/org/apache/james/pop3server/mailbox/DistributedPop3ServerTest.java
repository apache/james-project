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

package org.apache.james.pop3server.mailbox;

import java.util.Optional;

import org.apache.james.UserEntityValidator;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.pop3server.POP3ServerTest;
import org.apache.james.protocols.lib.mock.MockProtocolHandlerLoader;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

import com.google.inject.name.Names;

public class DistributedPop3ServerTest extends POP3ServerTest {
    protected void setUpServiceManager() {
        InMemoryIntegrationResources memoryIntegrationResources = InMemoryIntegrationResources.builder()
            .authenticator((userid, passwd) -> {
                try {
                    return usersRepository.test(userid, passwd.toString());
                } catch (UsersRepositoryException e) {
                    e.printStackTrace();
                    return Optional.empty();
                }
            })
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();
        mailboxManager = memoryIntegrationResources
            .getMailboxManager();
        Configuration configuration = Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();
        fileSystem = new FileSystemImpl(configuration.directories());
        MemoryPop3MetadataStore metadataStore = new MemoryPop3MetadataStore();
        mailboxManager.getEventBus().register(
            new PopulateMetadataStoreListener(metadataStore));

        protocolHandlerChain = MockProtocolHandlerLoader.builder()
            .put(binder -> binder.bind(UsersRepository.class).toInstance(usersRepository))
            .put(binder -> binder.bind(MailboxManager.class).annotatedWith(Names.named("mailboxmanager")).toInstance(mailboxManager))
            .put(binder -> binder.bind(FileSystem.class).toInstance(fileSystem))
            .put(binder -> binder.bind(MailboxAdapterFactory.class).to(DistributedMailboxAdapter.Factory.class))
            .put(binder -> binder.bind(MessageIdManager.class).toInstance(memoryIntegrationResources.getMessageIdManager()))
            .put(binder -> binder.bind(MessageId.Factory.class).toInstance(memoryIntegrationResources.getMessageIdFactory()))
            .put(binder -> binder.bind(MetricFactory.class).to(RecordingMetricFactory.class))
            .put(binder -> binder.bind(Pop3MetadataStore.class).toInstance(metadataStore))
            .put(binder -> binder.bind(UserEntityValidator.class).toInstance(UserEntityValidator.NOOP))
            .build();
    }
}
