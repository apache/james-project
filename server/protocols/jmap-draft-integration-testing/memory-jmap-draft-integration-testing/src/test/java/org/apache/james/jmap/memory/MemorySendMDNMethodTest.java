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

import java.util.concurrent.ThreadLocalRandom;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.jmap.draft.methods.integration.SendMDNMethodTest;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.extension.RegisterExtension;

public class MemorySendMDNMethodTest extends SendMDNMethodTest {

    @RegisterExtension
    JamesServerExtension testExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(binder -> binder.bind(MessageSearchIndex.class).to(SimpleMessageSearchIndex.class)))
        .build();

    @Override
    protected MessageId randomMessageId() {
        return new InMemoryMessageId.Factory().fromString(String.valueOf(ThreadLocalRandom.current().nextInt(100000) + 100));
    }
}
