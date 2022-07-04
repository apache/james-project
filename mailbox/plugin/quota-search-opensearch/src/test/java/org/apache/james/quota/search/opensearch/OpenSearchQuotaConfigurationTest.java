/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.quota.search.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.backends.opensearch.IndexName;
import org.apache.james.backends.opensearch.ReadAliasName;
import org.apache.james.backends.opensearch.WriteAliasName;
import org.junit.jupiter.api.Test;

class OpenSearchQuotaConfigurationTest {

    @Test
    void getReadAliasQuotaRatioNameShouldReturnConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.read.quota.ratio.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        OpenSearchQuotaConfiguration openSearchQuotaConfiguration = OpenSearchQuotaConfiguration.fromProperties(configuration);

        assertThat(openSearchQuotaConfiguration.getReadAliasQuotaRatioName())
            .isEqualTo(new ReadAliasName(name));
    }

    @Test
    void getReadAliasQuotaRatioNameShouldReturnDefaultValueWhenMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        OpenSearchQuotaConfiguration openSearchConfiguration = OpenSearchQuotaConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getReadAliasQuotaRatioName())
            .isEqualTo(QuotaRatioOpenSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS);
    }

    @Test
    void getWriteAliasQuotaRatioNameShouldReturnConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.write.quota.ratio.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        OpenSearchQuotaConfiguration openSearchConfiguration = OpenSearchQuotaConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getWriteAliasQuotaRatioName())
            .isEqualTo(new WriteAliasName(name));
    }

    @Test
    void getWriteAliasQuotaRatioNameShouldReturnDefaultValueWhenMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        OpenSearchQuotaConfiguration openSearchConfiguration = OpenSearchQuotaConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getWriteAliasQuotaRatioName())
            .isEqualTo(QuotaRatioOpenSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS);
    }

    @Test
    void getIndexQuotaRatioNameShouldReturnConfiguredValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.index.quota.ratio.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        OpenSearchQuotaConfiguration openSearchConfiguration = OpenSearchQuotaConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getIndexQuotaRatioName())
            .isEqualTo(new IndexName(name));
    }

    @Test
    void getIndexQuotaRatioNameShouldReturnDefaultValueWhenMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        OpenSearchQuotaConfiguration openSearchConfiguration = OpenSearchQuotaConfiguration.fromProperties(configuration);

        assertThat(openSearchConfiguration.getIndexQuotaRatioName())
            .isEqualTo(QuotaRatioOpenSearchConstants.DEFAULT_QUOTA_RATIO_INDEX);
    }
}