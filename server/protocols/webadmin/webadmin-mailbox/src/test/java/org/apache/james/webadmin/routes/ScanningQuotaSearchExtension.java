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

import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.quota.search.QuotaSearchTestSystem;
import org.apache.james.quota.search.scanning.ClauseConverter;
import org.apache.james.quota.search.scanning.ScanningQuotaSearcher;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.Routes;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.jayway.restassured.specification.RequestSpecification;

public class ScanningQuotaSearchExtension implements ParameterResolver, BeforeEachCallback, AfterEachCallback {
    private static final Runnable NO_AWAIT = () -> {};

    private final List<Function<QuotaSearchTestSystem, Routes>> routesGenerators;
    private WebAdminQuotaSearchTestSystem restQuotaSearchTestSystem;
    private QuotaSearchTestSystem quotaSearchTestSystem;

    public ScanningQuotaSearchExtension(Function<QuotaSearchTestSystem, Routes>... routesGenerators) {
        this.routesGenerators = ImmutableList.copyOf(routesGenerators);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        try {
            InMemoryIntegrationResources.Resources resources = new InMemoryIntegrationResources().createResources(new SimpleGroupMembershipResolver());

            MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting();

            DNSService dnsService = mock(DNSService.class);
            MemoryDomainList domainList = new MemoryDomainList(dnsService);
            domainList.configure(new DefaultConfigurationBuilder());
            usersRepository.setDomainList(domainList);

            quotaSearchTestSystem = new QuotaSearchTestSystem(
                resources.getMaxQuotaManager(),
                resources.getMailboxManager(),
                resources.getQuotaManager(),
                resources.getQuotaRootResolver(),
                new ScanningQuotaSearcher(usersRepository,
                    new ClauseConverter(resources.getQuotaRootResolver(), resources.getQuotaManager())),
                usersRepository,
                domainList,
                resources.getCurrentQuotaManager(),
                NO_AWAIT);

            List<Routes> routes = routesGenerators.stream()
                .map(generator -> generator.apply(quotaSearchTestSystem))
                .collect(Guavate.toImmutableList());

            restQuotaSearchTestSystem = new WebAdminQuotaSearchTestSystem(quotaSearchTestSystem, routes);
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

    public QuotaSearchTestSystem getQuotaSearchTestSystem() {
        return quotaSearchTestSystem;
    }

    public RequestSpecification getRequestSpecification() {
        return restQuotaSearchTestSystem.getRequestSpecification();
    }
}
