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

package org.apache.james.modules.mailbox;

import java.util.Set;

import org.apache.james.backends.opensearch.ClientProvider;
import org.apache.james.backends.opensearch.ElasticSearchHealthCheck;
import org.apache.james.backends.opensearch.IndexName;
import org.apache.james.backends.opensearch.ReactorElasticSearchClient;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.mailbox.opensearch.ElasticSearchMailboxConfiguration;
import org.apache.james.quota.search.opensearch.OpenSearchQuotaConfiguration;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class ElasticSearchClientModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ClientProvider.class).in(Scopes.SINGLETON);
        bind(ReactorElasticSearchClient.class).toProvider(ClientProvider.class);

        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding()
            .to(ElasticSearchHealthCheck.class);
    }

    @Provides
    @Singleton
    Set<IndexName> provideIndexNames(ElasticSearchMailboxConfiguration mailboxConfiguration,
                                     OpenSearchQuotaConfiguration quotaConfiguration) {
        return ImmutableSet.of(
            mailboxConfiguration.getIndexMailboxName(),
            quotaConfiguration.getIndexQuotaRatioName());
    }
}
