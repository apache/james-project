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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
import org.apache.james.util.OptionalUtils;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class ElasticSearchConfiguration {
    public static final String ELASTICSEARCH_HOSTS = "elasticsearch.hosts";
    public static final String ELASTICSEARCH_MASTER_HOST = "elasticsearch.masterHost";
    public static final String ELASTICSEARCH_PORT = "elasticsearch.port";
    public static final String ELASTICSEARCH_INDEX_NAME = "elasticsearch.index.name";
    public static final String ELASTICSEARCH_INDEX_MAILBOX_NAME = "elasticsearch.index.mailbox.name";
    public static final String ELASTICSEARCH_NB_REPLICA = "elasticsearch.nb.replica";
    public static final String ELASTICSEARCH_NB_SHARDS = "elasticsearch.nb.shards";
    public static final String ELASTICSEARCH_ALIAS_READ_NAME = "elasticsearch.alias.read.name";
    public static final String ELASTICSEARCH_ALIAS_WRITE_NAME = "elasticsearch.alias.write.name";
    public static final String ELASTICSEARCH_ALIAS_READ_MAILBOX_NAME = "elasticsearch.alias.read.mailbox.name";
    public static final String ELASTICSEARCH_ALIAS_WRITE_MAILBOX_NAME = "elasticsearch.alias.write.mailbox.name";
    public static final String ELASTICSEARCH_INDEX_QUOTA_RATIO_NAME = "elasticsearch.index.quota.ratio.name";
    public static final String ELASTICSEARCH_ALIAS_READ_QUOTA_RATIO_NAME = "elasticsearch.alias.read.quota.ratio.name";
    public static final String ELASTICSEARCH_ALIAS_WRITE_QUOTA_RATIO_NAME = "elasticsearch.alias.write.quota.ratio.name";
    public static final String ELASTICSEARCH_RETRY_CONNECTION_MIN_DELAY = "elasticsearch.retryConnection.minDelay";
    public static final String ELASTICSEARCH_RETRY_CONNECTION_MAX_RETRIES = "elasticsearch.retryConnection.maxRetries";
    public static final String ELASTICSEARCH_INDEX_ATTACHMENTS = "elasticsearch.indexAttachments";

    public static final int DEFAULT_CONNECTION_MAX_RETRIES = 7;
    public static final int DEFAULT_CONNECTION_MIN_DELAY = 3000;
    public static final boolean DEFAULT_INDEX_ATTACHMENTS = true;
    public static final int DEFAULT_NB_SHARDS = 1;
    public static final int DEFAULT_NB_REPLICA = 0;
    public static final int DEFAULT_PORT = 9300;
    private static final String LOCALHOST = "127.0.0.1";
    public static final Optional<Integer> DEFAULT_PORT_AS_OPTIONAL = Optional.of(DEFAULT_PORT);

    public static final ElasticSearchConfiguration DEFAULT_CONFIGURATION = new ElasticSearchConfiguration(
        ImmutableList.of(Host.from(LOCALHOST, DEFAULT_PORT)),
        MailboxElasticSearchConstants.DEFAULT_MAILBOX_INDEX,
        MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS,
        MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS,
        QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_INDEX,
        QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS,
        QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS,
        DEFAULT_NB_SHARDS,
        DEFAULT_NB_REPLICA,
        DEFAULT_CONNECTION_MIN_DELAY,
        DEFAULT_CONNECTION_MAX_RETRIES,
        IndexAttachments.YES);

    public static ElasticSearchConfiguration fromProperties(PropertiesConfiguration configuration) throws ConfigurationException {
        int nbShards = configuration.getInt(ELASTICSEARCH_NB_SHARDS, DEFAULT_NB_SHARDS);
        int nbReplica = configuration.getInt(ELASTICSEARCH_NB_REPLICA, DEFAULT_NB_REPLICA);
        int maxRetries = configuration.getInt(ELASTICSEARCH_RETRY_CONNECTION_MAX_RETRIES, DEFAULT_CONNECTION_MAX_RETRIES);
        int minDelay = configuration.getInt(ELASTICSEARCH_RETRY_CONNECTION_MIN_DELAY, DEFAULT_CONNECTION_MIN_DELAY);
        IndexAttachments indexAttachments = provideIndexAttachments(configuration);
        ImmutableList<Host> hosts = getHosts(configuration);

        ReadAliasName readAlias = computeMailboxReadAlias(configuration);
        WriteAliasName writeAlias = computeMailboxWriteAlias(configuration);
        IndexName indexName = computeMailboxIndexName(configuration);

        IndexName quotaRatioIndexName = computeQuotaSearchIndexName(configuration);
        ReadAliasName quotaSearchReadAlias = computeQuotaSearchReadAlias(configuration);
        WriteAliasName quotaSearchWriteAlias = computeQuotaSearchWriteAlias(configuration);

        return new ElasticSearchConfiguration(
            hosts,
            indexName,
            readAlias,
            writeAlias,
            quotaRatioIndexName,
            quotaSearchReadAlias,
            quotaSearchWriteAlias,
            nbShards,
            nbReplica,
            minDelay,
            maxRetries,
            indexAttachments);
    }

    public static IndexName computeMailboxIndexName(PropertiesConfiguration configuration) {
        return OptionalUtils.or(
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_INDEX_MAILBOX_NAME))
                .map(IndexName::new),
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_INDEX_NAME))
                .map(IndexName::new))
            .orElse(MailboxElasticSearchConstants.DEFAULT_MAILBOX_INDEX);
    }

    public static WriteAliasName computeMailboxWriteAlias(PropertiesConfiguration configuration) {
        return OptionalUtils.or(
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_WRITE_MAILBOX_NAME))
                .map(WriteAliasName::new),
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_WRITE_NAME))
                .map(WriteAliasName::new))
            .orElse(MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS);
    }

    public static ReadAliasName computeMailboxReadAlias(PropertiesConfiguration configuration) {
        return OptionalUtils.or(
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_READ_MAILBOX_NAME))
                .map(ReadAliasName::new),
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_READ_NAME))
                .map(ReadAliasName::new))
            .orElse(MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS);
    }

    public static IndexName computeQuotaSearchIndexName(PropertiesConfiguration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_INDEX_QUOTA_RATIO_NAME))
            .map(IndexName::new)
            .orElse(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_INDEX);
    }

    public static WriteAliasName computeQuotaSearchWriteAlias(PropertiesConfiguration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_WRITE_QUOTA_RATIO_NAME))
            .map(WriteAliasName::new)
            .orElse(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS);
    }

    public static ReadAliasName computeQuotaSearchReadAlias(PropertiesConfiguration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_READ_QUOTA_RATIO_NAME))
                .map(ReadAliasName::new)
            .orElse(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS);
    }

    private static IndexAttachments provideIndexAttachments(PropertiesConfiguration configuration) {
        if (configuration.getBoolean(ELASTICSEARCH_INDEX_ATTACHMENTS, DEFAULT_INDEX_ATTACHMENTS)) {
            return IndexAttachments.YES;
        }
        return IndexAttachments.NO;
    }

    private static ImmutableList<Host> getHosts(PropertiesConfiguration propertiesReader) throws ConfigurationException {
        Optional<String> masterHost = Optional.ofNullable(
            propertiesReader.getString(ELASTICSEARCH_MASTER_HOST, null));
        Optional<Integer> masterPort = Optional.ofNullable(
            propertiesReader.getInteger(ELASTICSEARCH_PORT, null));
        List<String> multiHosts = Arrays.asList(propertiesReader.getStringArray(ELASTICSEARCH_HOSTS));

        validateHostsConfigurationOptions(masterHost, masterPort, multiHosts);

        if (masterHost.isPresent()) {
            return ImmutableList.of(
                Host.from(masterHost.get(),
                masterPort.get()));
        } else {
            return multiHosts.stream()
                .map(ipAndPort -> Host.parse(ipAndPort, DEFAULT_PORT_AS_OPTIONAL))
                .collect(Guavate.toImmutableList());
        }
    }

    @VisibleForTesting
    static void validateHostsConfigurationOptions(Optional<String> masterHost,
                                                  Optional<Integer> masterPort,
                                                  List<String> multiHosts) throws ConfigurationException {
        if (masterHost.isPresent() != masterPort.isPresent()) {
            throw new ConfigurationException(ELASTICSEARCH_MASTER_HOST + " and " + ELASTICSEARCH_PORT + " should be specified together");
        }
        if (!multiHosts.isEmpty() && masterHost.isPresent()) {
            throw new ConfigurationException("You should choose between mono host set up and " + ELASTICSEARCH_HOSTS);
        }
        if (multiHosts.isEmpty() && !masterHost.isPresent()) {
            throw new ConfigurationException("You should specify either (" + ELASTICSEARCH_MASTER_HOST + " and " + ELASTICSEARCH_PORT + ") or " + ELASTICSEARCH_HOSTS);
        }
    }

    private final ImmutableList<Host> hosts;
    private final IndexName indexMailboxName;
    private final ReadAliasName readAliasMailboxName;
    private final WriteAliasName writeAliasMailboxName;
    private final IndexName indexQuotaRatioName;
    private final ReadAliasName readAliasQuotaRatioName;
    private final WriteAliasName writeAliasQuotaRatioName;
    private final int nbShards;
    private final int nbReplica;
    private final int minDelay;
    private final int maxRetries;
    private final IndexAttachments indexAttachment;

    private ElasticSearchConfiguration(ImmutableList<Host> hosts, IndexName indexMailboxName, ReadAliasName readAliasMailboxName,
                                      WriteAliasName writeAliasMailboxName, IndexName indexQuotaRatioName, ReadAliasName readAliasQuotaRatioName, WriteAliasName writeAliasQuotaRatioName, int nbShards, int nbReplica, int minDelay,
                                      int maxRetries, IndexAttachments indexAttachment) {
        this.hosts = hosts;
        this.indexMailboxName = indexMailboxName;
        this.readAliasMailboxName = readAliasMailboxName;
        this.writeAliasMailboxName = writeAliasMailboxName;
        this.indexQuotaRatioName = indexQuotaRatioName;
        this.readAliasQuotaRatioName = readAliasQuotaRatioName;
        this.writeAliasQuotaRatioName = writeAliasQuotaRatioName;
        this.nbShards = nbShards;
        this.nbReplica = nbReplica;
        this.minDelay = minDelay;
        this.maxRetries = maxRetries;
        this.indexAttachment = indexAttachment;
    }

    public ImmutableList<Host> getHosts() {
        return hosts;
    }

    public IndexName getIndexMailboxName() {
        return indexMailboxName;
    }

    public ReadAliasName getReadAliasMailboxName() {
        return readAliasMailboxName;
    }

    public WriteAliasName getWriteAliasMailboxName() {
        return writeAliasMailboxName;
    }

    public int getNbShards() {
        return nbShards;
    }

    public int getNbReplica() {
        return nbReplica;
    }

    public int getMinDelay() {
        return minDelay;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public IndexAttachments getIndexAttachment() {
        return indexAttachment;
    }

    public IndexName getIndexQuotaRatioName() {
        return indexQuotaRatioName;
    }

    public ReadAliasName getReadAliasQuotaRatioName() {
        return readAliasQuotaRatioName;
    }

    public WriteAliasName getWriteAliasQuotaRatioName() {
        return writeAliasQuotaRatioName;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ElasticSearchConfiguration) {
            ElasticSearchConfiguration that = (ElasticSearchConfiguration) o;

            return Objects.equals(this.nbShards, that.nbShards)
                && Objects.equals(this.nbReplica, that.nbReplica)
                && Objects.equals(this.minDelay, that.minDelay)
                && Objects.equals(this.maxRetries, that.maxRetries)
                && Objects.equals(this.indexAttachment, that.indexAttachment)
                && Objects.equals(this.hosts, that.hosts)
                && Objects.equals(this.indexMailboxName, that.indexMailboxName)
                && Objects.equals(this.readAliasMailboxName, that.readAliasMailboxName)
                && Objects.equals(this.writeAliasMailboxName, that.writeAliasMailboxName)
                && Objects.equals(this.indexQuotaRatioName, that.indexQuotaRatioName)
                && Objects.equals(this.readAliasQuotaRatioName, that.readAliasQuotaRatioName)
                && Objects.equals(this.writeAliasQuotaRatioName, that.writeAliasQuotaRatioName);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(hosts, indexMailboxName, readAliasMailboxName, writeAliasMailboxName, nbShards,
            nbReplica, minDelay, maxRetries, indexAttachment, indexQuotaRatioName, readAliasQuotaRatioName, writeAliasMailboxName);
    }
}
