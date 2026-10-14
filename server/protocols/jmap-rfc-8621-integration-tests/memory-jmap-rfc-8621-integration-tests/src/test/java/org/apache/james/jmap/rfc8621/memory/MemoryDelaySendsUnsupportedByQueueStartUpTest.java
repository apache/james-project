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

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.StartUpChecksPerformer;
import org.apache.james.jmap.JMAPModule.DelaySendsStartUpCheck;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableMap;

class MemoryDelaySendsUnsupportedByQueueStartUpTest {
    private static final MemoryMailQueueFactory QUEUE_NOT_SUPPORTING_DELAYS = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory()) {
        @Override
        public boolean supportsDelays() {
            return false;
        }
    };

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule(ImmutableMap.of("delay.sends.enabled", true)))
            .overrideWith(binder -> binder.bind(MemoryMailQueueFactory.class).toInstance(QUEUE_NOT_SUPPORTING_DELAYS)))
        .disableAutoStart()
        .build();

    @Test
    void serverShouldNotStartWhenDelaySendsAreEnabledOnAQueueNotSupportingDelays(GuiceJamesServer server) {
        assertThatThrownBy(server::start)
            .isInstanceOfSatisfying(StartUpChecksPerformer.StartUpChecksException.class,
                e -> assertThat(e.badCheckNames())
                    .contains(DelaySendsStartUpCheck.CHECK_NAME));
    }
}
