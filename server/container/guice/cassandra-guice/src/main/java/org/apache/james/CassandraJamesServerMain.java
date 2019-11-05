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

import static org.apache.james.eventsourcing.eventstore.cassandra.JsonEventSerializer.EVENT_NESTED_TYPES_INJECTION_NAME;

import java.util.Set;

import org.apache.james.json.DTOModule;
import org.apache.james.modules.BlobExportMechanismModule;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.activemq.ActiveMQQueueModule;
import org.apache.james.modules.data.CassandraDLPConfigurationStoreModule;
import org.apache.james.modules.data.CassandraDomainListModule;
import org.apache.james.modules.data.CassandraJmapModule;
import org.apache.james.modules.data.CassandraMailRepositoryModule;
import org.apache.james.modules.data.CassandraRecipientRewriteTableModule;
import org.apache.james.modules.data.CassandraSieveRepositoryModule;
import org.apache.james.modules.data.CassandraUsersRepositoryModule;
import org.apache.james.modules.eventstore.CassandraEventStoreModule;
import org.apache.james.modules.mailbox.BlobStoreAPIModule;
import org.apache.james.modules.mailbox.CassandraBlobStoreModule;
import org.apache.james.modules.mailbox.CassandraDeletedMessageVaultModule;
import org.apache.james.modules.mailbox.CassandraMailboxModule;
import org.apache.james.modules.mailbox.CassandraQuotaMailingModule;
import org.apache.james.modules.mailbox.CassandraSessionModule;
import org.apache.james.modules.mailbox.ElasticSearchClientModule;
import org.apache.james.modules.mailbox.ElasticSearchMailboxModule;
import org.apache.james.modules.mailbox.TikaMailboxModule;
import org.apache.james.modules.metrics.CassandraMetricsModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.JMAPDraftServerModule;
import org.apache.james.modules.protocols.LMTPServerModule;
import org.apache.james.modules.protocols.ManageSieveServerModule;
import org.apache.james.modules.protocols.POP3ServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.server.CassandraDataRoutesModules;
import org.apache.james.modules.server.CassandraRoutesModule;
import org.apache.james.modules.server.DKIMMailetModule;
import org.apache.james.modules.server.DLPRoutesModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.ElasticSearchMetricReporterModule;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.server.MailQueueRoutesModule;
import org.apache.james.modules.server.MailRepositoriesRoutesModule;
import org.apache.james.modules.server.MailboxRoutesModule;
import org.apache.james.modules.server.MessageIdReIndexingModule;
import org.apache.james.modules.server.ReIndexingModule;
import org.apache.james.modules.server.SieveRoutesModule;
import org.apache.james.modules.server.SwaggerRoutesModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.modules.spamassassin.SpamAssassinListenerModule;
import org.apache.james.modules.vault.DeletedMessageVaultRoutesModule;
import org.apache.james.server.core.configuration.Configuration;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;

public class CassandraJamesServerMain {

    public static final Module WEBADMIN = Modules.combine(
        new CassandraRoutesModule(),
        new CassandraDataRoutesModules(),
        new DataRoutesModules(),
        new DeletedMessageVaultRoutesModule(),
        new MailboxRoutesModule(),
        new MailQueueRoutesModule(),
        new MailRepositoriesRoutesModule(),
        new SwaggerRoutesModule(),
        new WebAdminServerModule(),
        new DLPRoutesModule(),
        new SieveRoutesModule(),
        new ReIndexingModule(),
        new MessageIdReIndexingModule());

    public static final Module PROTOCOLS = Modules.combine(
        new CassandraJmapModule(),
        new IMAPServerModule(),
        new LMTPServerModule(),
        new ManageSieveServerModule(),
        new POP3ServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule(),
        new JMAPDraftServerModule(),
        WEBADMIN);

    public static final Module PLUGINS = Modules.combine(
        new CassandraQuotaMailingModule());

    private static final Module BLOB_MODULE = Modules.combine(
        new BlobStoreAPIModule(),
        new BlobExportMechanismModule());

    private static final Module CASSANDRA_EVENT_STORE_JSON_SERIALIZATION_DEFAULT_MODULE = binder ->
        binder.bind(new TypeLiteral<Set<DTOModule<?, ?>>>() {}).annotatedWith(Names.named(EVENT_NESTED_TYPES_INJECTION_NAME))
            .toInstance(ImmutableSet.of());

    public static final Module CASSANDRA_SERVER_CORE_MODULE = Modules.combine(
        new ActiveMQQueueModule(),
        new CassandraDomainListModule(),
        new CassandraDLPConfigurationStoreModule(),
        new CassandraEventStoreModule(),
        new CassandraMailRepositoryModule(),
        new CassandraMetricsModule(),
        new CassandraBlobStoreModule(),
        new CassandraRecipientRewriteTableModule(),
        new CassandraSessionModule(),
        new CassandraSieveRepositoryModule(),
        new CassandraUsersRepositoryModule(),
        BLOB_MODULE,
        CASSANDRA_EVENT_STORE_JSON_SERIALIZATION_DEFAULT_MODULE);

    public static final Module CASSANDRA_MAILBOX_MODULE = Modules.combine(
        new CassandraMailboxModule(),
        new CassandraDeletedMessageVaultModule(),
        new ElasticSearchClientModule(),
        new ElasticSearchMailboxModule(),
        new ElasticSearchMetricReporterModule(),
        new MailboxModule(),
        new TikaMailboxModule(),
        new SpamAssassinListenerModule());

    public static Module REQUIRE_TASK_MANAGER_MODULE = Modules.combine(
        CASSANDRA_SERVER_CORE_MODULE,
        CASSANDRA_MAILBOX_MODULE,
        PROTOCOLS,
        PLUGINS,
        new DKIMMailetModule());

    public static Module ALL_BUT_JMX_CASSANDRA_MODULE = Modules.combine(
        REQUIRE_TASK_MANAGER_MODULE,
        new TaskManagerModule(),
        CASSANDRA_EVENT_STORE_JSON_SERIALIZATION_DEFAULT_MODULE
    );

    public static void main(String[] args) throws Exception {
        Configuration configuration = Configuration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        GuiceJamesServer server = GuiceJamesServer.forConfiguration(configuration)
                    .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE, new JMXServerModule());
        server.start();
    }

}
