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

package org.apache.james.quota.search.elasticsearch.v7;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.backends.es.v7.IndexName;
import org.apache.james.backends.es.v7.ReadAliasName;
import org.apache.james.backends.es.v7.WriteAliasName;

public class ElasticSearchQuotaConfiguration {

    public static class Builder {

        private Optional<IndexName> indexQuotaRatioName;
        private Optional<ReadAliasName> readAliasQuotaRatioName;
        private Optional<WriteAliasName> writeAliasQuotaRatioName;

        public Builder() {
            indexQuotaRatioName = Optional.empty();
            readAliasQuotaRatioName = Optional.empty();
            writeAliasQuotaRatioName = Optional.empty();
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


        public ElasticSearchQuotaConfiguration build() {
            return new ElasticSearchQuotaConfiguration(
                indexQuotaRatioName.orElse(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_INDEX),
                readAliasQuotaRatioName.orElse(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_READ_ALIAS),
                writeAliasQuotaRatioName.orElse(QuotaRatioElasticSearchConstants.DEFAULT_QUOTA_RATIO_WRITE_ALIAS));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final String ELASTICSEARCH_INDEX_QUOTA_RATIO_NAME = "elasticsearch.index.quota.ratio.name";
    public static final String ELASTICSEARCH_ALIAS_READ_QUOTA_RATIO_NAME = "elasticsearch.alias.read.quota.ratio.name";
    public static final String ELASTICSEARCH_ALIAS_WRITE_QUOTA_RATIO_NAME = "elasticsearch.alias.write.quota.ratio.name";

    public static final ElasticSearchQuotaConfiguration DEFAULT_CONFIGURATION = builder().build();

    public static ElasticSearchQuotaConfiguration fromProperties(Configuration configuration) {
        return builder()
            .indexQuotaRatioName(computeQuotaSearchIndexName(configuration))
            .readAliasQuotaRatioName(computeQuotaSearchReadAlias(configuration))
            .writeAliasQuotaRatioName(computeQuotaSearchWriteAlias(configuration))
            .build();
    }

    public static Optional<IndexName> computeQuotaSearchIndexName(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_INDEX_QUOTA_RATIO_NAME))
            .map(IndexName::new);
    }

    public static Optional<WriteAliasName> computeQuotaSearchWriteAlias(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_WRITE_QUOTA_RATIO_NAME))
            .map(WriteAliasName::new);
    }

    public static Optional<ReadAliasName> computeQuotaSearchReadAlias(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_READ_QUOTA_RATIO_NAME))
                .map(ReadAliasName::new);
    }

    private final IndexName indexQuotaRatioName;
    private final ReadAliasName readAliasQuotaRatioName;
    private final WriteAliasName writeAliasQuotaRatioName;

    private ElasticSearchQuotaConfiguration(IndexName indexQuotaRatioName, ReadAliasName readAliasQuotaRatioName, WriteAliasName writeAliasQuotaRatioName) {
        this.indexQuotaRatioName = indexQuotaRatioName;
        this.readAliasQuotaRatioName = readAliasQuotaRatioName;
        this.writeAliasQuotaRatioName = writeAliasQuotaRatioName;
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
        if (o instanceof ElasticSearchQuotaConfiguration) {
            ElasticSearchQuotaConfiguration that = (ElasticSearchQuotaConfiguration) o;

            return Objects.equals(this.indexQuotaRatioName, that.indexQuotaRatioName)
                && Objects.equals(this.readAliasQuotaRatioName, that.readAliasQuotaRatioName)
                && Objects.equals(this.writeAliasQuotaRatioName, that.writeAliasQuotaRatioName);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(indexQuotaRatioName, readAliasQuotaRatioName);
    }
}
