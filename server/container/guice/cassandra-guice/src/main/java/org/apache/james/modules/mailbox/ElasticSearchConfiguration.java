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
import java.util.Collection;
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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class ElasticSearchConfiguration {

    public static class Builder {

        private final ImmutableList.Builder<Host> hosts;
        private Optional<IndexName> indexMailboxName;
        private Optional<ReadAliasName> readAliasMailboxName;
        private Optional<WriteAliasName> writeAliasMailboxName;
        private Optional<IndexName> indexQuotaRatioName;
        private Optional<ReadAliasName> readAliasQuotaRatioName;
        private Optional<WriteAliasName> writeAliasQuotaRatioName;
        private Optional<Integer> nbShards;
        private Optional<Integer> nbReplica;
        private Optional<Integer> minDelay;
        private Optional<Integer> maxRetries;
        private Optional<IndexAttachments> indexAttachment;

        public Builder() {
            hosts = ImmutableList.builder();
            indexMailboxName = Optional.empty();
            readAliasMailboxName = Optional.empty();
            writeAliasMailboxName = Optional.empty();
            indexQuotaRatioName = Optional.empty();
            readAliasQuotaRatioName = Optional.empty();
            writeAliasQuotaRatioName = Optional.empty();
            nbShards = Optional.empty();
            nbReplica = Optional.empty();
            minDelay = Optional.empty();
            maxRetries = Optional.empty();
            indexAttachment = Optional.empty();
        }

        public Builder addHost(Host host) {
            this.hosts.add(host);
            return this;
        }

        public Builder addHosts(Collection<Host> hosts) {
            this.hosts.addAll(hosts);
            return this;
        }

        public Builder indexMailboxName(IndexName indexMailboxName) {
            return indexMailboxName(Optional.of(indexMailboxName));
        }

        public Builder indexMailboxName(Optional<IndexName> indexMailboxName) {
            this.indexMailboxName = indexMailboxName;
            return this;
        }

        public Builder readAliasMailboxName(ReadAliasName readAliasMailboxName) {
            return readAliasMailboxName(Optional.of(readAliasMailboxName));
        }

        public Builder readAliasMailboxName(Optional<ReadAliasName> readAliasMailboxName) {
            this.readAliasMailboxName = readAliasMailboxName;
            return this;
        }

        public Builder writeAliasMailboxName(WriteAliasName writeAliasMailboxName) {
            return writeAliasMailboxName(Optional.of(writeAliasMailboxName));
        }

        public Builder writeAliasMailboxName(Optional<WriteAliasName> writeAliasMailboxName) {
            this.writeAliasMailboxName = writeAliasMailboxName;
            return this;
        }

        public Builder indexQuotaRatioName(IndexName indexQuotaRatioName) {
            return indexQuotaRatioName(Optional.of(indexQuotaRatioName));
        }

        public Builder indexQuotaRatioName(Optional<IndexName> indexQuotaRatioName) {
            this.indexQuotaRatioName = indexQuotaRatioName;
            return this;
        }

        public Builder readAliasQuotaRatioName(ReadAliasName readAliasQuotaRatioName) {
            return readAliasQuotaRatioName(Optional.of(readAliasQuotaRatioName));
        }

        public Builder readAliasQuotaRatioName(Optional<ReadAliasName> readAliasQuotaRatioName) {
            this.readAliasQuotaRatioName = readAliasQuotaRatioName;
            return this;
        }

        public Builder writeAliasQuotaRatioName(WriteAliasName writeAliasQuotaRatioName) {
            return writeAliasQuotaRatioName(Optional.of(writeAliasQuotaRatioName));
        }

        public Builder writeAliasQuotaRatioName(Optional<WriteAliasName> writeAliasQuotaRatioName) {
            this.writeAliasQuotaRatioName = writeAliasQuotaRatioName;
            return this;
        }

        public Builder indexAttachment(IndexAttachments indexAttachment) {
            this.indexAttachment = Optional.of(indexAttachment);
            return this;
        }

        public Builder nbShards(Optional<Integer> nbShards) {
            this.nbShards = nbShards;
            return this;
        }

        public Builder nbReplica(Optional<Integer> nbReplica) {
            this.nbReplica = nbReplica;
            return this;
        }

        public Builder minDelay(Optional<Integer> minDelay) {
            this.minDelay = minDelay;
            return this;
        }

        public Builder maxRetries(Optional<Integer> maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ElasticSearchConfiguration build() {
            ImmutableList<Host> hosts = this.hosts.build();
            Preconditions.checkState(!hosts.isEmpty(), "You need to specify ElasticSearch host");
            return new ElasticSearchConfiguration(
                hosts,
                indexMailboxName.orElse(MailboxElasticSearchConstants.DEFAULT_MAILBOX_INDEX),
                readAliasMailboxName.orElse(MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS),
                writeAliasMailboxName.orElse(MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS),
                indexQuotaRatioName.orElse(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_INDEX),
                readAliasQuotaRatioName.orElse(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS),
                writeAliasQuotaRatioName.orElse(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS),
                nbShards.orElse(DEFAULT_NB_SHARDS),
                nbReplica.orElse(DEFAULT_NB_REPLICA),
                minDelay.orElse(DEFAULT_CONNECTION_MIN_DELAY),
                maxRetries.orElse(DEFAULT_CONNECTION_MAX_RETRIES),
                indexAttachment.orElse(IndexAttachments.YES));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

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

    public static final ElasticSearchConfiguration DEFAULT_CONFIGURATION = builder()
        .addHost(Host.from(LOCALHOST, DEFAULT_PORT))
        .build();

    public static ElasticSearchConfiguration fromProperties(PropertiesConfiguration configuration) throws ConfigurationException {
        return builder()
            .addHosts(getHosts(configuration))
            .indexMailboxName(computeMailboxIndexName(configuration))
            .readAliasMailboxName(computeMailboxReadAlias(configuration))
            .writeAliasMailboxName(computeMailboxWriteAlias(configuration))
            .indexQuotaRatioName(computeQuotaSearchIndexName(configuration))
            .readAliasQuotaRatioName(computeQuotaSearchReadAlias(configuration))
            .writeAliasQuotaRatioName(computeQuotaSearchWriteAlias(configuration))
            .nbShards(Optional.ofNullable(configuration.getInteger(ELASTICSEARCH_NB_SHARDS, null)))
            .nbReplica(Optional.ofNullable(configuration.getInteger(ELASTICSEARCH_NB_REPLICA, null)))
            .minDelay(Optional.ofNullable(configuration.getInteger(ELASTICSEARCH_RETRY_CONNECTION_MIN_DELAY, null)))
            .maxRetries(Optional.ofNullable(configuration.getInteger(ELASTICSEARCH_RETRY_CONNECTION_MAX_RETRIES, null)))
            .indexAttachment(provideIndexAttachments(configuration))
            .build();
    }

    public static Optional<IndexName> computeMailboxIndexName(PropertiesConfiguration configuration) {
        return OptionalUtils.or(
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_INDEX_MAILBOX_NAME))
                .map(IndexName::new),
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_INDEX_NAME))
                .map(IndexName::new));
    }

    public static Optional<WriteAliasName> computeMailboxWriteAlias(PropertiesConfiguration configuration) {
        return OptionalUtils.or(
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_WRITE_MAILBOX_NAME))
                .map(WriteAliasName::new),
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_WRITE_NAME))
                .map(WriteAliasName::new));
    }

    public static Optional<ReadAliasName> computeMailboxReadAlias(PropertiesConfiguration configuration) {
        return OptionalUtils.or(
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_READ_MAILBOX_NAME))
                .map(ReadAliasName::new),
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_READ_NAME))
                .map(ReadAliasName::new));
    }

    public static Optional<IndexName> computeQuotaSearchIndexName(PropertiesConfiguration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_INDEX_QUOTA_RATIO_NAME))
            .map(IndexName::new);
    }

    public static Optional<WriteAliasName> computeQuotaSearchWriteAlias(PropertiesConfiguration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_WRITE_QUOTA_RATIO_NAME))
            .map(WriteAliasName::new);
    }

    public static Optional<ReadAliasName> computeQuotaSearchReadAlias(PropertiesConfiguration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_READ_QUOTA_RATIO_NAME))
                .map(ReadAliasName::new);
    }

    private static IndexAttachments provideIndexAttachments(PropertiesConfiguration configuration) {
        if (configuration.getBoolean(ELASTICSEARCH_INDEX_ATTACHMENTS, DEFAULT_INDEX_ATTACHMENTS)) {
            return IndexAttachments.YES;
        }
        return IndexAttachments.NO;
    }

    private static ImmutableList<Host> getHosts(PropertiesConfiguration propertiesReader) throws ConfigurationException {
        propertiesReader.setListDelimiter(',');
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
