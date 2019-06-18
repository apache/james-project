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

import static org.apache.james.CassandraJamesServerMain.ALL_BUT_JMX_CASSANDRA_MODULE;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.ConfigurationProbe;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraJamesServerTest implements JamesServerContract {
    private static final int LIMIT_TO_10_MESSAGES = 10;

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder()
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
            .overrideWith(DOMAIN_LIST_CONFIGURATION_MODULE))
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
