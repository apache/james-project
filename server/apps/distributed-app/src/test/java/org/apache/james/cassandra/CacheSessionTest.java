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

package org.apache.james.cassandra;

import static com.datastax.oss.driver.api.core.type.DataTypes.BIGINT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMEUUID;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.init.configuration.InjectionNames;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.modules.mailbox.CassandraCacheSessionModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.inject.Inject;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

class CacheSessionTest {
    private static final String TABLE_NAME = "tablename";

    static class CacheSessionTestCheck implements StartUpCheck {
        static final String NAME = "CacheSessionTest-check";
        private final CqlSession cacheSession;

        @Inject
        CacheSessionTestCheck(@Named(InjectionNames.CACHE) CqlSession cacheSession) {
            this.cacheSession = cacheSession;
        }

        @Override
        public CheckResult check() {
            try {
                cacheSession.execute(selectFrom(TABLE_NAME).all().build());
                return CheckResult.builder()
                    .checkName(NAME)
                    .resultType(ResultType.GOOD)
                    .build();
            } catch (Exception e) {
                return CheckResult.builder()
                    .checkName(NAME)
                    .resultType(ResultType.BAD)
                    .description(String.format("%s do not exist", TABLE_NAME))
                    .build();
            }
        }

        @Override
        public String checkName() {
            return NAME;
        }
    }

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
        CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.cassandra().deduplication().noCryptoConfig())
            .searchConfiguration(SearchConfiguration.scanning())
            .build())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
            .combineWith(new CassandraCacheSessionModule()))
        .overrideServerModule(binder -> Multibinder.newSetBinder(binder, CassandraDataDefinition.class, Names.named(InjectionNames.CACHE))
            .addBinding()
            .toInstance(CassandraDataDefinition.table(TABLE_NAME)
                .comment("Testing table")
                .statement(statement -> types -> statement
                    .withPartitionKey("id", TIMEUUID)
                    .withClusteringColumn("clustering", BIGINT))
                .build()))
        .overrideServerModule(binder -> Multibinder.newSetBinder(binder, StartUpCheck.class)
            .addBinding()
            .to(CacheSessionTestCheck.class))
        .disableAutoStart()
        .build();

    @Test
    void cacheTableShouldBeWellCreated(GuiceJamesServer jamesServer) {
        assertThatCode(jamesServer::start)
            .doesNotThrowAnyException();
    }
}
