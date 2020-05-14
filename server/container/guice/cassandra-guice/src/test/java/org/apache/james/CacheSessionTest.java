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

import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.CassandraJamesServerMain.ALL_BUT_JMX_CASSANDRA_MODULE;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.InjectionNames;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.mailbox.CassandraCacheSessionModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.Session;
import com.google.inject.Inject;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

class CacheSessionTest {
    private static final String TABLE_NAME = "tablename";

    static class CacheSessionTestCheck implements StartUpCheck {
        static final String NAME = "CacheSessionTest-check";
        private final Session cacheSession;

        @Inject
        CacheSessionTestCheck(@Named(InjectionNames.CACHE) Session cacheSession) {
            this.cacheSession = cacheSession;
        }

        @Override
        public CheckResult check() {
            try {
                cacheSession.execute(select().from(TABLE_NAME));
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
    static JamesServerExtension testExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE, new CassandraCacheSessionModule())
            .overrideWith(new TestJMAPServerModule()))
        .overrideServerModule(binder -> Multibinder.newSetBinder(binder, CassandraModule.class, Names.named(InjectionNames.CACHE))
            .addBinding()
            .toInstance(CassandraModule.table(TABLE_NAME)
                .comment("Testing table")
                .statement(statement -> statement
                    .addPartitionKey("id", DataType.timeuuid())
                    .addClusteringColumn("clustering", DataType.bigint()))
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
