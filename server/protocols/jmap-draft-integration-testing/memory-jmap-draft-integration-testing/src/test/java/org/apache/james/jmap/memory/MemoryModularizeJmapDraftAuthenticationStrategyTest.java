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

import java.util.List;
import java.util.Optional;

import org.apache.james.GuiceJamesServer;
import org.apache.james.MemoryJmapTestRule;
import org.apache.james.jmap.ModularizeJmapDraftAuthenticationStrategyTest;
import org.apache.james.jmap.draft.JMAPDraftConfiguration;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.Rule;

public class MemoryModularizeJmapDraftAuthenticationStrategyTest extends ModularizeJmapDraftAuthenticationStrategyTest {
    @Rule
    public MemoryJmapTestRule memoryJmap = new MemoryJmapTestRule();

    @Override
    protected GuiceJamesServer createJmapServer(Optional<List<String>> authOverride) throws Exception {
        return memoryJmap.jmapServer()
            .overrideWith(binder -> binder.bind(JMAPDraftConfiguration.class)
                .toInstance(TestJMAPServerModule
                    .jmapDraftConfigurationBuilder()
                    .authenticationStrategies(authOverride)
                    .build()));
    }

}
