/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james;

import static org.apache.james.CassandraJamesServerMain.REQUIRE_TASK_MANAGER_MODULE;

import org.apache.james.modules.DistributedTaskManagerModule;
import org.apache.james.modules.TaskSerializationModule;
import org.apache.james.modules.blobstore.BlobStoreCacheModulesChooser;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.modules.blobstore.BlobStoreModulesChooser;
import org.apache.james.modules.event.RabbitMQEventBusModule;
import org.apache.james.modules.rabbitmq.RabbitMQModule;
import org.apache.james.modules.server.JMXServerModule;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class CassandraRabbitMQJamesServerMain implements JamesServerMain {
    protected static final Module MODULES =
        Modules
            .override(Modules.combine(REQUIRE_TASK_MANAGER_MODULE, new DistributedTaskManagerModule()))
            .with(new RabbitMQModule(), new RabbitMQEventBusModule(), new TaskSerializationModule());

    public static void main(String[] args) throws Exception {
        CassandraRabbitMQJamesConfiguration configuration = CassandraRabbitMQJamesConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        GuiceJamesServer server = createServer(configuration)
            .combineWith(new JMXServerModule());

        JamesServerMain.main(server);
    }

    public static GuiceJamesServer createServer(CassandraRabbitMQJamesConfiguration configuration) {
        BlobStoreConfiguration blobStoreConfiguration = configuration.blobStoreConfiguration();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(MODULES)
            .combineWith(BlobStoreModulesChooser.chooseModules(blobStoreConfiguration))
            .combineWith(BlobStoreCacheModulesChooser.chooseModules(blobStoreConfiguration));
    }
}
