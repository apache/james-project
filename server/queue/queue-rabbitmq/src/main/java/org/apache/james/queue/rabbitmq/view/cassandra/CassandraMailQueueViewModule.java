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

package org.apache.james.queue.rabbitmq.view.cassandra;


import org.apache.james.backends.cassandra.components.CassandraModule;

import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.schema.compaction.TimeWindowCompactionStrategy;


public interface CassandraMailQueueViewModule {

    interface EnqueuedMailsTable {
        String TABLE_NAME = "enqueuedMailsV4";

        String QUEUE_NAME = "queueName";
        String TIME_RANGE_START = "timeRangeStart";
        String BUCKET_ID = "bucketId";

        String ENQUEUED_TIME = "enqueuedTime";
        String ENQUEUE_ID = "enqueueId";
        String NAME = "name";
        String HEADER_BLOB_ID = "headerBlobId";
        String BODY_BLOB_ID = "bodyBlobId";
        String STATE = "state";
        String SENDER = "sender";
        String RECIPIENTS = "recipients";
        String ATTRIBUTES = "attributes";
        String ERROR_MESSAGE = "errorMessage";
        String REMOTE_HOST = "remoteHost";
        String REMOTE_ADDR = "remoteAddr";
        String LAST_UPDATED = "lastUpdated";
        String PER_RECIPIENT_SPECIFIC_HEADERS = "perRecipientSpecificHeaders";
    }

    interface BrowseStartTable {
        String TABLE_NAME = "browseStart";

        String QUEUE_NAME = "queueName";
        String BROWSE_START = "browseStart";
    }

    interface ContentStartTable {
        String TABLE_NAME = "contentStart";

        String QUEUE_NAME = "queueName";
        String CONTENT_START = "contentStart";
    }

    interface DeletedMailTable {
        String TABLE_NAME = "deletedMailsV2";

        String QUEUE_NAME = "queueName";
        String ENQUEUE_ID = "enqueueId";
    }

    interface HeaderEntry {
        int USER_INDEX = 0;
        int HEADER_NAME_INDEX = 1;
        int HEADER_VALUE_INDEX = 2;
    }

    CassandraModule MODULE = CassandraModule
        .table(EnqueuedMailsTable.TABLE_NAME)
        .comment("store enqueued mails, if a mail is enqueued into a mail queue, it also being stored in this table," +
            " when a mail is dequeued from a mail queue, the record associated with that mail still available in this" +
            " table and will not be deleted immediately regarding to the performance impacts," +
            " but after some scheduled tasks")
        .options(options -> options
            .withCompaction(SchemaBuilder.timeWindowCompactionStrategy()
                .withCompactionWindow(1, TimeWindowCompactionStrategy.CompactionWindowUnit.HOURS)))
        .statement(statement -> types -> statement
            .withPartitionKey(EnqueuedMailsTable.QUEUE_NAME, DataTypes.TEXT)
            .withPartitionKey(EnqueuedMailsTable.TIME_RANGE_START, DataTypes.TIMESTAMP)
            .withPartitionKey(EnqueuedMailsTable.BUCKET_ID, DataTypes.INT)
            .withClusteringColumn(EnqueuedMailsTable.ENQUEUE_ID, DataTypes.UUID)
            .withColumn(EnqueuedMailsTable.ENQUEUED_TIME, DataTypes.TIMESTAMP)
            .withColumn(EnqueuedMailsTable.NAME, DataTypes.TEXT)
            .withColumn(EnqueuedMailsTable.STATE, DataTypes.TEXT)
            .withColumn(EnqueuedMailsTable.HEADER_BLOB_ID, DataTypes.TEXT)
            .withColumn(EnqueuedMailsTable.BODY_BLOB_ID, DataTypes.TEXT)
            .withColumn(EnqueuedMailsTable.ATTRIBUTES, DataTypes.frozenMapOf(DataTypes.TEXT, DataTypes.BLOB))
            .withColumn(EnqueuedMailsTable.ERROR_MESSAGE, DataTypes.TEXT)
            .withColumn(EnqueuedMailsTable.SENDER, DataTypes.TEXT)
            .withColumn(EnqueuedMailsTable.RECIPIENTS, DataTypes.frozenListOf(DataTypes.TEXT))
            .withColumn(EnqueuedMailsTable.REMOTE_HOST, DataTypes.TEXT)
            .withColumn(EnqueuedMailsTable.REMOTE_ADDR, DataTypes.TEXT)
            .withColumn(EnqueuedMailsTable.LAST_UPDATED, DataTypes.TIMESTAMP)
            .withColumn(EnqueuedMailsTable.PER_RECIPIENT_SPECIFIC_HEADERS, DataTypes.frozenListOf(DataTypes.tupleOf(DataTypes.TEXT, DataTypes.TEXT, DataTypes.TEXT))))

        .table(BrowseStartTable.TABLE_NAME)
        .comment("this table allows to find the starting point of iteration from the table: "
            + EnqueuedMailsTable.TABLE_NAME + " in order to make a browse operation through mail queues")
        .options(options -> options)
        .statement(statement -> types -> statement
            .withPartitionKey(BrowseStartTable.QUEUE_NAME, DataTypes.TEXT)
            .withColumn(BrowseStartTable.BROWSE_START, DataTypes.TIMESTAMP))

        .table(ContentStartTable.TABLE_NAME)
        .comment("this table allows to find the starting point of content from the table: "
            + EnqueuedMailsTable.TABLE_NAME + " in order to make a browse operation through mail queues. Strictly " +
            "before browse start, it enables queue cleanup.")
        .options(options -> options)
        .statement(statement -> types -> statement
            .withPartitionKey(ContentStartTable.QUEUE_NAME, DataTypes.TEXT)
            .withColumn(ContentStartTable.CONTENT_START, DataTypes.TIMESTAMP))

        .table(DeletedMailTable.TABLE_NAME)
        .comment("this table stores the dequeued mails, while browsing mail from table: "
            + DeletedMailTable.TABLE_NAME + " we need to filter out mails have been dequeued by checking their " +
            "existence in this table")
        .options(options -> options
            .withCompaction(SchemaBuilder.timeWindowCompactionStrategy())
            .withBloomFilterFpChance(0.01))
        .statement(statement -> types -> statement
            .withPartitionKey(DeletedMailTable.QUEUE_NAME, DataTypes.TEXT)
            .withPartitionKey(DeletedMailTable.ENQUEUE_ID, DataTypes.UUID))

        .build();
}
