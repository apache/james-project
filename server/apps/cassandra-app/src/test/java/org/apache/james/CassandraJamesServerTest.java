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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.jmap.JmapJamesServerContract;
import org.apache.james.modules.ConfigurationProbe;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraJamesServerTest implements JamesServerConcreteContract, JmapJamesServerContract {
    @RegisterExtension
    static JamesServerExtension testExtension = TestingDistributedJamesServerBuilder.withSearchConfiguration(SearchConfiguration.openSearch())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .server(configuration -> CassandraJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @Test
    void moveBatchSizeShouldEqualsConfigurationValue(GuiceJamesServer jamesServer) {
        int moveBatchSize = jamesServer.getProbe(ConfigurationProbe.class).getMoveBatchSize();
        assertThat(moveBatchSize).isEqualTo(100);
    }

    @Test
    void copyBatchSizeShouldEqualsConfigurationValue(GuiceJamesServer jamesServer) {
        int copyBatchSize = jamesServer.getProbe(ConfigurationProbe.class).getCopyBatchSize();
        assertThat(copyBatchSize).isEqualTo(100);
    }
}
