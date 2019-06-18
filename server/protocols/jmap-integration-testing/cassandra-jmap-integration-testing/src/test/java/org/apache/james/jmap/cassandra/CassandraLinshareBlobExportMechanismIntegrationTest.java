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

package org.apache.james.jmap.cassandra;

import static org.apache.james.CassandraJamesServerMain.ALL_BUT_JMX_CASSANDRA_MODULE;

import org.apache.james.CassandraExtension;
import org.apache.james.DockerElasticSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.methods.integration.LinshareBlobExportMechanismIntegrationTest;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.LinshareGuiceExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.mailbox.PreDeletionHookConfiguration;
import org.apache.james.modules.mailbox.PreDeletionHooksConfiguration;
import org.apache.james.vault.DeletedMessageVaultHook;
import org.apache.james.vault.MailRepositoryDeletedMessageVault;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraLinshareBlobExportMechanismIntegrationTest extends LinshareBlobExportMechanismIntegrationTest {

    private static final long LIMIT_TO_10_MESSAGES = 10;

    private static final LinshareGuiceExtension linshareGuiceExtension = new LinshareGuiceExtension();

    @RegisterExtension
    JamesServerExtension testExtension = new JamesServerBuilder()
        .extension(linshareGuiceExtension)
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE)
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
            .overrideWith(binder -> {
                binder.bind(WebAdminConfiguration.class)
                    .toInstance(WebAdminConfiguration.TEST_CONFIGURATION);
                binder.bind(PreDeletionHooksConfiguration.class)
                    .toInstance(PreDeletionHooksConfiguration.forHooks(
                        PreDeletionHookConfiguration.forClass(DeletedMessageVaultHook.class)));
                binder.bind(MailRepositoryDeletedMessageVault.Configuration.class)
                    .toInstance(new MailRepositoryDeletedMessageVault.Configuration(MailRepositoryUrl.from("cassandra://var/deletedMessages/user")));
            }))
        .build();
}
