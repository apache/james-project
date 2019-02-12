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
import org.apache.james.CleanupTasksPerformer;
import org.apache.james.EmbeddedElasticSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerExtension;
import org.apache.james.JamesServerExtensionBuilder;
import org.apache.james.jmap.methods.integration.SendMDNMethodTest;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.SwiftBlobStoreExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.extension.RegisterExtension;

public class RabbitMQSendMDNMethodTest extends SendMDNMethodTest {

    private static final long LIMIT_TO_10_MESSAGES = 10;

    @RegisterExtension
    JamesServerExtension testExtension = new JamesServerExtensionBuilder()
        .extension(new EmbeddedElasticSearchExtension())
        .extension(new CassandraExtension())
        .extension(new SwiftBlobStoreExtension())
        .extension(new RabbitMQExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(CassandraRabbitMQJamesServerMain.MODULES)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES)))
        .build();

    @Override
    protected MessageId randomMessageId() {
        return new CassandraMessageId.Factory().generate();
    }
    
}
