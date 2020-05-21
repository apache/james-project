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

import java.io.IOException;

import org.apache.james.backends.rabbitmq.DockerRabbitMQSingleton;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestDockerESMetricReporterModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.TestRabbitMQModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.modules.objectstorage.swift.DockerSwiftTestRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.Module;

public class CassandraRabbitMQSwiftJmapTestRule implements TestRule {

    public static final int TWO_SECONDS = 2000;
    private final TemporaryFolder temporaryFolder;

    public static CassandraRabbitMQSwiftJmapTestRule defaultTestRule() {
        return new CassandraRabbitMQSwiftJmapTestRule();
    }

    private final GuiceModuleTestRule guiceModuleTestRule;
    private final DockerElasticSearchRule dockerElasticSearchRule;

    public CassandraRabbitMQSwiftJmapTestRule(GuiceModuleTestRule... guiceModuleTestRule) {
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
        CassandraRabbitMQJamesConfiguration configuration = CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(temporaryFolder.newFolder())
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.objectStorage().disableCache())
            .build();

        return CassandraRabbitMQJamesServerMain.createServer(configuration)
            .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
            .overrideWith(new TestRabbitMQModule(DockerRabbitMQSingleton.SINGLETON))
            .overrideWith(new DockerSwiftTestRule().getModule())
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(new TestDockerESMetricReporterModule(dockerElasticSearchRule.getDockerEs().getHttpHost()))
            .overrideWith(guiceModuleTestRule.getModule())
            .overrideWith((binder -> binder.bind(CleanupTasksPerformer.class).asEagerSingleton()))
            .overrideWith(additionals);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return guiceModuleTestRule.apply(base, description);
    }

    public void await() {
        awaitProcessingStart();
        guiceModuleTestRule.await();
    }

    private void awaitProcessingStart() {
        // As the RabbitMQEventBus is asynchronous we have otherwise no guaranties that the processing to be awaiting for did start
        try {
            Thread.sleep(TWO_SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
