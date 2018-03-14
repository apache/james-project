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

package org.apache.james.mailrepository;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.file.FileMailRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

public class FileMailRepositoryTest {

    public abstract class GenericFileMailRepositoryTest implements MailRepositoryContract {
        private FileMailRepository mailRepository;
        private MockFileSystem filesystem;

        @BeforeEach
        void init() throws Exception {
            filesystem = new MockFileSystem();
            mailRepository = new FileMailRepository();
            mailRepository.setFileSystem(filesystem);
            mailRepository.configure(getConfiguration());
            mailRepository.init();
        }

        protected DefaultConfigurationBuilder getConfiguration() {
            DefaultConfigurationBuilder configuration = new DefaultConfigurationBuilder();
            configuration.addProperty("[@destinationURL]", "file://target/var/mailRepository");
            return withConfigurationOptions(configuration);
        }

        protected abstract DefaultConfigurationBuilder withConfigurationOptions(DefaultConfigurationBuilder configuration);

        @AfterEach
        void tearDown() {
            filesystem.clear();
        }

        @Override
        public MailRepository retrieveRepository() {
            return mailRepository;
        }
    }

    @Nested
    @DisplayName("Default configuration")
    public class DefaultFileMailRepositoryTest extends GenericFileMailRepositoryTest {

        @Override
        protected DefaultConfigurationBuilder withConfigurationOptions(DefaultConfigurationBuilder configuration) {
            configuration.addProperty("[@FIFO]", "false");
            configuration.addProperty("[@CACHEKEYS]", "true");
            return configuration;
        }
    }

    @Nested
    @DisplayName("No cache configuration")
    public class NoCacheFileMailRepositoryTest extends GenericFileMailRepositoryTest {

        @Override
        protected DefaultConfigurationBuilder withConfigurationOptions(DefaultConfigurationBuilder configuration) {
            configuration.addProperty("[@FIFO]", "false");
            configuration.addProperty("[@CACHEKEYS]", "false");
            return configuration;
        }
    }

    @Nested
    @DisplayName("Fifo configuration")
    public class FifoFileMailRepositoryTest extends GenericFileMailRepositoryTest {

        @Override
        protected DefaultConfigurationBuilder withConfigurationOptions(DefaultConfigurationBuilder configuration) {
            configuration.addProperty("[@FIFO]", "true");
            configuration.addProperty("[@CACHEKEYS]", "true");
            return configuration;
        }
    }

    @Nested
    @DisplayName("Fifo no cache configuration")
    public class FifoNoCacheFileMailRepositoryTest extends GenericFileMailRepositoryTest {

        @Override
        protected DefaultConfigurationBuilder withConfigurationOptions(DefaultConfigurationBuilder configuration) {
            configuration.addProperty("[@FIFO]", "true");
            configuration.addProperty("[@CACHEKEYS]", "false");
            return configuration;
        }
    }

}
