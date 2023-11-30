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

package org.apache.james.mpt.imapmailbox.postgres.host;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.quota.PostgresQuotaModule;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateModule;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class PostgresHostSystemExtension implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback, ParameterResolver {
    private final PostgresHostSystem hostSystem;
    private final PostgresExtension postgresExtension;

    public PostgresHostSystemExtension() {
        this.postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresModule.aggregateModules(
            PostgresMailboxAggregateModule.MODULE,
            PostgresQuotaModule.MODULE));
        try {
            hostSystem = PostgresHostSystem.build(postgresExtension);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        postgresExtension.afterEach(extensionContext);
        hostSystem.afterTest();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        postgresExtension.beforeEach(extensionContext);
        hostSystem.beforeTest();
    }

    public JamesImapHostSystem getHostSystem() {
        return hostSystem;
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        postgresExtension.afterAll(extensionContext);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        postgresExtension.beforeAll(extensionContext);
        hostSystem.beforeAll();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return false;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return postgresExtension;
    }
}
