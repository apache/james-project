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

package org.apache.james.mailbox.elasticsearch.v7;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.backends.es.IndexName;
import org.apache.james.backends.es.ReadAliasName;
import org.apache.james.backends.es.WriteAliasName;

public class ElasticSearchMailboxConfiguration {

    public static class Builder {
        private Optional<IndexName> indexMailboxName;
        private Optional<ReadAliasName> readAliasMailboxName;
        private Optional<WriteAliasName> writeAliasMailboxName;
        private Optional<IndexAttachments> indexAttachment;

        Builder() {
            indexMailboxName = Optional.empty();
            readAliasMailboxName = Optional.empty();
            writeAliasMailboxName = Optional.empty();
            indexAttachment = Optional.empty();
        }

        Builder indexMailboxName(Optional<IndexName> indexMailboxName) {
            this.indexMailboxName = indexMailboxName;
            return this;
        }

        Builder readAliasMailboxName(Optional<ReadAliasName> readAliasMailboxName) {
            this.readAliasMailboxName = readAliasMailboxName;
            return this;
        }

        Builder writeAliasMailboxName(Optional<WriteAliasName> writeAliasMailboxName) {
            this.writeAliasMailboxName = writeAliasMailboxName;
            return this;
        }


        Builder indexAttachment(IndexAttachments indexAttachment) {
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

    private static final String ELASTICSEARCH_INDEX_NAME = "elasticsearch.index.name";
    private static final String ELASTICSEARCH_INDEX_MAILBOX_NAME = "elasticsearch.index.mailbox.name";
    private static final String ELASTICSEARCH_ALIAS_READ_NAME = "elasticsearch.alias.read.name";
    private static final String ELASTICSEARCH_ALIAS_WRITE_NAME = "elasticsearch.alias.write.name";
    private static final String ELASTICSEARCH_ALIAS_READ_MAILBOX_NAME = "elasticsearch.alias.read.mailbox.name";
    private static final String ELASTICSEARCH_ALIAS_WRITE_MAILBOX_NAME = "elasticsearch.alias.write.mailbox.name";
    private static final String ELASTICSEARCH_INDEX_ATTACHMENTS = "elasticsearch.indexAttachments";
    private static final boolean DEFAULT_INDEX_ATTACHMENTS = true;

    public static final ElasticSearchMailboxConfiguration DEFAULT_CONFIGURATION = builder().build();

    public static ElasticSearchMailboxConfiguration fromProperties(Configuration configuration) {
        return builder()
            .indexMailboxName(computeMailboxIndexName(configuration))
            .readAliasMailboxName(computeMailboxReadAlias(configuration))
            .writeAliasMailboxName(computeMailboxWriteAlias(configuration))
            .indexAttachment(provideIndexAttachments(configuration))
            .build();
    }

    static Optional<IndexName> computeMailboxIndexName(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_INDEX_MAILBOX_NAME))
                .map(IndexName::new)
            .or(() -> Optional.ofNullable(configuration.getString(ELASTICSEARCH_INDEX_NAME))
                .map(IndexName::new));
    }

    static Optional<WriteAliasName> computeMailboxWriteAlias(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_WRITE_MAILBOX_NAME))
                .map(WriteAliasName::new)
            .or(() -> Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_WRITE_NAME))
                .map(WriteAliasName::new));
    }

    static Optional<ReadAliasName> computeMailboxReadAlias(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_READ_MAILBOX_NAME))
                .map(ReadAliasName::new)
            .or(() -> Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_READ_NAME))
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
