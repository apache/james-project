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

import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.data.CassandraDomainListModule;
import org.apache.james.modules.data.CassandraJmapModule;
import org.apache.james.modules.data.CassandraRecipientRewriteTableModule;
import org.apache.james.modules.data.CassandraSieveRepositoryModule;
import org.apache.james.modules.data.CassandraUsersRepositoryModule;
import org.apache.james.modules.mailbox.CassandraMailboxModule;
import org.apache.james.modules.mailbox.CassandraSessionModule;
import org.apache.james.modules.mailbox.ElasticSearchMailboxModule;
import org.apache.james.modules.mailbox.TikaMailboxModule;
import org.apache.james.modules.metrics.CassandraMetricsModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.JMAPServerModule;
import org.apache.james.modules.protocols.LMTPServerModule;
import org.apache.james.modules.protocols.ManageSieveServerModule;
import org.apache.james.modules.protocols.POP3ServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.server.ActiveMQQueueModule;
import org.apache.james.modules.server.CassandraRoutesModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.ESMetricReporterModule;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.server.MailboxRoutesModule;
import org.apache.james.modules.server.SwaggerRoutesModules;
import org.apache.james.modules.server.WebAdminServerModule;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class CassandraJamesServerMain {

    public static final Module protocols = Modules.combine(
            new IMAPServerModule(),
            new ProtocolHandlerModule(),
            new POP3ServerModule(),
            new SMTPServerModule(),
            new LMTPServerModule(),
            new ManageSieveServerModule(),
            new WebAdminServerModule(),
            new DataRoutesModules(),
            new MailboxRoutesModule(),
            new CassandraRoutesModule(),
            new SwaggerRoutesModules());

    public static final Module cassandraServerModule = Modules.combine(
        new JMAPServerModule(),
        new CassandraUsersRepositoryModule(),
        new CassandraDomainListModule(),
        new CassandraRecipientRewriteTableModule(),
        new CassandraSieveRepositoryModule(),
        new CassandraJmapModule(),
        new CassandraMailboxModule(),
        new CassandraSessionModule(),
        new ElasticSearchMailboxModule(),
        new TikaMailboxModule(),
        new ActiveMQQueueModule(),
        new ESMetricReporterModule(),
        new CassandraMetricsModule(),
        new MailboxModule());


    public static void main(String[] args) throws Exception {
        GuiceJamesServer server = new GuiceJamesServer()
                    .combineWith(cassandraServerModule, protocols, new JMXServerModule());
        server.start();
    }

}
