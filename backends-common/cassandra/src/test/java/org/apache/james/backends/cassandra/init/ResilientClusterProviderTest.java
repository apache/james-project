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

package org.apache.james.backends.cassandra.init;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ResilientClusterProviderTest {
    @RegisterExtension
    static CassandraClusterExtension cassandraExtension = new CassandraClusterExtension(CassandraModule.EMPTY_MODULE);

    @Test
    void getShouldNotThrowWhenHealthyCassandra() {
        assertThatCode(() -> new ResilientClusterProvider(cassandraExtension.clusterConfiguration().build(), CassandraConsistenciesConfiguration.DEFAULT)
                .get())
            .doesNotThrowAnyException();
    }

    @Test
    void getShouldThrowWhenNotHealthyCassandra() {
        cassandraExtension.pause();
        try {
            assertThatThrownBy(() -> new ResilientClusterProvider(cassandraExtension.clusterConfiguration()
                    .maxRetry(1)
                    .minDelay(1)
                    .build(), CassandraConsistenciesConfiguration.DEFAULT)
                .get())
                .isInstanceOf(Exception.class);
        } finally {
            cassandraExtension.unpause();
        }
    }

    @Test
    void getShouldRecoverFromTemporaryOutage() {
        cassandraExtension.pause();

        try {
            Mono.delay(Duration.ofMillis(200))
                .then(Mono.fromRunnable(cassandraExtension::unpause))
                .subscribeOn(Schedulers.elastic())
                .subscribe();

            assertThatCode(() -> new ResilientClusterProvider(cassandraExtension.clusterConfiguration().build(),
                    CassandraConsistenciesConfiguration.DEFAULT)
                .get())
                .doesNotThrowAnyException();
        } finally {
            cassandraExtension.unpause();
        }
    }
}