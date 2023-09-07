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

package org.apache.james.jmap.cassandra.upload;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaModule;
import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao;
import org.apache.james.jmap.api.upload.UploadUsageRepository;
import org.apache.james.jmap.api.upload.UploadUsageRepositoryContract;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxQuotaModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraUploadUsageRepositoryTest implements UploadUsageRepositoryContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(CassandraMailboxQuotaModule.MODULE, CassandraMutualizedQuotaModule.MODULE));

    private CassandraUploadUsageRepository cassandraUploadUsageRepository;

    @BeforeEach
    private void setup() {
        cassandraUploadUsageRepository = new CassandraUploadUsageRepository(new CassandraQuotaCurrentValueDao(cassandraCluster.getCassandraCluster().getConf()));
        resetCounterToZero();
    }

    @Override
    public UploadUsageRepository uploadUsageRepository() {
        return cassandraUploadUsageRepository;
    }
}
