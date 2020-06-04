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

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.FailingPropertiesProvider;
import org.apache.james.utils.PropertiesProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DefaultCassandraJamesServerTest {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<CassandraJamesServerConfiguration>(tmpDir ->
        CassandraJamesServerConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.elasticSearch())
            .build())
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .server(configuration -> CassandraJamesServerMain.createServer(configuration)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> binder.bind(PropertiesProvider.class).to(FailingPropertiesProvider.class))
            .overrideWith(binder -> binder.bind(ConfigurationProvider.class).toInstance((s, l) -> new BaseHierarchicalConfiguration())))
        .build();

    @Test
    void cassandraJamesServerShouldStartWithNoConfigurationFile(GuiceJamesServer server) {
        assertThat(server.isStarted()).isTrue();
    }
}
