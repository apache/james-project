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

package org.apache.james.mailrepository.memory;

import org.apache.james.mailrepository.api.EmptyErrorMailRepositoryHealthCheckContract;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.BeforeEach;

public class MemoryEmptyErrorMailRepositoryHealthCheckTest implements EmptyErrorMailRepositoryHealthCheckContract {

    private MemoryMailRepositoryStore repositoryStore;

    @BeforeEach
    void setup() throws Exception {
        Configuration.Basic configuration = Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();

        FileSystemImpl fileSystem = new FileSystemImpl(configuration.directories());

        MailRepositoryStoreConfiguration storeConfiguration = MailRepositoryStoreConfiguration.parse(
            new FileConfigurationProvider(fileSystem, configuration).getConfiguration("mailrepositorystore"));

        repositoryStore = new MemoryMailRepositoryStore(new MemoryMailRepositoryUrlStore(),
            new SimpleMailRepositoryLoader(),
            storeConfiguration);
        repositoryStore.init();
    }

    @Override
    public MailRepositoryStore repositoryStore() {
        return repositoryStore;
    }

    @Override
    public void createRepository() {
        try {
            repositoryStore.create(MailRepositoryUrl.fromPathAndProtocol(ERROR_REPOSITORY_PATH, "memory1"));
        } catch (MailRepositoryStore.MailRepositoryStoreException e) {
            throw new RuntimeException(e);
        }
    }
}
