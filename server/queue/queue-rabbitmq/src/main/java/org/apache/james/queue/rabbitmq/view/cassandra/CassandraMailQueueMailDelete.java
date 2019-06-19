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

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import javax.inject.Inject;

import org.apache.james.queue.rabbitmq.EnqueueId;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CassandraMailQueueMailDelete {

    private final DeletedMailsDAO deletedMailsDao;
    private final BrowseStartDAO browseStartDao;
    private final CassandraMailQueueBrowser cassandraMailQueueBrowser;
    private final CassandraMailQueueViewConfiguration configuration;

    @Inject
    CassandraMailQueueMailDelete(DeletedMailsDAO deletedMailsDao,
                                 BrowseStartDAO browseStartDao,
                                 CassandraMailQueueBrowser cassandraMailQueueBrowser,
                                 CassandraMailQueueViewConfiguration configuration) {
        this.deletedMailsDao = deletedMailsDao;
        this.browseStartDao = browseStartDao;
        this.cassandraMailQueueBrowser = cassandraMailQueueBrowser;
        this.configuration = configuration;
    }

    Mono<Void> considerDeleted(EnqueueId enqueueId, MailQueueName mailQueueName) {
        return deletedMailsDao
            .markAsDeleted(mailQueueName, enqueueId)
            .doOnNext(ignored -> maybeUpdateBrowseStart(mailQueueName));
    }

    Mono<Boolean> isDeleted(EnqueueId enqueueId, MailQueueName mailQueueName) {
        return deletedMailsDao.isDeleted(mailQueueName, enqueueId);
    }

    void updateBrowseStart(MailQueueName mailQueueName) {
        findNewBrowseStart(mailQueueName)
            .flatMap(newBrowseStart -> updateNewBrowseStart(mailQueueName, newBrowseStart))
            .subscribeOn(Schedulers.elastic())
            .subscribe();
    }

    private void maybeUpdateBrowseStart(MailQueueName mailQueueName) {
        if (shouldUpdateBrowseStart()) {
            updateBrowseStart(mailQueueName);
        }
    }

    private Mono<Instant> findNewBrowseStart(MailQueueName mailQueueName) {
        return cassandraMailQueueBrowser.browseReferences(mailQueueName)
            .map(enqueuedItem -> enqueuedItem.getSlicingContext().getTimeRangeStart())
            .next();
    }

    private Mono<Void> updateNewBrowseStart(MailQueueName mailQueueName, Instant newBrowseStartInstant) {
        return browseStartDao.updateBrowseStart(mailQueueName, newBrowseStartInstant);
    }

    private boolean shouldUpdateBrowseStart() {
        int threshold = configuration.getUpdateBrowseStartPace();
        return Math.abs(ThreadLocalRandom.current().nextInt()) % threshold == 0;
    }
}
