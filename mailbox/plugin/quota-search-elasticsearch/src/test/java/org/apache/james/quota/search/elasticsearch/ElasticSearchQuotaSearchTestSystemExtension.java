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

package org.apache.james.quota.search.elasticsearch;

import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.apache.james.backends.es.DockerElasticSearch;
import org.apache.james.backends.es.DockerElasticSearchSingleton;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.ReactorElasticSearchClient;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.quota.search.QuotaSearchTestSystem;
import org.apache.james.quota.search.elasticsearch.events.ElasticSearchQuotaMailboxListener;
import org.apache.james.quota.search.elasticsearch.json.QuotaRatioToElasticSearchJson;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class ElasticSearchQuotaSearchTestSystemExtension implements ParameterResolver, BeforeEachCallback, AfterEachCallback {

    private final DockerElasticSearch elasticSearch = DockerElasticSearchSingleton.INSTANCE;
    private ReactorElasticSearchClient client;

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == QuotaSearchTestSystem.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        try {
            client = QuotaSearchIndexCreationUtil.prepareDefaultClient(
                elasticSearch.clientProvider().get(),
                elasticSearch.configuration());

            InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();

            DNSService dnsService = mock(DNSService.class);
            MemoryDomainList domainList = new MemoryDomainList(dnsService);
            domainList.configure(DomainListConfiguration.DEFAULT);
            MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

            ElasticSearchQuotaMailboxListener listener = new ElasticSearchQuotaMailboxListener(
                new ElasticSearchIndexer(client,
                    QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS),
                new QuotaRatioToElasticSearchJson(),
                new UserRoutingKeyFactory());

            resources.getMailboxManager().getEventBus().register(listener);

            QuotaComponents quotaComponents = resources.getMailboxManager().getQuotaComponents();

            return new QuotaSearchTestSystem(
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
        } catch (Exception e) {
            throw new ParameterResolutionException("Error while resolving parameter", e);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        elasticSearch.start();
    }

    @Override
    public void afterEach(ExtensionContext context) throws IOException {
        client.close();
        elasticSearch.cleanUpData();
    }
}
