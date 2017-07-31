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

package org.apache.james.modules.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.cassandra.CassandraConfiguration;
import org.junit.Before;
import org.junit.Test;

public class CassandraSessionModuleTest {

    private CassandraSessionModule cassandraSessionModule;

    @Before
    public void setUp() {
        cassandraSessionModule = new CassandraSessionModule();
    }

    @Test
    public void provideCassandraConfigurationShouldReturnDefaultOnEmptyConfigurationFile() throws ConfigurationException {
        CassandraConfiguration configuration = cassandraSessionModule.provideCassandraConfiguration(PropertiesConfiguration::new);

        assertThat(configuration).isEqualTo(CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    @Test
    public void provideCassandraConfigurationShouldReturnRightConfigurationFile() throws ConfigurationException {
        CassandraConfiguration configuration = cassandraSessionModule.provideCassandraConfiguration(
            () -> new PropertiesConfiguration(ClassLoader.getSystemResource("modules/mailbox/cassandra.properties")));

        assertThat(configuration)
            .isEqualTo(CassandraConfiguration.builder()
                .aclMaxRetry(1)
                .modSeqMaxRetry(2)
                .uidMaxRetry(3)
                .flagsUpdateMessageMaxRetry(4)
                .flagsUpdateMessageIdMaxRetry(5)
                .fetchNextPageInAdvanceRow(6)
                .flagsUpdateChunkSize(7)
                .messageReadChunkSize(8)
                .expungeChunkSize(9)
                .blobPartSize(10)
                .onTheFlyV1ToV2Migration(true)
                .v1ToV2ThreadCount(11)
                .v1ToV2QueueLength(12)
                .v1ReadFetchSize(13)
                .build());
    }

}
