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

package org.apache.james.xodus;

import java.io.Closeable;
import java.io.File;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.PersistentEntityStoreConfig;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.EnvironmentConfig;
import jetbrains.exodus.env.Environments;

public class XodusCommonModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(XodusCommonModule.class);
    private static final String DEFAULT_XODUS_PATH = "var/xodus";

    @Singleton
    public static class XodusHolder implements Closeable {
        private final Environment environment;
        private final PersistentEntityStore entityStore;

        @Inject
        public XodusHolder(ConfigurationProvider configurationProvider, FileSystem fileSystem) throws java.io.FileNotFoundException {
            String xodusPath = DEFAULT_XODUS_PATH;
            int cacheMemoryPercentage = 50;
            long logSyncPeriod = 2000L;
            boolean gcEnabled = true;
            int deduplicateBlobsEveryDays = 7;
            int deduplicateBlobsMinSize = 4096;
            long logFileSize = 16384L; // 16 MB in KB (universal profile for HDD sequential I/O & SSD wear reduction)
            int logCachePageSize = 65536; // 64 KB cache page size (optimal for OS/HDD read-ahead)
            int gcFileMinAge = 8; // Prevent I/O thrashing on HDD by letting active transactions settle
            int gcMinUtilization = 35; // Trigger GC when free space in logs exceeds 65%
            int gcRunPeriod = 30000; // ms between GC cycles; 30s reduces GC/write transaction contention at peak SMTP load
            int logCacheOpenFilesCount = 2000; // .xd files kept open; 2000 reduces open()/close() syscalls during IMAP FETCH

            try {
                Configuration conf = configurationProvider.getConfiguration("xodus");
                xodusPath = conf.getString("xodus.path", DEFAULT_XODUS_PATH);
                cacheMemoryPercentage = conf.getInt("xodus.entityStore.cacheMemoryPercentage", 50);
                logSyncPeriod = conf.getLong("xodus.log.syncPeriod", 2000L);
                gcEnabled = conf.getBoolean("xodus.gc.enabled", true);
                deduplicateBlobsEveryDays = conf.getInt("xodus.entityStore.refactoring.deduplicateBlobsEveryDays", 7);
                deduplicateBlobsMinSize = conf.getInt("xodus.entityStore.refactoring.deduplicateBlobsMinSize", 4096);
                logFileSize = conf.getLong("xodus.log.fileSize", 16384L);
                logCachePageSize = conf.getInt("xodus.log.cache.pageSize", 65536);
                gcFileMinAge = conf.getInt("xodus.gc.fileMinAge", 8);
                gcMinUtilization = conf.getInt("xodus.gc.minUtilization", 35);
                gcRunPeriod = conf.getInt("xodus.gc.runPeriod", 30000);
                logCacheOpenFilesCount = conf.getInt("xodus.log.cache.openFilesCount", 2000);
            } catch (ConfigurationException e) {
                LOGGER.info("xodus.properties not found, using default settings with path {}", DEFAULT_XODUS_PATH);
            }

            File envDir;
            if (new File(xodusPath).isAbsolute()) {
                envDir = new File(xodusPath);
            } else {
                envDir = new File(fileSystem.getBasedir(), xodusPath);
            }

            if (!envDir.exists()) {
                envDir.mkdirs();
            }
            LOGGER.info("Initializing native Xodus Environment at {}", envDir.getAbsolutePath());

            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setLogSyncPeriod(logSyncPeriod);
            envConfig.setGcEnabled(gcEnabled);
            envConfig.setLogFileSize(logFileSize);
            envConfig.setLogCachePageSize(logCachePageSize);
            envConfig.setLogCacheOpenFilesCount(logCacheOpenFilesCount);
            envConfig.setGcFileMinAge(gcFileMinAge);
            envConfig.setGcMinUtilization(gcMinUtilization);
            envConfig.setGcRunPeriod(gcRunPeriod);
            envConfig.setUseVersion1Format(false); // Enable modern Xodus V2 format (required for periodic in-place blob deduplication and bitmap indexes)

            PersistentEntityStoreConfig storeConfig = new PersistentEntityStoreConfig();
            storeConfig.setSetting(PersistentEntityStoreConfig.ENTITY_ITERABLE_CACHE_MEMORY_PERCENTAGE, cacheMemoryPercentage);
            storeConfig.setRefactoringDeduplicateBlobsEvery(deduplicateBlobsEveryDays);
            storeConfig.setRefactoringDeduplicateBlobsMinSize(deduplicateBlobsMinSize); // Deduplicate blobs >= minSize (default 4096 bytes / 4 KB)

            this.environment = Environments.newInstance(envDir, envConfig);
            this.entityStore = PersistentEntityStores.newInstance(storeConfig, this.environment, "jamesStore");
        }

        public Environment getEnvironment() {
            return environment;
        }

        public PersistentEntityStore getEntityStore() {
            return entityStore;
        }

        @Override
        @PreDestroy
        public void close() {
            LOGGER.info("Closing Xodus EntityStore and Environment");
            try {
                if (entityStore != null) {
                    entityStore.close();
                }
            } catch (Exception e) {
                LOGGER.warn("Error closing Xodus EntityStore", e);
            }
            try {
                if (environment != null && environment.isOpen()) {
                    environment.close();
                }
            } catch (Exception e) {
                LOGGER.warn("Error closing Xodus Environment", e);
            }
        }
    }

    @Override
    protected void configure() {
        bind(XodusHolder.class).asEagerSingleton();

        com.google.inject.multibindings.Multibinder.newSetBinder(binder(), org.apache.james.webadmin.Routes.class)
            .addBinding()
            .to(XodusAdminRoutes.class);
    }

    @Provides
    @Singleton
    Environment provideXodusEnvironment(XodusHolder holder) {
        return holder.getEnvironment();
    }

    @Provides
    @Singleton
    PersistentEntityStore provideXodusEntityStore(XodusHolder holder) {
        return holder.getEntityStore();
    }
}
