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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.es.IndexName;
import org.apache.james.backends.es.ReadAliasName;
import org.apache.james.backends.es.WriteAliasName;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.MailboxElasticSearchConstants;
import org.apache.james.quota.search.elasticsearch.QuotaRatioElasticSearchConstants;
import org.apache.james.util.Host;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class ElasticSearchConfigurationTest {

    @Test
    public void getNbReplicaShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.nb.replica", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getNbReplica())
            .isEqualTo(value);
    }

    @Test
    public void getNbReplicaShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getNbReplica())
            .isEqualTo(ElasticSearchConfiguration.DEFAULT_NB_REPLICA);
    }

    @Test
    public void getNbShardsShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.nb.shards", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getNbShards())
            .isEqualTo(value);
    }

    @Test
    public void getNbShardsShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getNbShards())
            .isEqualTo(ElasticSearchConfiguration.DEFAULT_NB_SHARDS);
    }

    @Test
    public void getMaxRetriesShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.retryConnection.maxRetries", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getMaxRetries())
            .isEqualTo(value);
    }

    @Test
    public void getMaxRetriesShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getMaxRetries())
            .isEqualTo(ElasticSearchConfiguration.DEFAULT_CONNECTION_MAX_RETRIES);
    }

    @Test
    public void getMinDelayShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.retryConnection.minDelay", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getMinDelay())
            .isEqualTo(value);
    }

    @Test
    public void getMinDelayShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getMinDelay())
            .isEqualTo(ElasticSearchConfiguration.DEFAULT_CONNECTION_MIN_DELAY);
    }

    @Test
    public void getIndexMailboxNameShouldReturnOldConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.index.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexMailboxName())
            .isEqualTo(new IndexName(name));
    }

    @Test
    public void getIndexMailboxNameShouldReturnNewConfiguredValueWhenBoth() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.index.name", "other");
        configuration.addProperty("elasticsearch.index.mailbox.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexMailboxName())
            .isEqualTo(new IndexName(name));
    }

    @Test
    public void getIndexMailboxNameShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.index.mailbox.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexMailboxName())
            .isEqualTo(new IndexName(name));
    }

    @Test
    public void getIndexMailboxNameShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexMailboxName())
            .isEqualTo(MailboxElasticSearchConstants.DEFAULT_MAILBOX_INDEX);
    }

    @Test
    public void getReadAliasMailboxNameShouldReturnOldConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.read.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getReadAliasMailboxName())
            .isEqualTo(new ReadAliasName(name));
    }

    @Test
    public void getReadAliasMailboxNameShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.read.mailbox.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getReadAliasMailboxName())
            .isEqualTo(new ReadAliasName(name));
    }

    @Test
    public void getReadAliasMailboxNameShouldReturnNewConfiguredValueWhenBoth() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.read.mailbox.name", name);
        configuration.addProperty("elasticsearch.alias.read.name", "other");
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getReadAliasMailboxName())
            .isEqualTo(new ReadAliasName(name));
    }

    @Test
    public void getReadAliasMailboxNameShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getReadAliasMailboxName())
            .isEqualTo(MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS);
    }

    @Test
    public void getWriteAliasMailboxNameShouldReturnOldConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.write.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWriteAliasMailboxName())
            .isEqualTo(new WriteAliasName(name));
    }

    @Test
    public void getWriteAliasMailboxNameShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.write.mailbox.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWriteAliasMailboxName())
            .isEqualTo(new WriteAliasName(name));
    }

    @Test
    public void getWriteAliasMailboxNameShouldReturnNewConfiguredValueWhenBoth() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.write.mailbox.name", name);
        configuration.addProperty("elasticsearch.alias.write.name", "other");
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWriteAliasMailboxName())
            .isEqualTo(new WriteAliasName(name));
    }

    @Test
    public void getWriteAliasMailboxNameShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWriteAliasMailboxName())
            .isEqualTo(MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS);
    }

    @Test
    public void getReadAliasQuotaRatioNameShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.read.quota.ratio.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getReadAliasQuotaRatioName())
            .isEqualTo(new ReadAliasName(name));
    }

    @Test
    public void getReadAliasQuotaRatioNameShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getReadAliasQuotaRatioName())
            .isEqualTo(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS);
    }

    @Test
    public void getWriteAliasQuotaRatioNameShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.alias.write.quota.ratio.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWriteAliasQuotaRatioName())
            .isEqualTo(new WriteAliasName(name));
    }

    @Test
    public void getWriteAliasQuotaRatioNameShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getWriteAliasQuotaRatioName())
            .isEqualTo(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS);
    }

    @Test
    public void getIndexQuotaRatioNameShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String name = "name";
        configuration.addProperty("elasticsearch.index.quota.ratio.name", name);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexQuotaRatioName())
            .isEqualTo(new IndexName(name));
    }

    @Test
    public void getIndexQuotaRatioNameShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexQuotaRatioName())
            .isEqualTo(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_INDEX);
    }

    @Test
    public void getIndexAttachmentShouldReturnConfiguredValueWhenTrue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.indexAttachments", true);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexAttachment())
            .isEqualTo(IndexAttachments.YES);
    }

    @Test
    public void getIndexAttachmentShouldReturnConfiguredValueWhenFalse() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.indexAttachments", false);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexAttachment())
            .isEqualTo(IndexAttachments.NO);
    }

    @Test
    public void getIndexAttachmentShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getIndexAttachment())
            .isEqualTo(IndexAttachments.YES);
    }


    @Test
    public void getHostsShouldReturnConfiguredHostsWhenNoPort() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String hostname = "myHost";
        configuration.addProperty("elasticsearch.hosts", hostname);

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getHosts())
            .containsOnly(Host.from(hostname, ElasticSearchConfiguration.DEFAULT_PORT));
    }

    @Test
    public void getHostsShouldReturnConfiguredHostsWhenListIsUsed() throws ConfigurationException {
        String hostname = "myHost";
        String hostname2 = "myOtherHost";
        int port = 2154;
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", hostname + "," + hostname2 + ":" + port);

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getHosts())
            .containsOnly(Host.from(hostname, ElasticSearchConfiguration.DEFAULT_PORT),
                Host.from(hostname2, port));
    }

    @Test
    public void getHostsShouldReturnConfiguredHosts() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String hostname = "myHost";
        int port = 2154;
        configuration.addProperty("elasticsearch.hosts", hostname + ":" + port);

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getHosts())
            .containsOnly(Host.from(hostname, port));
    }

    @Test
    public void getHostsShouldReturnConfiguredMasterHost() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String hostname = "myHost";
        configuration.addProperty("elasticsearch.masterHost", hostname);
        int port = 9300;
        configuration.addProperty("elasticsearch.port", port);

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.getHosts())
            .containsOnly(Host.from(hostname, port));
    }

    @Test
    public void validateHostsConfigurationOptionsShouldThrowWhenNoHostSpecify() {
        assertThatThrownBy(() ->
            ElasticSearchConfiguration.validateHostsConfigurationOptions(
                Optional.empty(),
                Optional.empty(),
                ImmutableList.of()))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("You should specify either (" + ElasticSearchConfiguration.ELASTICSEARCH_MASTER_HOST +
                " and " + ElasticSearchConfiguration.ELASTICSEARCH_PORT +
                ") or " + ElasticSearchConfiguration.ELASTICSEARCH_HOSTS);
    }

    @Test
    public void validateHostsConfigurationOptionsShouldThrowWhenMonoAndMultiHostSpecified() {
        assertThatThrownBy(() ->
            ElasticSearchConfiguration.validateHostsConfigurationOptions(
                Optional.of("localhost"),
                Optional.of(9200),
                ImmutableList.of("localhost:9200")))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("You should choose between mono host set up and " + ElasticSearchConfiguration.ELASTICSEARCH_HOSTS);
    }

    @Test
    public void validateHostsConfigurationOptionsShouldThrowWhenMonoHostWithoutPort() {
        assertThatThrownBy(() ->
            ElasticSearchConfiguration.validateHostsConfigurationOptions(
                Optional.of("localhost"),
                Optional.empty(),
                ImmutableList.of()))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage(ElasticSearchConfiguration.ELASTICSEARCH_MASTER_HOST +
                " and " + ElasticSearchConfiguration.ELASTICSEARCH_PORT + " should be specified together");
    }

    @Test
    public void validateHostsConfigurationOptionsShouldThrowWhenMonoHostWithoutAddress() {
        assertThatThrownBy(() ->
        ElasticSearchConfiguration.validateHostsConfigurationOptions(
            Optional.empty(),
            Optional.of(9200),
            ImmutableList.of()))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage(ElasticSearchConfiguration.ELASTICSEARCH_MASTER_HOST + " and " +
            ElasticSearchConfiguration.ELASTICSEARCH_PORT + " should be specified together");
    }

    @Test
    public void validateHostsConfigurationOptionsShouldAcceptMonoHostConfiguration() throws Exception {
        ElasticSearchConfiguration.validateHostsConfigurationOptions(
            Optional.of("localhost"),
            Optional.of(9200),
            ImmutableList.of());
    }

    @Test
    public void validateHostsConfigurationOptionsShouldAcceptMultiHostConfiguration() throws Exception {
        ElasticSearchConfiguration.validateHostsConfigurationOptions(
            Optional.empty(),
            Optional.empty(),
            ImmutableList.of("localhost:9200"));
    }


}