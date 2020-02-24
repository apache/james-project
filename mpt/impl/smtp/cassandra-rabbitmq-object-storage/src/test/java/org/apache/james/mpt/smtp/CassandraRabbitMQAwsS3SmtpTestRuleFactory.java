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
package org.apache.james.mpt.smtp;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.CassandraJamesServerMain;
import org.apache.james.CleanupTasksPerformer;
import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.cassandra.DockerCassandra;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.backends.rabbitmq.DockerRabbitMQSingleton;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.modules.TestAwsS3BlobStoreModule;
import org.apache.james.modules.TestRabbitMQModule;
import org.apache.james.modules.blobstore.BlobStoreChoosingModule;
import org.apache.james.modules.objectstorage.aws.s3.DockerAwsS3TestRule;
import org.apache.james.modules.protocols.SmtpGuiceProbe.SmtpServerConnectedType;
import org.apache.james.modules.rabbitmq.RabbitMQModule;
import org.apache.james.modules.server.CamelMailetContainerModule;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.util.Host;
import org.junit.rules.TemporaryFolder;

public final class CassandraRabbitMQAwsS3SmtpTestRuleFactory {
    public static SmtpTestRule create(SmtpServerConnectedType smtpServerConnectedType, Host cassandraHost, DockerAwsS3TestRule awsS3TestRule) {
        SmtpTestRule.ServerBuilder createJamesServer = (folder, dnsService) -> createJamesServer(cassandraHost, awsS3TestRule, folder, dnsService);

        return new SmtpTestRule(smtpServerConnectedType, createJamesServer);
    }

    private static GuiceJamesServer createJamesServer(Host cassandraHost, DockerAwsS3TestRule awsS3TestRule, TemporaryFolder folder, DNSService dnsService) throws Exception {
        Configuration configuration = Configuration.builder()
            .workingDirectory(folder.newFolder())
            .configurationFromClasspath()
            .build();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(CassandraJamesServerMain.CASSANDRA_SERVER_CORE_MODULE,
                SmtpTestRule.SMTP_PROTOCOL_MODULE,
                binder -> binder.bind(MailQueueItemDecoratorFactory.class).to(RawMailQueueItemDecoratorFactory.class),
                binder -> binder.bind(CamelMailetContainerModule.DefaultProcessorsConfigurationSupplier.class)
                    .toInstance(BaseHierarchicalConfiguration::new))
            .overrideWith(
                new RabbitMQModule(),
                new BlobStoreChoosingModule())
            .overrideWith(
                new TestRabbitMQModule(DockerRabbitMQSingleton.SINGLETON),
                new TestAwsS3BlobStoreModule(awsS3TestRule),
                binder -> binder.bind(ClusterConfiguration.class).toInstance(
                    DockerCassandra.configurationBuilder(cassandraHost)
                        .build()),
                binder -> binder.bind(DNSService.class).toInstance(dnsService),
                binder -> binder.bind(CleanupTasksPerformer.class).asEagerSingleton());
    }
}

