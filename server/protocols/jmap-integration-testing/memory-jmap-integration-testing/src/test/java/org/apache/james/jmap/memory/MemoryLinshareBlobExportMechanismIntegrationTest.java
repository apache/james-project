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

package org.apache.james.jmap.memory;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesServerMain;
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

class MemoryLinshareBlobExportMechanismIntegrationTest extends LinshareBlobExportMechanismIntegrationTest {

    private static final int LIMIT_TO_10_MESSAGES = 10;

    private final LinshareGuiceExtension linshareGuiceExtension = new LinshareGuiceExtension();

    @RegisterExtension
    JamesServerExtension jamesServerExtension = new JamesServerBuilder()
        .extension(linshareGuiceExtension)
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
            .overrideWith(binder -> {
                binder.bind(WebAdminConfiguration.class)
                    .toInstance(WebAdminConfiguration.TEST_CONFIGURATION);
                binder.bind(PreDeletionHooksConfiguration.class)
                    .toInstance(PreDeletionHooksConfiguration.forHooks(
                        PreDeletionHookConfiguration.forClass(DeletedMessageVaultHook.class)));
                binder.bind(MailRepositoryDeletedMessageVault.Configuration.class)
                    .toInstance(new MailRepositoryDeletedMessageVault.Configuration(MailRepositoryUrl.from("memory://var/deletedMessages/user")));
            }))
        .build();
}
