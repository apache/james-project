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

import org.apache.james.jmap.draft.JmapJamesServerContract;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.junit.jupiter.api.extension.RegisterExtension;

class WithScanningSearchImmutableTest implements JmapJamesServerContract, JamesServerConcreteContract {
    static JamesServerBuilder<CassandraRabbitMQJamesConfiguration> baseExtension() {
        return new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
            CassandraRabbitMQJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .blobStore(BlobStoreConfiguration.builder()
                    .s3()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig())
                .searchConfiguration(SearchConfiguration.scanning())
                .build())
            .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
                .overrideWith(new TestJMAPServerModule()))
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .extension(new AwsS3BlobStoreExtension());
    }

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = baseExtension()
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();
}