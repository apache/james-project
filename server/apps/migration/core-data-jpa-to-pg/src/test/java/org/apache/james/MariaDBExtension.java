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

import java.util.UUID;

import org.apache.james.backends.jpa.JPAConfiguration;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.output.OutputFrame;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class MariaDBExtension implements GuiceModuleTestExtension {

    private static final Logger logger = LoggerFactory.getLogger(MariaDBExtension.class);

    private static void displayDockerLog(OutputFrame outputFrame) {
        logger.info(outputFrame.getUtf8String().trim());
    }

    private final MariaDBContainer container;
    private JPAConfiguration jpaConfiguration;

    public MariaDBExtension() {
        container = new MariaDBContainer<>("mariadb:10.6")
                .withLogConsumer(MariaDBExtension::displayDockerLog)
                .withReuse(true);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        container.start();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        container.stop();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        var dbName = UUID.randomUUID().toString().replace('-', '_');
        String script = String.format("create database %s; grant all on %s.* to %s", dbName, dbName, container.getUsername());
        container.execInContainer("mariadb", "-u", "root", "--password=" + container.getPassword(), "--execute", script);
        container.withDatabaseName(dbName);
        jpaConfiguration = JPAConfiguration.builder()
                .driverName(container.getDriverClassName())
                .driverURL(container.getJdbcUrl())
                .username(container.getUsername())
                .password(container.getPassword())
                .build();
    }

    @Override
    public Module getModule() {
        return Modules.combine(binder -> binder.bind(JPAConfiguration.class)
                .toInstance(jpaConfiguration));
    }
}
