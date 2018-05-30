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

import java.util.concurrent.Executors;

import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.backends.es.utils.TestingClientProvider;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.quota.search.QuotaSearchTestSystem;
import org.apache.james.quota.search.elasticsearch.ElasticSearchQuotaSearcher;
import org.apache.james.quota.search.elasticsearch.QuotaRatioElasticSearchConstants;
import org.apache.james.quota.search.elasticsearch.QuotaSearchIndexCreationUtil;
import org.apache.james.quota.search.elasticsearch.events.ElasticSearchQuotaMailboxListener;
import org.apache.james.quota.search.elasticsearch.json.QuotaRatioToElasticSearchJson;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.jackson.QuotaModule;
import org.apache.james.webadmin.service.UserQuotaService;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.elasticsearch.client.Client;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class ElasticSearchQuotaSearchExtension implements ParameterResolver, BeforeEachCallback, AfterEachCallback {

    private WebAdminQuotaSearchTestSystem restQuotaSearchTestSystem;
    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder, QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_INDEX);

    @Override
    public void beforeEach(ExtensionContext context) {
        try {
            temporaryFolder.create();
            embeddedElasticSearch.before();

            Client client = QuotaSearchIndexCreationUtil.prepareDefaultClient(
                new TestingClientProvider(embeddedElasticSearch.getNode()).get());

            InMemoryIntegrationResources.Resources resources = new InMemoryIntegrationResources().createResources(new SimpleGroupMembershipResolver());

            MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting();

            DNSService dnsService = mock(DNSService.class);
            MemoryDomainList domainList = new MemoryDomainList(dnsService);
            usersRepository.setDomainList(domainList);

            ElasticSearchQuotaMailboxListener listener = new ElasticSearchQuotaMailboxListener(
                new ElasticSearchIndexer(client, Executors.newSingleThreadExecutor(),
                    QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS,
                    QuotaRatioElasticSearchConstants.QUOTA_RATIO_TYPE),
                new QuotaRatioToElasticSearchJson());

            resources.getMailboxManager()
                .addGlobalListener(listener, new MockMailboxSession("ANY"));

            QuotaSearchTestSystem quotaSearchTestSystem = new QuotaSearchTestSystem(
                resources.getMaxQuotaManager(),
                resources.getMailboxManager(),
                resources.getQuotaManager(),
                resources.getQuotaRootResolver(),
                new ElasticSearchQuotaSearcher(client,
                    QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS),
                usersRepository,
                domainList,
                resources.getCurrentQuotaManager(),
                () -> embeddedElasticSearch.awaitForElasticSearch());

            UserQuotaRoutes routes = new UserQuotaRoutes(
                quotaSearchTestSystem.getUsersRepository(),
                new UserQuotaService(
                    quotaSearchTestSystem.getMaxQuotaManager(),
                    quotaSearchTestSystem.getQuotaManager(),
                    quotaSearchTestSystem.getQuotaRootResolver(),
                    quotaSearchTestSystem.getQuotaSearcher()),
                new JsonTransformer(new QuotaModule()),
                ImmutableSet.of(new QuotaModule()));

            restQuotaSearchTestSystem = new WebAdminQuotaSearchTestSystem(quotaSearchTestSystem, ImmutableList.of(routes));
        } catch (Exception e) {
            throw new ParameterResolutionException("Error while resolving parameter", e);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        restQuotaSearchTestSystem.getWebAdminServer().destroy();

        embeddedElasticSearch.after();
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
