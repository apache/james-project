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
package org.apache.james.jmap.rabbitmq;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.EmbeddedElasticSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerExtension;
import org.apache.james.JamesServerExtensionBuilder;
import org.apache.james.jmap.methods.integration.SpamAssassinContract;
import org.apache.james.jmap.methods.integration.SpamAssassinModuleExtension;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.SwiftBlobStoreExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreChoosingConfiguration;
import org.apache.james.spamassassin.SpamAssassinExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class RabbitMQSpamAssassinContractTest implements SpamAssassinContract {

    private static final int LIMIT_TO_20_MESSAGES = 20;

    private static final SpamAssassinModuleExtension spamAssassinExtension = new SpamAssassinModuleExtension();
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerExtensionBuilder()
        .extension(new EmbeddedElasticSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new SwiftBlobStoreExtension())
        .extension(spamAssassinExtension)
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(CassandraRabbitMQJamesServerMain.MODULES)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(binder -> binder.bind(BlobStoreChoosingConfiguration.class)
                .toInstance(BlobStoreChoosingConfiguration.objectStorage()))
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_20_MESSAGES)))
        .build();

    @Override
    public SpamAssassinExtension.SpamAssassin spamAssassin() {
        return spamAssassinExtension.spamAssassinExtension().getSpamAssassin();
    }
}
