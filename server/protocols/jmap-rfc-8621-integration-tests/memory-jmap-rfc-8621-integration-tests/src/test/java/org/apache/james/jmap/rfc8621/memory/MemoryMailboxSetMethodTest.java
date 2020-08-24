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

package org.apache.james.jmap.rfc8621.memory;

import static org.apache.james.MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.MailboxSetMethodContract;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.extension.RegisterExtension;

public class MemoryMailboxSetMethodTest implements MailboxSetMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(IN_MEMORY_SERVER_AGGREGATE_MODULE)
            .overrideWith(new TestJMAPServerModule()))
        .build();

    @Override
    public MailboxId randomMailboxId() {
        return InMemoryId.of(ThreadLocalRandom.current().nextInt(100000) + 100);
    }

    @Override
    public String errorInvalidMailboxIdMessage(String value) {
        return String.format("%s is not a mailboxId: For input string: \\\"%s\\\"", value, value);
    }
}
