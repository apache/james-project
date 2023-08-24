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

import static org.apache.james.jmap.api.upload.UploadUsageRepositoryContract.USER_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.jmap.api.upload.UploadUsageRepository;
import org.apache.james.jmap.api.upload.UploadUsageRepositoryContract;
import org.apache.james.mailbox.cassandra.modules.CassandraQuotaModule;
import org.apache.james.mailbox.cassandra.quota.CassandraQuotaCurrentValueDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

public class CassandraUploadUsageRepositoryTest implements UploadUsageRepositoryContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(CassandraQuotaModule.MODULE));

    private CassandraUploadUsageRepository cassandraUploadUsageRepository;

    @BeforeEach
    private void setup() {
        cassandraUploadUsageRepository = new CassandraUploadUsageRepository(new CassandraQuotaCurrentValueDao(cassandraCluster.getCassandraCluster().getConf()));
        resetCounterToZero();
    }

    private void resetCounterToZero() {
        Mono.from(cassandraUploadUsageRepository.increaseSpace(USER_NAME(), QuotaSizeUsage.size(0))).block();
        QuotaSizeUsage quotaSizeUsage = Mono.from(cassandraUploadUsageRepository.getSpaceUsage(USER_NAME())).block();
        Mono.from(cassandraUploadUsageRepository.decreaseSpace(USER_NAME(), quotaSizeUsage)).block();
        QuotaSizeUsage actual = Mono.from(cassandraUploadUsageRepository.getSpaceUsage(USER_NAME())).block();
        assertThat(actual.asLong()).isEqualTo(0l);
    }

    @Override
    public UploadUsageRepository uploadUsageRepository() {
        return cassandraUploadUsageRepository;
    }
}
