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

package org.apache.james.webadmin.integration.rabbitmq.vault;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.DockerElasticSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.rabbitmq.DockerRabbitMQSingleton;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.LinshareGuiceExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.TestRabbitMQModule;
import org.apache.james.modules.vault.TestDeleteMessageVaultPreDeletionHookModule;
import org.apache.james.webadmin.integration.WebadminIntegrationTestModule;
import org.apache.james.webadmin.integration.vault.LinshareBlobExportMechanismIntegrationTest;
import org.junit.jupiter.api.extension.RegisterExtension;

class RabbitMQLinshareBlobExportMechanismIntegrationTest extends LinshareBlobExportMechanismIntegrationTest {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder()
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new LinshareGuiceExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(CassandraRabbitMQJamesServerMain.MODULES)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(new TestRabbitMQModule(DockerRabbitMQSingleton.SINGLETON))
            .overrideWith(new WebadminIntegrationTestModule())
            .overrideWith(new TestDeleteMessageVaultPreDeletionHookModule()))
        .build();
}
