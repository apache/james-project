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

import org.apache.james.data.LdapUsersRepositoryModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.modules.blobstore.BlobStoreModulesChooser;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.server.core.configuration.Configuration;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class CassandraRabbitMQLdapJamesServerMain implements JamesServerMain {
    public static final Module MODULES = Modules
        .override(CassandraRabbitMQJamesServerMain.MODULES)
        .with(new LdapUsersRepositoryModule());

    public static void main(String[] args) throws Exception {
        Configuration configuration = Configuration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        BlobStoreConfiguration blobStoreConfiguration = BlobStoreConfiguration.parse(configuration);

        Module baseModule = baseModule(blobStoreConfiguration);
        JamesServerMain.main(configuration,
            baseModule, new JMXServerModule());
    }

    public static Module baseModule(BlobStoreConfiguration blobStoreConfiguration) {
        return Modules.combine(ImmutableList.<Module>builder()
            .add(MODULES)
            .addAll(BlobStoreModulesChooser.chooseModules(blobStoreConfiguration))
            .build());
    }
}
