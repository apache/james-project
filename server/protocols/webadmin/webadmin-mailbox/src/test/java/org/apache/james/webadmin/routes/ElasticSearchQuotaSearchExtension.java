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

import java.io.IOException;

import org.apache.james.backends.es.v7.DockerElasticSearch;
import org.apache.james.backends.es.v7.DockerElasticSearchSingleton;
import org.apache.james.backends.es.v7.ElasticSearchConfiguration;
import org.apache.james.backends.es.v7.ElasticSearchIndexer;
import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.quota.search.QuotaSearchTestSystem;
import org.apache.james.quota.search.elasticsearch.v7.ElasticSearchQuotaSearcher;
import org.apache.james.quota.search.elasticsearch.v7.QuotaRatioElasticSearchConstants;
import org.apache.james.quota.search.elasticsearch.v7.QuotaSearchIndexCreationUtil;
import org.apache.james.quota.search.elasticsearch.v7.UserRoutingKeyFactory;
import org.apache.james.quota.search.elasticsearch.v7.events.ElasticSearchQuotaMailboxListener;
import org.apache.james.quota.search.elasticsearch.v7.json.QuotaRatioToElasticSearchJson;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.rules.TemporaryFolder;

public class ElasticSearchQuotaSearchExtension implements ParameterResolver, BeforeEachCallback, AfterEachCallback {

    private final DockerElasticSearch elasticSearch = DockerElasticSearchSingleton.INSTANCE;
    private WebAdminQuotaSearchTestSystem restQuotaSearchTestSystem;
    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private ReactorElasticSearchClient client;

    @Override
    public void beforeEach(ExtensionContext context) {
        try {
            temporaryFolder.create();
            elasticSearch.start();

            client = QuotaSearchIndexCreationUtil.prepareDefaultClient(
                elasticSearch.clientProvider().get(),
                ElasticSearchConfiguration.builder()
                    .addHost(elasticSearch.getHttpHost())
                    .build());

            InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();


            DNSService dnsService = mock(DNSService.class);
            MemoryDomainList domainList = new MemoryDomainList(dnsService);
            domainList.configure(DomainListConfiguration.DEFAULT);
            MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

            ElasticSearchQuotaMailboxListener listener = new ElasticSearchQuotaMailboxListener(
                new ElasticSearchIndexer(client, QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS),
                new QuotaRatioToElasticSearchJson(),
                new UserRoutingKeyFactory());

            resources.getMailboxManager().getEventBus().register(listener);

            QuotaComponents quotaComponents = resources.getMailboxManager().getQuotaComponents();

            QuotaSearchTestSystem quotaSearchTestSystem = new QuotaSearchTestSystem(
                quotaComponents.getMaxQuotaManager(),
                resources.getMailboxManager(),
                quotaComponents.getQuotaManager(),
                resources.getDefaultUserQuotaRootResolver(),
                new ElasticSearchQuotaSearcher(client,
                    QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS),
                usersRepository,
                domainList,
                resources.getCurrentQuotaManager(),
                elasticSearch::flushIndices);

            restQuotaSearchTestSystem = new WebAdminQuotaSearchTestSystem(quotaSearchTestSystem);
        } catch (Exception e) {
            throw new ParameterResolutionException("Error while resolving parameter", e);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws IOException {
        restQuotaSearchTestSystem.getWebAdminServer().destroy();
        client.close();
        elasticSearch.cleanUpData();
        temporaryFolder.delete();
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
