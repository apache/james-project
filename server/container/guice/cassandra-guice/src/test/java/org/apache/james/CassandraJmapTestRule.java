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

import java.io.IOException;

import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestDockerESMetricReporterModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.Module;

public class CassandraJmapTestRule implements TestRule {

    private static final int LIMIT_TO_10_MESSAGES = 10;
    private final TemporaryFolder temporaryFolder;

    public static CassandraJmapTestRule defaultTestRule() {
        return new CassandraJmapTestRule();
    }

    private final GuiceModuleTestRule guiceModuleTestRule;
    private final DockerElasticSearchRule dockerElasticSearchRule;

    public CassandraJmapTestRule(GuiceModuleTestRule... guiceModuleTestRule) {
        TempFilesystemTestRule tempFilesystemTestRule = new TempFilesystemTestRule();
        this.dockerElasticSearchRule = new DockerElasticSearchRule();
        this.temporaryFolder = tempFilesystemTestRule.getTemporaryFolder();
        this.guiceModuleTestRule =
                AggregateGuiceModuleTestRule
                    .of(guiceModuleTestRule)
                    .aggregate(dockerElasticSearchRule)
                    .aggregate(tempFilesystemTestRule);
    }

    public GuiceJamesServer jmapServer(Module... additionals) throws IOException {
        Configuration configuration = Configuration.builder()
            .workingDirectory(temporaryFolder.newFolder())
            .configurationFromClasspath()
            .build();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
            .overrideWith(new TestDockerESMetricReporterModule(dockerElasticSearchRule.getDockerEs().getHttpHost()))
            .overrideWith(guiceModuleTestRule.getModule())
            .overrideWith((binder -> binder.bind(CleanupTasksPerformer.class).asEagerSingleton()))
            .overrideWith(binder -> binder.bind(WebAdminConfiguration.class).toInstance(WebAdminConfiguration.TEST_CONFIGURATION))
            .overrideWith(additionals);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return guiceModuleTestRule.apply(base, description);
    }

    public void await() {
        guiceModuleTestRule.await();
    }
}
