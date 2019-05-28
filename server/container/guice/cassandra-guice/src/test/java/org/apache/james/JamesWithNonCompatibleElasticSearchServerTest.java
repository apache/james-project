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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.es.DockerElasticSearch;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.util.docker.Images;
import org.elasticsearch.ElasticsearchStatusException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JamesWithNonCompatibleElasticSearchServerTest {

    private static final int LIMIT_MAX_MESSAGES = 10;

    static DockerElasticSearch dockerES2 = new DockerElasticSearch(Images.ELASTICSEARCH_2);

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder()
        .extension(new DockerElasticSearchExtension(dockerES2))
        .extension(new CassandraExtension())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestJMAPServerModule(LIMIT_MAX_MESSAGES)))
        .disableAutoStart()
        .build();

    @AfterAll
    static void afterAll() {
        dockerES2.stop();
    }

    @Test
    void jamesShouldStopWhenStartingWithANonCompatibleElasticSearchServer(GuiceJamesServer server) throws Exception {
        assertThatThrownBy(server::start)
            .isInstanceOf(ElasticsearchStatusException.class);

        assertThat(server.isStarted())
            .isFalse();
    }
}
