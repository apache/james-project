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

package org.apache.james.jmap.rabbitmq.cucumber.awss3;

import java.util.Arrays;

import javax.inject.Inject;

import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.memory.MemoryPersistenceAdapter;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.CleanupTasksPerformer;
import org.apache.james.DockerCassandraRule;
import org.apache.james.DockerElasticSearchRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.methods.integration.cucumber.ImapStepdefs;
import org.apache.james.jmap.methods.integration.cucumber.MainStepdefs;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.modules.DockerRabbitMQRule;
import org.apache.james.modules.TestDockerESMetricReporterModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.TestRabbitMQModule;
import org.apache.james.modules.objectstorage.aws.s3.DockerAwsS3TestRule;
import org.apache.james.server.CassandraTruncateTableTask;
import org.apache.james.server.core.configuration.Configuration;
import org.junit.rules.TemporaryFolder;

import com.github.fge.lambdas.runnable.ThrowingRunnable;
import com.google.inject.multibindings.Multibinder;

import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class RabbitMQAwsS3Stepdefs {

    private static final int JMAP_GET_MESSAGE_LIST_MAXIMUM_LIMIT = 10;
    private final MainStepdefs mainStepdefs;
    private final ImapStepdefs imapStepdefs;
    private final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final DockerCassandraRule cassandraServer = CucumberCassandraSingleton.cassandraServer;
    private final DockerElasticSearchRule elasticSearch = CucumberElasticSearchSingleton.elasticSearch;
    private final DockerRabbitMQRule rabbitMQServer = CucumberRabbitMQSingleton.rabbitMQServer;
    private final DockerAwsS3TestRule swiftServer = CucumberAwsS3Singleton.awsS3Server;

    @Inject
    private RabbitMQAwsS3Stepdefs(MainStepdefs mainStepdefs, ImapStepdefs imapStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.imapStepdefs = imapStepdefs;
    }

    @Before
    public void init() throws Exception {
        cassandraServer.start();
        rabbitMQServer.start();
        swiftServer.start();
        elasticSearch.start();

        temporaryFolder.create();
        mainStepdefs.messageIdFactory = new CassandraMessageId.Factory();
        Configuration configuration = Configuration.builder()
                .workingDirectory(temporaryFolder.newFolder())
                .configurationFromClasspath()
                .build();

        mainStepdefs.jmapServer = GuiceJamesServer.forConfiguration(configuration)
                .combineWith(CassandraRabbitMQJamesServerMain.MODULES)
                .overrideWith(new TestJMAPServerModule(JMAP_GET_MESSAGE_LIST_MAXIMUM_LIMIT))
                .overrideWith(new TestDockerESMetricReporterModule(elasticSearch.getDockerEs().getHttpHost()))
                .overrideWith(new TestRabbitMQModule(rabbitMQServer.dockerRabbitMQ()))
                .overrideWith(swiftServer.getModule())
                .overrideWith(elasticSearch.getModule())
                .overrideWith(cassandraServer.getModule())
                .overrideWith(binder -> binder.bind(TextExtractor.class).to(DefaultTextExtractor.class))
                .overrideWith((binder) -> binder.bind(PersistenceAdapter.class).to(MemoryPersistenceAdapter.class))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, CleanupTasksPerformer.CleanupTask.class).addBinding().to(CassandraTruncateTableTask.class))
                .overrideWith((binder -> binder.bind(CleanupTasksPerformer.class).asEagerSingleton()));
        mainStepdefs.awaitMethod = () -> elasticSearch.getDockerEs().awaitForElasticSearch();
        mainStepdefs.init();
    }

    @After
    public void tearDown() {
        ignoreFailures(imapStepdefs::closeConnections,
                mainStepdefs::tearDown,
                () -> elasticSearch.getDockerEs().cleanUpData(),
                () -> temporaryFolder.delete());
    }

    private void ignoreFailures(ThrowingRunnable... cleaners) {
        Arrays.stream(cleaners)
                .forEach(this::runSwallowingException);
    }

    private void runSwallowingException(Runnable run) {
        try {
            run.run();
        } catch (Exception e) {
            // ignore
        }
    }
}

