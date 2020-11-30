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

import static org.apache.james.modules.protocols.SmtpGuiceProbe.SmtpServerConnectedType.SMTP_GLOBAL_SERVER;

import org.apache.james.backends.cassandra.DockerCassandraExtension;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Extension;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.objectstorage.aws.s3.DockerAwsS3TestRule;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

public class AwsS3RabbitMQForwardSmtpTest implements ForwardSmtpTest {

    @RegisterExtension
    public static DockerCassandraExtension cassandraServer = new DockerCassandraExtension();

    @RegisterExtension
    public AwsS3BlobStoreExtension dockerAwsS3TestRule = new AwsS3BlobStoreExtension();

    @RegisterExtension
    public SmtpTestExtension cassandraRabbitMQAwsS3SmtpTestRule =
            CassandraRabbitMQAwsS3SmtpTestRuleFactory.createExtension(SMTP_GLOBAL_SERVER, () -> cassandraServer.getDockerCassandra().getHost(), dockerAwsS3TestRule);

    private SmtpHostSystem hostSystem;

    @BeforeEach
    void setup(SmtpHostSystem hostSystem) {
        this.hostSystem = hostSystem;
    }


    @Override
    public SmtpHostSystem hostSystem() {
        return hostSystem;
    }

}
