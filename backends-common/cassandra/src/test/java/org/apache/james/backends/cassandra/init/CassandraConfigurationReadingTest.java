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

import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.junit.jupiter.api.Test;

class CassandraConfigurationReadingTest {

    @Test
    void provideCassandraConfigurationShouldReturnDefaultOnEmptyConfigurationFile() {
        CassandraConfiguration configuration = CassandraConfiguration.from(new PropertiesConfiguration());

        assertThat(configuration).isEqualTo(CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    @Test
    void provideCassandraConfigurationShouldReturnRightConfigurationFile() throws ConfigurationException {
        FileBasedConfigurationBuilder<FileBasedConfiguration> builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
            .configure(new Parameters()
                .fileBased()
                .setURL(ClassLoader.getSystemResource("configuration-reader-test/cassandra.properties")));

        CassandraConfiguration configuration = CassandraConfiguration.from(builder.getConfiguration());

        assertThat(configuration)
            .isEqualTo(CassandraConfiguration.builder()
                .aclMaxRetry(1)
                .modSeqMaxRetry(2)
                .uidMaxRetry(3)
                .flagsUpdateMessageMaxRetry(4)
                .flagsUpdateMessageIdMaxRetry(5)
                .fetchNextPageInAdvanceRow(6)
                .messageReadChunkSize(7)
                .expungeChunkSize(8)
                .blobPartSize(9)
                .attachmentV2MigrationReadTimeout(10)
                .messageAttachmentIdsReadTimeout(11)
                .consistencyLevelRegular("LOCAL_QUORUM")
                .consistencyLevelLightweightTransaction("LOCAL_SERIAL")
                .build());
    }

}
