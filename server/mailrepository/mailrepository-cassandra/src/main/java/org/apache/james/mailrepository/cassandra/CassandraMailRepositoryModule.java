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

import static com.datastax.oss.driver.api.core.type.DataTypes.BIGINT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMESTAMP;
import static com.datastax.oss.driver.api.core.type.DataTypes.listOf;
import static com.datastax.oss.driver.api.core.type.DataTypes.mapOf;
import static com.datastax.oss.driver.api.core.type.DataTypes.tupleOf;

import org.apache.james.backends.cassandra.components.CassandraModule;

public interface CassandraMailRepositoryModule {
    CassandraModule MODULE = CassandraModule.builder()
        .type(MailRepositoryTable.HEADER_TYPE)
        .statement(statement -> statement
            .withField(MailRepositoryTable.HEADER_NAME, TEXT)
            .withField(MailRepositoryTable.HEADER_VALUE, TEXT))

        .table(MailRepositoryTable.KEYS_TABLE_NAME)
        .comment("Per-mailRepository mail key list")
        .statement(statement -> types -> statement
            .withPartitionKey(MailRepositoryTable.REPOSITORY_NAME, TEXT)
            .withClusteringColumn(MailRepositoryTable.MAIL_KEY, TEXT))

        .table(MailRepositoryTableV2.CONTENT_TABLE_NAME)
        .comment("Stores the mails for a given repository. " +
            "Content is stored with other blobs. " +
            "This v2 version was introduced to support multiple headers for each user. " +
            "The attributes are store as Json introduced in Mailet API v3.2.")
        .statement(statement -> types -> statement
            .withPartitionKey(MailRepositoryTable.REPOSITORY_NAME, TEXT)
            .withPartitionKey(MailRepositoryTable.MAIL_KEY, TEXT)
            .withColumn(MailRepositoryTable.MESSAGE_SIZE, BIGINT)
            .withColumn(MailRepositoryTable.STATE, TEXT)
            .withColumn(MailRepositoryTable.HEADER_BLOB_ID, TEXT)
            .withColumn(MailRepositoryTable.BODY_BLOB_ID, TEXT)
            .withColumn(MailRepositoryTable.ATTRIBUTES, mapOf(TEXT, TEXT))
            .withColumn(MailRepositoryTable.ERROR_MESSAGE, TEXT)
            .withColumn(MailRepositoryTable.SENDER, TEXT)
            .withColumn(MailRepositoryTable.RECIPIENTS, listOf(TEXT))
            .withColumn(MailRepositoryTable.REMOTE_HOST, TEXT)
            .withColumn(MailRepositoryTable.REMOTE_ADDR, TEXT)
            .withColumn(MailRepositoryTable.LAST_UPDATED, TIMESTAMP)
            .withColumn(MailRepositoryTable.PER_RECIPIENT_SPECIFIC_HEADERS, listOf(tupleOf(TEXT, TEXT, TEXT))))
        .build();
}
