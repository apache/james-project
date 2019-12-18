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

import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.jwt.JwtConfiguration;
import org.apache.james.modules.BlobExportMechanismModule;
import org.apache.james.modules.BlobMemoryModule;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.data.MemoryDataJmapModule;
import org.apache.james.modules.data.MemoryDataModule;
import org.apache.james.modules.eventstore.MemoryEventStoreModule;
import org.apache.james.modules.mailbox.MemoryMailboxModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.JMAPDraftServerModule;
import org.apache.james.modules.protocols.LMTPServerModule;
import org.apache.james.modules.protocols.ManageSieveServerModule;
import org.apache.james.modules.protocols.POP3ServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.server.CamelMailetContainerModule;
import org.apache.james.modules.server.DKIMMailetModule;
import org.apache.james.modules.server.DLPRoutesModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.server.JmapTasksModule;
import org.apache.james.modules.server.MailQueueRoutesModule;
import org.apache.james.modules.server.MailRepositoriesRoutesModule;
import org.apache.james.modules.server.MailboxRoutesModule;
import org.apache.james.modules.server.MemoryMailQueueModule;
import org.apache.james.modules.server.RawPostDequeueDecoratorModule;
import org.apache.james.modules.server.SieveRoutesModule;
import org.apache.james.modules.server.SwaggerRoutesModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.modules.spamassassin.SpamAssassinListenerModule;
import org.apache.james.modules.vault.DeletedMessageVaultModule;
import org.apache.james.modules.vault.DeletedMessageVaultRoutesModule;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.apache.james.webadmin.authentication.AuthenticationFilter;
import org.apache.james.webadmin.authentication.NoAuthenticationFilter;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class MemoryJamesServerMain {

    public static final Module WEBADMIN = Modules.combine(
        new WebAdminServerModule(),
        new DataRoutesModules(),
        new DeletedMessageVaultRoutesModule(),
        new DLPRoutesModule(),
        new MailboxRoutesModule(),
        new MailQueueRoutesModule(),
        new MailRepositoriesRoutesModule(),
        new SieveRoutesModule(),
        new SwaggerRoutesModule());


    public static final JwtConfiguration NO_JWT_CONFIGURATION = new JwtConfiguration(Optional.empty());

    public static final Module WEBADMIN_NO_AUTH_MODULE = Modules.combine(binder -> binder.bind(JwtConfiguration.class).toInstance(NO_JWT_CONFIGURATION),
        binder -> binder.bind(AuthenticationFilter.class).to(NoAuthenticationFilter.class),
        binder -> binder.bind(WebAdminConfiguration.class).toInstance(WebAdminConfiguration.TEST_CONFIGURATION));

    public static final Module WEBADMIN_TESTING = Modules.override(WEBADMIN)
        .with(WEBADMIN_NO_AUTH_MODULE);

    public static final Module PROTOCOLS = Modules.combine(
        new IMAPServerModule(),
        new LMTPServerModule(),
        new ManageSieveServerModule(),
        new POP3ServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule(),
        new SpamAssassinListenerModule());

    public static final Module JMAP = Modules.combine(
        new JmapTasksModule(),
        new MemoryDataJmapModule(),
        new JMAPDraftServerModule());

    public static final Module IN_MEMORY_SERVER_MODULE = Modules.combine(
        new BlobMemoryModule(),
        new DeletedMessageVaultModule(),
        new BlobExportMechanismModule(),
        new MailboxModule(),
        new MemoryDataModule(),
        new MemoryEventStoreModule(),
        new MemoryMailboxModule(),
        new MemoryMailQueueModule(),
        new TaskManagerModule());

    public static final Module SMTP_ONLY_MODULE = Modules.combine(
        MemoryJamesServerMain.IN_MEMORY_SERVER_MODULE,
        new ProtocolHandlerModule(),
        new SMTPServerModule(),
        new RawPostDequeueDecoratorModule(),
        binder -> binder.bind(CamelMailetContainerModule.DefaultProcessorsConfigurationSupplier.class)
            .toInstance(BaseHierarchicalConfiguration::new));


    public static final Module SMTP_AND_IMAP_MODULE = Modules.combine(
        SMTP_ONLY_MODULE,
        new IMAPServerModule());

    public static final Module IN_MEMORY_SERVER_AGGREGATE_MODULE = Modules.combine(
        IN_MEMORY_SERVER_MODULE,
        PROTOCOLS,
        JMAP,
        WEBADMIN,
        new DKIMMailetModule());

    public static void main(String[] args) throws Exception {
        Configuration configuration = Configuration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        GuiceJamesServer.forConfiguration(configuration)
            .combineWith(new FakeSearchMailboxModule())
            .combineWith(IN_MEMORY_SERVER_AGGREGATE_MODULE, new JMXServerModule())
            .start();
    }

}
