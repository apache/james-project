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

package org.apache.james;

import java.util.List;

import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.modules.BlobExportMechanismModule;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.modules.RunArgumentsModule;
import org.apache.james.modules.blobstore.BlobStoreCacheModulesChooser;
import org.apache.james.modules.blobstore.BlobStoreModulesChooser;
import org.apache.james.modules.data.PostgresDataModule;
import org.apache.james.modules.data.PostgresDelegationStoreModule;
import org.apache.james.modules.data.PostgresUsersRepositoryModule;
import org.apache.james.modules.data.SievePostgresRepositoryModules;
import org.apache.james.modules.event.RabbitMQEventBusModule;
import org.apache.james.modules.events.PostgresDeadLetterModule;
import org.apache.james.modules.eventstore.MemoryEventStoreModule;
import org.apache.james.modules.mailbox.DefaultEventModule;
import org.apache.james.modules.mailbox.PostgresDeletedMessageVaultModule;
import org.apache.james.modules.mailbox.PostgresMailboxModule;
import org.apache.james.modules.mailbox.TikaMailboxModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.LMTPServerModule;
import org.apache.james.modules.protocols.ManageSieveServerModule;
import org.apache.james.modules.protocols.POP3ServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.queue.activemq.ActiveMQQueueModule;
import org.apache.james.modules.queue.rabbitmq.RabbitMQModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.DefaultProcessorsConfigurationProviderModule;
import org.apache.james.modules.server.InconsistencyQuotasSolvingRoutesModule;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.server.MailQueueRoutesModule;
import org.apache.james.modules.server.MailRepositoriesRoutesModule;
import org.apache.james.modules.server.MailboxRoutesModule;
import org.apache.james.modules.server.NoJwtModule;
import org.apache.james.modules.server.RawPostDequeueDecoratorModule;
import org.apache.james.modules.server.ReIndexingModule;
import org.apache.james.modules.server.SieveRoutesModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.server.WebAdminReIndexingTaskSerializationModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.modules.vault.DeletedMessageVaultRoutesModule;
import org.apache.james.vault.VaultConfiguration;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class PostgresJamesServerMain implements JamesServerMain {

    private static final Module WEBADMIN = Modules.combine(
        new WebAdminServerModule(),
        new DataRoutesModules(),
        new InconsistencyQuotasSolvingRoutesModule(),
        new MailboxRoutesModule(),
        new MailQueueRoutesModule(),
        new MailRepositoriesRoutesModule(),
        new ReIndexingModule(),
        new SieveRoutesModule(),
        new WebAdminReIndexingTaskSerializationModule());

    private static final Module PROTOCOLS = Modules.combine(
        new IMAPServerModule(),
        new LMTPServerModule(),
        new ManageSieveServerModule(),
        new POP3ServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule(),
        WEBADMIN);

    private static final Module POSTGRES_SERVER_MODULE = Modules.combine(
        new ActiveMQQueueModule(),
        new BlobExportMechanismModule(),
        new PostgresDelegationStoreModule(),
        new DefaultProcessorsConfigurationProviderModule(),
        new PostgresMailboxModule(),
        new PostgresDeadLetterModule(),
        new PostgresDataModule(),
        new MailboxModule(),
        new NoJwtModule(),
        new RawPostDequeueDecoratorModule(),
        new SievePostgresRepositoryModules(),
        new TaskManagerModule(),
        new MemoryEventStoreModule(),
        new TikaMailboxModule());

    private static final Module POSTGRES_MODULE_AGGREGATE = Modules.combine(
        new MailetProcessingModule(), POSTGRES_SERVER_MODULE, PROTOCOLS);

    public static void main(String[] args) throws Exception {
        ExtraProperties.initialize();

        PostgresJamesConfiguration configuration = PostgresJamesConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        LOGGER.info("Loading configuration {}", configuration.toString());
        GuiceJamesServer server = createServer(configuration)
            .combineWith(new JMXServerModule())
            .overrideWith(new RunArgumentsModule(args));

        JamesServerMain.main(server);
    }

    public static GuiceJamesServer createServer(PostgresJamesConfiguration configuration) {
        SearchConfiguration searchConfiguration = configuration.searchConfiguration();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(SearchModuleChooser.chooseModules(searchConfiguration))
            .combineWith(new UsersRepositoryModuleChooser(new PostgresUsersRepositoryModule())
                .chooseModules(configuration.getUsersRepositoryImplementation()))
            .combineWith(chooseBlobStoreModules(configuration))
            .combineWith(chooseEventBusModules(configuration))
            .combineWith(chooseDeletedMessageVaultModules(configuration.getDeletedMessageVaultConfiguration()))
            .combineWith(POSTGRES_MODULE_AGGREGATE);
    }

    private static List<Module> chooseBlobStoreModules(PostgresJamesConfiguration configuration) {
        ImmutableList.Builder<Module> builder = ImmutableList.<Module>builder()
            .addAll(BlobStoreModulesChooser.chooseModules(configuration.blobStoreConfiguration()))
            .add(new BlobStoreCacheModulesChooser.CacheDisabledModule());

        return builder.build();
    }

    public static List<Module> chooseEventBusModules(PostgresJamesConfiguration configuration) {
        switch (configuration.eventBusImpl()) {
            case IN_MEMORY:
                return List.of(new DefaultEventModule());
            case RABBITMQ:
                return List.of(new RabbitMQModule(),
                    Modules.override(new DefaultEventModule()).with(new RabbitMQEventBusModule()));
            default:
                throw new RuntimeException("Unsupported event-bus implementation " + configuration.eventBusImpl().name());
        }
    }

    private static Module chooseDeletedMessageVaultModules(VaultConfiguration vaultConfiguration) {
        if (vaultConfiguration.isEnabled()) {
            return Modules.combine(new PostgresDeletedMessageVaultModule(), new DeletedMessageVaultRoutesModule());
        }

        return Modules.EMPTY_MODULE;
    }
}
