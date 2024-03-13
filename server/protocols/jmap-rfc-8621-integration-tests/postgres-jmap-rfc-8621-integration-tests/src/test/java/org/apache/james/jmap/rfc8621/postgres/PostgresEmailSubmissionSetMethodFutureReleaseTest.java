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

import org.apache.james.ClockExtension;
import org.apache.james.DockerOpenSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.PostgresJamesConfiguration;
import org.apache.james.PostgresJamesServerMain;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.jmap.rfc8621.contract.EmailSubmissionSetMethodFutureReleaseContract;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.name.Names;

public class PostgresEmailSubmissionSetMethodFutureReleaseTest implements EmailSubmissionSetMethodFutureReleaseContract {
    public static final PostgresMessageId.Factory MESSAGE_ID_FACTORY = new PostgresMessageId.Factory();

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
        .extension(new ClockExtension())
        .server(configuration -> PostgresJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(new DelegationProbeModule()))
        .overrideServerModule(binder -> binder.bind(Boolean.class).annotatedWith(Names.named("supportsDelaySends")).toInstance(true))
        .build();

    @Override
    public MessageId randomMessageId() {
        return MESSAGE_ID_FACTORY.generate();
    }

    @Disabled("Not work for postgres test")
    @Override
    public void emailSubmissionSetCreateShouldDeliverEmailWhenHoldForExpired(GuiceJamesServer server, UpdatableTickingClock updatableTickingClock){
    }

    @Disabled("Not work for postgres test")
    @Override
    public void emailSubmissionSetCreateShouldDeliverEmailWhenHoldUntilExpired(GuiceJamesServer server, UpdatableTickingClock updatableTickingClock){
    }

    @Disabled("Not work for postgres test")
    @Override
    public void emailSubmissionSetCreateShouldDelayEmailWithHoldFor(GuiceJamesServer server, UpdatableTickingClock updatableTickingClock){
    }

    @Disabled("Not work for postgres test")
    @Override
    public void emailSubmissionSetCreateShouldDelayEmailWithHoldUntil(GuiceJamesServer server, UpdatableTickingClock updatableTickingClock){
    }
}
