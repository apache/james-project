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

import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.modules.CommonServicesModule;
import org.apache.james.modules.blobstore.BlobStoreCacheModulesChooser;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.modules.blobstore.BlobStoreModulesChooser;
import org.apache.james.modules.data.CassandraSieveQuotaLegacyModule;
import org.apache.james.modules.data.CassandraSieveQuotaModule;
import org.apache.james.modules.data.CassandraUsersRepositoryModule;
import org.apache.james.modules.mailbox.CassandraMailboxQuotaLegacyModule;
import org.apache.james.modules.mailbox.CassandraMailboxQuotaModule;
import org.apache.james.utils.InitializationOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;

/**
 * Alternate entrypoint for the distributed server that rebuilds messages from the blob store alone.
 *
 * <p>It reuses the production mailbox, DAO and blob store Guice modules (hence the existing
 * {@code blobstore.properties}: AES encryption and compression are applied transparently) but starts
 * neither the protocol servers nor RabbitMQ: the in-VM event bus is used because the RabbitMQ override
 * of {@link CassandraRabbitMQJamesServerMain} is intentionally not applied.</p>
 *
 * <p>Run it by overriding the docker entrypoint main class, e.g.:</p>
 * <pre>
 * docker run --rm --entrypoint java \
 *   -v /path/conf:/root/conf \
 *   apache/james:distributed-latest \
 *   -Dworking.directory=/root -Dextra.props=/root/conf/jvm.properties \
 *   -cp '/app/resources:/app/classes:/app/libs/*' \
 *   org.apache.james.S3RecoveryMain --restore-after=2026-01-01T00:00:00Z
 * </pre>
 */
public class S3RecoveryMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3RecoveryMain.class);

    public static void main(String[] args) throws Exception {
        ExtraProperties.initialize();

        CassandraRabbitMQJamesConfiguration configuration = CassandraRabbitMQJamesConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();
        RecoveryConfiguration recoveryConfiguration = RecoveryConfiguration.parse(args);

        LOGGER.info("Loading configuration {}", configuration);
        Injector injector = Guice.createInjector(recoveryModule(configuration, recoveryConfiguration));
        injector.getInstance(InitializationOperations.class).initModules();

        S3RecoveryService.Report report = injector.getInstance(S3RecoveryService.class).run().block();
        LOGGER.info("S3 recovery completed: {}", report);

        System.exit(0);
    }

    private static Module recoveryModule(CassandraRabbitMQJamesConfiguration configuration,
                                         RecoveryConfiguration recoveryConfiguration) {
        BlobStoreConfiguration blobStoreConfiguration = configuration.blobStoreConfiguration();

        return Modules.combine(ImmutableList.<Module>builder()
            .add(new CommonServicesModule(configuration))
            .add(CassandraRabbitMQJamesServerMain.CASSANDRA_SERVER_CORE_MODULE)
            .add(CassandraRabbitMQJamesServerMain.CASSANDRA_MAILBOX_MODULE)
            .add(chooseQuotaModule(configuration))
            .addAll(new UsersRepositoryModuleChooser(new CassandraUsersRepositoryModule())
                .chooseModules(configuration.getUsersRepositoryImplementation()))
            .addAll(BlobStoreModulesChooser.chooseModules(blobStoreConfiguration))
            .addAll(BlobStoreCacheModulesChooser.chooseModules(blobStoreConfiguration))
            .add(new RecoveryModule(recoveryConfiguration))
            .build());
    }

    private static Module chooseQuotaModule(CassandraRabbitMQJamesConfiguration configuration) {
        if (configuration.isQuotaCompatibilityMode()) {
            return Modules.combine(new CassandraMailboxQuotaLegacyModule(), new CassandraSieveQuotaLegacyModule());
        }
        return Modules.combine(new CassandraMailboxQuotaModule(), new CassandraSieveQuotaModule());
    }

    static class RecoveryModule extends AbstractModule {
        private final RecoveryConfiguration recoveryConfiguration;

        RecoveryModule(RecoveryConfiguration recoveryConfiguration) {
            this.recoveryConfiguration = recoveryConfiguration;
        }

        @Override
        protected void configure() {
            bind(RecoveryConfiguration.class).toInstance(recoveryConfiguration);
            bind(S3RecoveryService.class).in(Scopes.SINGLETON);
        }
    }
}
