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

package org.apache.james.webadmin.routes;

import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.quota.search.QuotaSearchTestSystem;
import org.apache.james.quota.search.scanning.ClauseConverter;
import org.apache.james.quota.search.scanning.ScanningQuotaSearcher;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class ScanningQuotaSearchExtension implements ParameterResolver, BeforeEachCallback, AfterEachCallback {
    private static final Runnable NO_AWAIT = () -> { };

    private WebAdminQuotaSearchTestSystem restQuotaSearchTestSystem;

    @Override
    public void beforeEach(ExtensionContext context) {
        try {
            InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();

            MemoryDomainList domainList = new MemoryDomainList();
            MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);


            QuotaComponents quotaComponents = resources.getMailboxManager().getQuotaComponents();

            QuotaSearchTestSystem quotaSearchTestSystem = new QuotaSearchTestSystem(
                quotaComponents.getMaxQuotaManager(),
                resources.getMailboxManager(),
                quotaComponents.getQuotaManager(),
                resources.getDefaultUserQuotaRootResolver(),
                new ScanningQuotaSearcher(usersRepository,
                    new ClauseConverter(resources.getDefaultUserQuotaRootResolver(), quotaComponents.getQuotaManager())),
                usersRepository,
                domainList,
                resources.getCurrentQuotaManager(),
                NO_AWAIT);

            restQuotaSearchTestSystem = new WebAdminQuotaSearchTestSystem(quotaSearchTestSystem);
        } catch (Exception e) {
            throw new ParameterResolutionException("Error while resolving parameter", e);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        restQuotaSearchTestSystem.getWebAdminServer().destroy();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == WebAdminQuotaSearchTestSystem.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return restQuotaSearchTestSystem;
    }
}
