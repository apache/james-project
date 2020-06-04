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

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThatCode;

import javax.inject.Inject;

import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.utils.InitializationOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;

class CassandraMessageIdManagerInjectionTest {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<CassandraJamesServerConfiguration>(tmpDir ->
        CassandraJamesServerConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.elasticSearch())
            .build())
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .server(configuration -> CassandraJamesServerMain.createServer(configuration)
            .overrideWith(binder -> Multibinder.newSetBinder(binder, InitializationOperation.class)
                .addBinding()
                .to(CallMe.class)))
        .disableAutoStart()
        .build();

    @Test
    void messageIdManagerShouldBeInjected(GuiceJamesServer server) {
        assertThatCode(server::start).doesNotThrowAnyException();
    }

    public static class CallMe implements InitializationOperation, Startable {
        @Inject
        public CallMe(MessageIdManager messageIdManager) {
        }

        @Override
        public void initModule() {

        }

        @Override
        public Class<? extends Startable> forClass() {
            return CallMe.class;
        }
    }
}
