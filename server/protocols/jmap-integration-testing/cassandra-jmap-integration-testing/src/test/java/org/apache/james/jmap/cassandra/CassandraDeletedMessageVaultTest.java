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

import java.io.IOException;

import org.apache.james.CassandraJmapTestRule;
import org.apache.james.DockerCassandraRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.categories.BasicFeature;
import org.apache.james.jmap.methods.integration.DeletedMessagesVaultTest;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.mailbox.PreDeletionHookConfiguration;
import org.apache.james.modules.mailbox.PreDeletionHooksConfiguration;
import org.apache.james.vault.DeletedMessageVaultHook;
import org.apache.james.vault.MailRepositoryDeletedMessageVault;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Disabled;

public class CassandraDeletedMessageVaultTest extends DeletedMessagesVaultTest {

    @ClassRule
    public static DockerCassandraRule cassandra = new DockerCassandraRule();

    @Rule
    public CassandraJmapTestRule rule = CassandraJmapTestRule.defaultTestRule();
    
    @Override
    protected GuiceJamesServer createJmapServer() throws IOException {
        return rule.jmapServer(cassandra.getModule(),
            binder -> binder.bind(PreDeletionHooksConfiguration.class)
                .toInstance(PreDeletionHooksConfiguration.forHooks(
                    PreDeletionHookConfiguration.forClass(DeletedMessageVaultHook.class))),
            binder -> binder.bind(WebAdminConfiguration.class).toInstance(WebAdminConfiguration.TEST_CONFIGURATION),
            binder -> binder.bind(MailRepositoryDeletedMessageVault.Configuration.class)
                .toInstance(new MailRepositoryDeletedMessageVault.Configuration(MailRepositoryUrl.from("cassandra://var/deletedMessages/user"))));
    }

    @Override
    protected void awaitSearchUpToDate() {
        rule.await();
    }

    @Disabled("MAILBOX-379 PreDeletionHook are not yet triggered upon mailbox deletion")
    @Category(BasicFeature.class)
    @Override
    @Test
    public void vaultEndpointShouldRestoreImapDeletedMailbox() {

    }
}
