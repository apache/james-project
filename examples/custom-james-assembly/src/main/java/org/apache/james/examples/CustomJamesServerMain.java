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

package org.apache.james.examples;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.modules.data.MemoryDataModule;
import org.apache.james.modules.data.MemoryUsersRepositoryModule;
import org.apache.james.modules.eventstore.MemoryEventStoreModule;
import org.apache.james.modules.mailbox.MemoryMailboxModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.queue.memory.MemoryMailQueueModule;
import org.apache.james.modules.server.MailRepositoryTaskSerializationModule;
import org.apache.james.modules.server.MailetContainerModule;
import org.apache.james.modules.server.RawPostDequeueDecoratorModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.server.core.configuration.Configuration;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class CustomJamesServerMain implements JamesServerMain {
    public static final Module PROTOCOLS = Modules.combine(
        new IMAPServerModule(),
        new ProtocolHandlerModule(),
        new MailRepositoryTaskSerializationModule(),
        new SMTPServerModule());

    public static final Module CUSTOM_SERVER_MODULE = Modules.combine(
        new MailetProcessingModule(),
        new MailboxModule(),
        new MemoryDataModule(),
        new MemoryEventStoreModule(),
        new MemoryMailboxModule(),
        new MemoryMailQueueModule(),
        new TaskManagerModule(),
        new RawPostDequeueDecoratorModule(),
        binder -> binder.bind(MailetContainerModule.DefaultProcessorsConfigurationSupplier.class)
            .toInstance(BaseHierarchicalConfiguration::new));

    public static final Module CUSTOM_SERVER_AGGREGATE_MODULE = Modules.combine(
        CUSTOM_SERVER_MODULE,
        PROTOCOLS);

    public static void main(String[] args) throws Exception {
	    MemoryJamesConfiguration configuration = MemoryJamesConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        JamesServerMain.main(GuiceJamesServer.forConfiguration(configuration)
            .combineWith(CUSTOM_SERVER_AGGREGATE_MODULE)
            .combineWith(new UsersRepositoryModuleChooser(new MemoryUsersRepositoryModule())
								             .chooseModules(configuration.getUsersRepositoryImplementation()))
        );
    }
}
