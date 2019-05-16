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

package org.apache.james.mailbox.elasticsearch.v6;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration.Configuration;
import org.apache.james.backends.es.IndexName;
import org.apache.james.backends.es.ReadAliasName;
import org.apache.james.backends.es.WriteAliasName;
import org.apache.james.util.OptionalUtils;

public class ElasticSearchMailboxConfiguration {

    public static class Builder {
        private Optional<IndexName> indexMailboxName;
        private Optional<ReadAliasName> readAliasMailboxName;
        private Optional<WriteAliasName> writeAliasMailboxName;
        private Optional<IndexAttachments> indexAttachment;

        public Builder() {
            indexMailboxName = Optional.empty();
            readAliasMailboxName = Optional.empty();
            writeAliasMailboxName = Optional.empty();
            indexAttachment = Optional.empty();
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


        public Builder indexAttachment(IndexAttachments indexAttachment) {
            this.indexAttachment = Optional.of(indexAttachment);
            return this;
        }



        public ElasticSearchMailboxConfiguration build() {
            return new ElasticSearchMailboxConfiguration(
                indexMailboxName.orElse(MailboxElasticSearchConstants.DEFAULT_MAILBOX_INDEX),
                readAliasMailboxName.orElse(MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS),
                writeAliasMailboxName.orElse(MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS),
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
    public static final int DEFAULT_NB_SHARDS = 5;
    public static final int DEFAULT_NB_REPLICA = 1;
    public static final int DEFAULT_PORT = 9300;
    public static final Optional<Integer> DEFAULT_PORT_AS_OPTIONAL = Optional.of(DEFAULT_PORT);

    public static final ElasticSearchMailboxConfiguration DEFAULT_CONFIGURATION = builder().build();

    public static ElasticSearchMailboxConfiguration fromProperties(Configuration configuration) {
        return builder()
            .indexMailboxName(computeMailboxIndexName(configuration))
            .readAliasMailboxName(computeMailboxReadAlias(configuration))
            .writeAliasMailboxName(computeMailboxWriteAlias(configuration))
            .indexAttachment(provideIndexAttachments(configuration))
            .build();
    }

    public static Optional<IndexName> computeMailboxIndexName(Configuration configuration) {
        return OptionalUtils.or(
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_INDEX_MAILBOX_NAME))
                .map(IndexName::new),
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_INDEX_NAME))
                .map(IndexName::new));
    }

    public static Optional<WriteAliasName> computeMailboxWriteAlias(Configuration configuration) {
        return OptionalUtils.or(
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_WRITE_MAILBOX_NAME))
                .map(WriteAliasName::new),
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_WRITE_NAME))
                .map(WriteAliasName::new));
    }

    public static Optional<ReadAliasName> computeMailboxReadAlias(Configuration configuration) {
        return OptionalUtils.or(
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_READ_MAILBOX_NAME))
                .map(ReadAliasName::new),
            Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_READ_NAME))
                .map(ReadAliasName::new));
    }


    private static IndexAttachments provideIndexAttachments(Configuration configuration) {
        if (configuration.getBoolean(ELASTICSEARCH_INDEX_ATTACHMENTS, DEFAULT_INDEX_ATTACHMENTS)) {
            return IndexAttachments.YES;
        }
        return IndexAttachments.NO;
    }




    private final IndexName indexMailboxName;
    private final ReadAliasName readAliasMailboxName;
    private final WriteAliasName writeAliasMailboxName;
    private final IndexAttachments indexAttachment;

    private ElasticSearchMailboxConfiguration(IndexName indexMailboxName, ReadAliasName readAliasMailboxName,
                                              WriteAliasName writeAliasMailboxName, IndexAttachments indexAttachment) {
        this.indexMailboxName = indexMailboxName;
        this.readAliasMailboxName = readAliasMailboxName;
        this.writeAliasMailboxName = writeAliasMailboxName;
        this.indexAttachment = indexAttachment;
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

    public IndexAttachments getIndexAttachment() {
        return indexAttachment;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ElasticSearchMailboxConfiguration) {
            ElasticSearchMailboxConfiguration that = (ElasticSearchMailboxConfiguration) o;

            return Objects.equals(this.indexAttachment, that.indexAttachment)
                && Objects.equals(this.indexMailboxName, that.indexMailboxName)
                && Objects.equals(this.readAliasMailboxName, that.readAliasMailboxName)
                && Objects.equals(this.writeAliasMailboxName, that.writeAliasMailboxName);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(indexMailboxName, readAliasMailboxName, writeAliasMailboxName, indexAttachment, writeAliasMailboxName);
    }
}
