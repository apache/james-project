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
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraRabbitMQLegacyQuotaJamesServerTest implements JamesServerConcreteContract, JmapJamesServerContract {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
        CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.openSearch())
            .quotaCompatibilityModeEnabled(true)
            .build())
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();
}
