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

package org.apache.james.backends.opensearch;

import java.util.Optional;

import org.apache.james.backends.opensearch.OpenSearchConfiguration.SSLConfiguration;
import org.apache.james.backends.opensearch.OpenSearchConfiguration.SSLConfiguration.SSLTrustStore;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ClientProviderImplConnectionAuthESOverrideTrustStoreTest implements ClientProviderImplConnectionContract {

    private static final String TRUST_STORE_PASSWORD = "mypass";
    private static final String TRUST_STORE_FILE_PATH = "src/test/resources/auth-es/server.jks";

    @RegisterExtension
    static OpenSearchClusterExtension extension = new OpenSearchClusterExtension(new OpenSearchClusterExtension.OpenSearchCluster(
        DockerAuthElasticSearchSingleton.INSTANCE,
        new DockerOpenSearch.WithAuth()));

    @Override
    public OpenSearchConfiguration.Builder configurationBuilder() {
        return OpenSearchConfiguration.builder()
            .credential(Optional.of(DockerOpenSearch.WithAuth.DEFAULT_CREDENTIAL))
            .hostScheme(Optional.of(OpenSearchConfiguration.HostScheme.HTTPS))
            .sslTrustConfiguration(SSLConfiguration.builder()
                .strategyOverride(SSLTrustStore.of(TRUST_STORE_FILE_PATH, TRUST_STORE_PASSWORD))
                .acceptAnyHostNameVerifier()
                .build());
    }
}