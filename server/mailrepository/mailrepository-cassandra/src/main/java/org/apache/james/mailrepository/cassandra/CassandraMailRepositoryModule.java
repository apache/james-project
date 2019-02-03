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

package org.apache.james.mailrepository.cassandra;

import static com.datastax.driver.core.DataType.bigint;
import static com.datastax.driver.core.DataType.blob;
import static com.datastax.driver.core.DataType.counter;
import static com.datastax.driver.core.DataType.list;
import static com.datastax.driver.core.DataType.map;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timestamp;
import static com.datastax.driver.core.schemabuilder.SchemaBuilder.frozen;

import org.apache.james.backends.cassandra.components.CassandraModule;

import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.TupleType;

public interface CassandraMailRepositoryModule {
    CassandraModule MODULE = CassandraModule.builder()
        .type(MailRepositoryTable.HEADER_TYPE)
        .statement(statement -> statement
            .addColumn(MailRepositoryTable.HEADER_NAME, text())
            .addColumn(MailRepositoryTable.HEADER_VALUE, text()))
        .table(MailRepositoryTable.COUNT_TABLE)
        .comment("Projection maintaining per mail repository mail counter")
        .statement(statement -> statement
            .addPartitionKey(MailRepositoryTable.REPOSITORY_NAME, text())
            .addColumn(MailRepositoryTable.COUNT, counter()))
        .table(MailRepositoryTable.KEYS_TABLE_NAME)
        .comment("Per-mailRepository mail key list")
        .statement(statement -> statement
            .addPartitionKey(MailRepositoryTable.REPOSITORY_NAME, text())
            .addClusteringColumn(MailRepositoryTable.MAIL_KEY, text()))
        .table(MailRepositoryTable.CONTENT_TABLE_NAME)
        .comment("Stores the mails for a given repository. " +
            "Content is stored with other blobs")
        .statement(statement -> statement
            .addPartitionKey(MailRepositoryTable.REPOSITORY_NAME, text())
            .addPartitionKey(MailRepositoryTable.MAIL_KEY, text())
            .addColumn(MailRepositoryTable.MESSAGE_SIZE, bigint())
            .addColumn(MailRepositoryTable.STATE, text())
            .addColumn(MailRepositoryTable.HEADER_BLOB_ID, text())
            .addColumn(MailRepositoryTable.BODY_BLOB_ID, text())
            .addColumn(MailRepositoryTable.ATTRIBUTES, map(text(), blob()))
            .addColumn(MailRepositoryTable.ERROR_MESSAGE, text())
            .addColumn(MailRepositoryTable.SENDER, text())
            .addColumn(MailRepositoryTable.RECIPIENTS, list(text()))
            .addColumn(MailRepositoryTable.REMOTE_HOST, text())
            .addColumn(MailRepositoryTable.REMOTE_ADDR, text())
            .addColumn(MailRepositoryTable.LAST_UPDATED, timestamp())
            .addUDTMapColumn(MailRepositoryTable.PER_RECIPIENT_SPECIFIC_HEADERS, text(), frozen(MailRepositoryTable.HEADER_TYPE)))
        .table(MailRepositoryTableV2.CONTENT_TABLE_NAME)
        .comment("Stores the mails for a given repository. " +
            "Content is stored with other blobs. " +
            "This v2 version was introduced to support multiple headers for each user. " +
            "The attributes are store as Json introduced in Mailet API v3.2.")
        .statement(statement -> statement
            .addPartitionKey(MailRepositoryTable.REPOSITORY_NAME, text())
            .addPartitionKey(MailRepositoryTable.MAIL_KEY, text())
            .addColumn(MailRepositoryTable.MESSAGE_SIZE, bigint())
            .addColumn(MailRepositoryTable.STATE, text())
            .addColumn(MailRepositoryTable.HEADER_BLOB_ID, text())
            .addColumn(MailRepositoryTable.BODY_BLOB_ID, text())
            .addColumn(MailRepositoryTable.ATTRIBUTES, map(text(), text()))
            .addColumn(MailRepositoryTable.ERROR_MESSAGE, text())
            .addColumn(MailRepositoryTable.SENDER, text())
            .addColumn(MailRepositoryTable.RECIPIENTS, list(text()))
            .addColumn(MailRepositoryTable.REMOTE_HOST, text())
            .addColumn(MailRepositoryTable.REMOTE_ADDR, text())
            .addColumn(MailRepositoryTable.LAST_UPDATED, timestamp())
            .addColumn(MailRepositoryTable.PER_RECIPIENT_SPECIFIC_HEADERS, list(TupleType.of(ProtocolVersion.NEWEST_SUPPORTED, CodecRegistry.DEFAULT_INSTANCE, text(), text(), text()))))
        .build();
}
