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

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.api.change.Limit;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.memory.change.MemoryEmailChangeRepository;
import org.apache.james.jmap.memory.change.MemoryMailboxChangeRepository;
import org.apache.james.jmap.rfc8621.contract.EmailChangesMethodContract;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.name.Names;

public class MemoryEmailChangesMethodTest implements EmailChangesMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(IN_MEMORY_SERVER_AGGREGATE_MODULE)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> binder.bind(Limit.class).annotatedWith(Names.named(MemoryMailboxChangeRepository.LIMIT_NAME)).toInstance(Limit.of(5)))
            .overrideWith(binder -> binder.bind(Limit.class).annotatedWith(Names.named(MemoryEmailChangeRepository.LIMIT_NAME)).toInstance(Limit.of(5))))
        .build();

    @Override
    public State.Factory stateFactory() {
        return State.Factory.DEFAULT;
    }
}
