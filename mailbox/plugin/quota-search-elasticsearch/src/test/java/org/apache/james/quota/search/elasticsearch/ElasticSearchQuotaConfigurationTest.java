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

package org.apache.james.quota.search.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.es.IndexName;
import org.apache.james.backends.es.ReadAliasName;
import org.apache.james.backends.es.WriteAliasName;
import org.junit.Test;

public class ElasticSearchQuotaConfigurationTest {

    @Test
    public void getReadAliasQuotaRatioNameShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.read.quota.ratio.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchQuotaConfiguration elasticSearchConfiguration = ElasticSearchQuotaConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getReadAliasQuotaRatioName())
            .isEqualTo(new ReadAliasName(name));
    }

    @Test
    public void getReadAliasQuotaRatioNameShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchQuotaConfiguration elasticSearchConfiguration = ElasticSearchQuotaConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getReadAliasQuotaRatioName())
            .isEqualTo(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS);
    }

    @Test
    public void getWriteAliasQuotaRatioNameShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.write.quota.ratio.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchQuotaConfiguration elasticSearchConfiguration = ElasticSearchQuotaConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWriteAliasQuotaRatioName())
            .isEqualTo(new WriteAliasName(name));
    }

    @Test
    public void getWriteAliasQuotaRatioNameShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchQuotaConfiguration elasticSearchConfiguration = ElasticSearchQuotaConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWriteAliasQuotaRatioName())
            .isEqualTo(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS);
    }

    @Test
    public void getIndexQuotaRatioNameShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.index.quota.ratio.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchQuotaConfiguration elasticSearchConfiguration = ElasticSearchQuotaConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexQuotaRatioName())
            .isEqualTo(new IndexName(name));
    }

    @Test
    public void getIndexQuotaRatioNameShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchQuotaConfiguration elasticSearchConfiguration = ElasticSearchQuotaConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexQuotaRatioName())
            .isEqualTo(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_INDEX);
    }
}