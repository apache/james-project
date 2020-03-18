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

import static com.datastax.driver.core.DataType.blob;
import static com.datastax.driver.core.DataType.cint;
import static com.datastax.driver.core.DataType.list;
import static com.datastax.driver.core.DataType.map;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timestamp;
import static com.datastax.driver.core.DataType.uuid;
import static com.datastax.driver.core.schemabuilder.TableOptions.CompactionOptions.TimeWindowCompactionStrategyOptions.CompactionWindowUnit.HOURS;

import org.apache.james.backends.cassandra.components.CassandraModule;

import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.TupleType;
import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface CassandraMailQueueViewModule {

    interface EnqueuedMailsTable {
        String TABLE_NAME = "enqueuedMailsV3";

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
            .compactionOptions(SchemaBuilder.timeWindowCompactionStrategy()
                .compactionWindowSize(1)
                .compactionWindowUnit(HOURS)))
        .statement(statement -> statement
            .addPartitionKey(EnqueuedMailsTable.QUEUE_NAME, text())
            .addPartitionKey(EnqueuedMailsTable.TIME_RANGE_START, timestamp())
            .addPartitionKey(EnqueuedMailsTable.BUCKET_ID, cint())
            .addClusteringColumn(EnqueuedMailsTable.ENQUEUE_ID, uuid())
            .addColumn(EnqueuedMailsTable.ENQUEUED_TIME, timestamp())
            .addColumn(EnqueuedMailsTable.NAME, text())
            .addColumn(EnqueuedMailsTable.STATE, text())
            .addColumn(EnqueuedMailsTable.HEADER_BLOB_ID, text())
            .addColumn(EnqueuedMailsTable.BODY_BLOB_ID, text())
            .addColumn(EnqueuedMailsTable.ATTRIBUTES, map(text(), blob()))
            .addColumn(EnqueuedMailsTable.ERROR_MESSAGE, text())
            .addColumn(EnqueuedMailsTable.SENDER, text())
            .addColumn(EnqueuedMailsTable.RECIPIENTS, list(text()))
            .addColumn(EnqueuedMailsTable.REMOTE_HOST, text())
            .addColumn(EnqueuedMailsTable.REMOTE_ADDR, text())
            .addColumn(EnqueuedMailsTable.LAST_UPDATED, timestamp())
            .addColumn(EnqueuedMailsTable.PER_RECIPIENT_SPECIFIC_HEADERS, list(TupleType.of(ProtocolVersion.NEWEST_SUPPORTED, CodecRegistry.DEFAULT_INSTANCE, text(), text(), text()))))

        .table(BrowseStartTable.TABLE_NAME)
        .comment("this table allows to find the starting point of iteration from the table: "
            + EnqueuedMailsTable.TABLE_NAME + " in order to make a browse operations through mail queues")
        .options(options -> options)
        .statement(statement -> statement
            .addPartitionKey(BrowseStartTable.QUEUE_NAME, text())
            .addColumn(BrowseStartTable.BROWSE_START, timestamp()))

        .table(DeletedMailTable.TABLE_NAME)
        .comment("this table stores the dequeued mails, while browsing mail from table: "
            + DeletedMailTable.TABLE_NAME + " we need to filter out mails have been dequeued by checking their " +
            "existence in this table")
        .options(options -> options
            .compactionOptions(SchemaBuilder.timeWindowCompactionStrategy()))
        .statement(statement -> statement
            .addPartitionKey(DeletedMailTable.QUEUE_NAME, text())
            .addPartitionKey(DeletedMailTable.ENQUEUE_ID, uuid()))

        .build();
}
