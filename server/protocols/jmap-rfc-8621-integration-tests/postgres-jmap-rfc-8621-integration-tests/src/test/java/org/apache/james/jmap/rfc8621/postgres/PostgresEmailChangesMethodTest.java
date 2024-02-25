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

package org.apache.james.jmap.rfc8621.postgres;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import org.apache.james.DockerOpenSearchExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.PostgresJamesConfiguration;
import org.apache.james.PostgresJamesServerMain;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.jmap.api.change.Limit;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.postgres.change.PostgresEmailChangeRepository;
import org.apache.james.jmap.postgres.change.PostgresStateFactory;
import org.apache.james.jmap.rfc8621.contract.EmailChangesMethodContract;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.name.Names;

public class PostgresEmailChangesMethodTest implements EmailChangesMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<PostgresJamesConfiguration>(tmpDir ->
        PostgresJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.openSearch())
            .usersRepository(DEFAULT)
            .eventBusImpl(PostgresJamesConfiguration.EventBusImpl.RABBITMQ)
            .blobStore(BlobStoreConfiguration.builder()
                .postgres()
                .disableCache()
                .deduplication()
                .noCryptoConfig())
            .build())
        .extension(PostgresExtension.empty())
        .extension(new RabbitMQExtension())
        .extension(new DockerOpenSearchExtension())
        .server(configuration -> PostgresJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> binder.bind(Limit.class).annotatedWith(Names.named(PostgresEmailChangeRepository.LIMIT_NAME)).toInstance(Limit.of(5)))
            .overrideWith(binder -> binder.bind(Limit.class).annotatedWith(Names.named(PostgresEmailChangeRepository.LIMIT_NAME)).toInstance(Limit.of(5))))
        .build();

    @Override
    public State.Factory stateFactory() {
        return new PostgresStateFactory();
    }
}
