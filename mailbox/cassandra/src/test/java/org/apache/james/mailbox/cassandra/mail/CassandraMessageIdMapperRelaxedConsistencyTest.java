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

package org.apache.james.mailbox.cassandra.mail;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.mailbox.store.mail.model.MessageIdMapperTest;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMessageIdMapperRelaxedConsistencyTest {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MailboxAggregateModule.MODULE);

    @Nested
    class WeakReadConsistency extends MessageIdMapperTest {
        private CassandraMapperProvider mapperProvider;

        @Override
        protected CassandraMapperProvider provideMapper() {
            mapperProvider = new CassandraMapperProvider(
                cassandraCluster.getCassandraCluster(),
                CassandraConfiguration.builder()
                    .messageReadStrongConsistency(false)
                    .messageWriteStrongConsistency(true)
                    .build());
            return mapperProvider;
        }

        @Override
        protected UpdatableTickingClock updatableTickingClock() {
            return mapperProvider.getUpdatableTickingClock();
        }
    }

    @Nested
    class WeakWriteConsistency extends MessageIdMapperTest {
        private CassandraMapperProvider mapperProvider;

        @Override
        protected CassandraMapperProvider provideMapper() {
            mapperProvider = new CassandraMapperProvider(
                cassandraCluster.getCassandraCluster(),
                CassandraConfiguration.builder()
                    .messageReadStrongConsistency(false)
                    .messageWriteStrongConsistency(false)
                    .build());
            return mapperProvider;
        }

        @Override
        protected UpdatableTickingClock updatableTickingClock() {
            return mapperProvider.getUpdatableTickingClock();
        }

        @Disabled("JAMES-3435 Without strong consistency flags update is not thread safe as long as it follows a read-before-write pattern")
        @Override
        public void setFlagsShouldWorkWithConcurrencyWithAdd() throws Exception {
            super.setFlagsShouldWorkWithConcurrencyWithAdd();
        }

        @Disabled("JAMES-3435 Without strong consistency flags update is not thread safe as long as it follows a read-before-write pattern")
        @Override
        public void setFlagsShouldWorkWithConcurrencyWithRemove() throws Exception {
            super.setFlagsShouldWorkWithConcurrencyWithRemove();
        }
    }
}
