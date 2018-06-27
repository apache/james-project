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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.junit.Test;

public class CassandraConfigurationReadingTest {

    @Test
    public void provideCassandraConfigurationShouldReturnDefaultOnEmptyConfigurationFile() {
        CassandraConfiguration configuration = CassandraConfiguration.from(new PropertiesConfiguration());

        assertThat(configuration).isEqualTo(CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    @Test
    public void provideCassandraConfigurationShouldReturnRightConfigurationFile() throws ConfigurationException {
        CassandraConfiguration configuration = CassandraConfiguration.from(new PropertiesConfiguration(
            ClassLoader.getSystemResource("cassandra.properties")));

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
                .attachmentV2MigrationReadTimeout(11)
                .messageAttachmentIdsReadTimeout(12)
                .build());
    }

}
