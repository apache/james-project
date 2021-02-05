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

package org.apache.james.backends.es.v7;

import static org.apache.james.backends.es.v7.ElasticSearchClusterExtension.ElasticSearchCluster;

import java.util.Optional;

import org.apache.james.backends.es.v7.ElasticSearchConfiguration.HostScheme;
import org.apache.james.backends.es.v7.ElasticSearchConfiguration.SSLConfiguration;
import org.junit.jupiter.api.extension.RegisterExtension;

class ClientProviderImplConnectionAuthESIgnoreSSLCheckTest implements ClientProviderImplConnectionContract {

    @RegisterExtension
    static ElasticSearchClusterExtension extension = new ElasticSearchClusterExtension(new ElasticSearchCluster(
        DockerAuthElasticSearchSingleton.INSTANCE,
        new DockerElasticSearch.WithAuth()));

    @Override
    public ElasticSearchConfiguration.Builder configurationBuilder() {
        return ElasticSearchConfiguration.builder()
            .credential(Optional.of(DockerElasticSearch.WithAuth.DEFAULT_CREDENTIAL))
            .hostScheme(Optional.of(HostScheme.HTTPS))
            .sslTrustConfiguration(SSLConfiguration.builder()
                .strategyIgnore()
                .acceptAnyHostNameVerifier()
                .build());
    }
}
