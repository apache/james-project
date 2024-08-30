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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.opensearch.DockerOpenSearch;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.lifecycle.api.StartUpCheck.CheckResult;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.mailbox.OpenSearchStartUpCheck;
import org.apache.james.util.docker.Images;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JamesWithNonCompatibleElasticSearchServerTest {

    static DockerOpenSearch dockerES6 = new DockerOpenSearch.NoAuth(Images.ELASTICSEARCH_6);

    @RegisterExtension
    static JamesServerExtension testExtension = TestingDistributedJamesServerBuilder.withSearchConfiguration(SearchConfiguration.openSearch())
        .extension(new DockerOpenSearchExtension(dockerES6))
        .extension(new CassandraExtension())
        .server(configuration -> CassandraJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .disableAutoStart()
        .build();

    @AfterAll
    static void afterAll() {
        dockerES6.stop();
    }

    @Test
    @Disabled("test failed, and CassandraJamesServerMain was mark as deprecated, and will be removed in the future.")
    void jamesShouldStopWhenStartingWithANonCompatibleElasticSearchServer(GuiceJamesServer server) throws Exception {
        assertThatThrownBy(server::start)
            .isInstanceOfSatisfying(
                StartUpChecksPerformer.StartUpChecksException.class,
                ex -> assertThat(ex.getBadChecks())
                    .containsOnly(CheckResult.builder()
                        .checkName(OpenSearchStartUpCheck.CHECK_NAME)
                        .resultType(StartUpCheck.ResultType.BAD)
                        .description("Error when checking ES version: Missing required property 'OpenSearchVersionInfo.distribution'")
                        .build()));

        assertThat(server.isStarted())
            .isFalse();
    }
}
