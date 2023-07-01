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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import javax.inject.Inject;

import org.apache.james.queue.rabbitmq.EnqueueId;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices;
import org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.Slice;
import org.apache.james.queue.rabbitmq.view.cassandra.model.EnqueuedItemWithSlicingContext.SlicingContext;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CassandraMailQueueMailDelete {

    private final DeletedMailsDAO deletedMailsDao;
    private final BrowseStartDAO browseStartDao;
    private final ContentStartDAO contentStartDAO;
    private final EnqueuedMailsDAO enqueuedMailsDAO;
    private final CassandraMailQueueBrowser cassandraMailQueueBrowser;
    private final CassandraMailQueueViewConfiguration configuration;
    private final Clock clock;

    @Inject
    CassandraMailQueueMailDelete(DeletedMailsDAO deletedMailsDao,
                                 BrowseStartDAO browseStartDao,
                                 ContentStartDAO contentStartDAO, EnqueuedMailsDAO enqueuedMailsDAO, CassandraMailQueueBrowser cassandraMailQueueBrowser,
                                 CassandraMailQueueViewConfiguration configuration, Clock clock) {
        this.deletedMailsDao = deletedMailsDao;
        this.browseStartDao = browseStartDao;
        this.contentStartDAO = contentStartDAO;
        this.enqueuedMailsDAO = enqueuedMailsDAO;
        this.cassandraMailQueueBrowser = cassandraMailQueueBrowser;
        this.configuration = configuration;
        this.clock = clock;
    }

    Mono<Void> considerDeleted(EnqueueId enqueueId, MailQueueName mailQueueName) {
        return deletedMailsDao
            .markAsDeleted(mailQueueName, enqueueId)
            .doFinally(any -> maybeUpdateBrowseStart(mailQueueName));
    }

    Mono<Boolean> isDeleted(EnqueueId enqueueId, MailQueueName mailQueueName) {
        return deletedMailsDao.isDeleted(mailQueueName, enqueueId);
    }

    void updateBrowseStart(MailQueueName mailQueueName) {
        updateBrowseStartReactive(mailQueueName)
            .subscribeOn(Schedulers.parallel())
            .subscribe();
    }

    Mono<Void> updateBrowseStartReactive(MailQueueName mailQueueName) {
        return findNewBrowseStart(mailQueueName)
            .flatMap(newBrowseStart -> updateNewBrowseStart(mailQueueName, newBrowseStart)
                .then(clearContentBeforeBrowse(mailQueueName, newBrowseStart)));
    }

    private void maybeUpdateBrowseStart(MailQueueName mailQueueName) {
        if (shouldUpdateBrowseStart()) {
            updateBrowseStart(mailQueueName);
        }
    }

    private Mono<Instant> findNewBrowseStart(MailQueueName mailQueueName) {
        Instant now = clock.instant();
        return browseStartDao.findBrowseStart(mailQueueName)
            .filter(browseStart -> browseStart.isBefore(now.minus(configuration.getSliceWindow())))
            .flatMap(browseStart -> cassandraMailQueueBrowser.browseReferences(mailQueueName, browseStart)
                .map(enqueuedItem -> enqueuedItem.getSlicingContext().getTimeRangeStart())
                .next()
                .filter(newBrowseStart -> newBrowseStart.isAfter(browseStart)));
    }

    private Mono<Void> updateNewBrowseStart(MailQueueName mailQueueName, Instant newBrowseStartInstant) {
        return browseStartDao.updateBrowseStart(mailQueueName, newBrowseStartInstant);
    }

    private Mono<Void> clearContentBeforeBrowse(MailQueueName mailQueueName, Instant newBrowseStartInstant) {
        return contentStartDAO.findContentStart(mailQueueName)
            .flatMapIterable(contentStart ->
                Slice.of(contentStart).allSlicesTill(newBrowseStartInstant, configuration.getSliceWindow())
                    .filter(slice -> slice.getStartSliceInstant().isBefore(newBrowseStartInstant))
                    .flatMap(slice -> IntStream.range(0, configuration.getBucketCount()).boxed()
                        .map(bucket -> SlicingContext.of(BucketedSlices.BucketId.of(bucket), slice.getStartSliceInstant())))
                    .collect(ImmutableList.toImmutableList()))
            .concatMap(slice -> deleteEmailsFromBrowseProjection(mailQueueName, slice))
            .concatMap(slice -> enqueuedMailsDAO.deleteBucket(mailQueueName, Slice.of(slice.getTimeRangeStart()), slice.getBucketId()))
            .then(contentStartDAO.updateContentStart(mailQueueName, newBrowseStartInstant));
    }

    private Mono<SlicingContext> deleteEmailsFromBrowseProjection(MailQueueName mailQueueName, SlicingContext slicingContext) {
        return enqueuedMailsDAO.selectEnqueuedMails(mailQueueName, Slice.of(slicingContext.getTimeRangeStart()), slicingContext.getBucketId())
            .flatMap(item -> deletedMailsDao.removeDeletedMark(mailQueueName, item.getEnqueuedItem().getEnqueueId())
                .then(Mono.fromRunnable(item::dispose).subscribeOn(Schedulers.boundedElastic())), DEFAULT_CONCURRENCY)
            .then()
            .thenReturn(slicingContext);
    }

    private boolean shouldUpdateBrowseStart() {
        int threshold = configuration.getUpdateBrowseStartPace();
        return ThreadLocalRandom.current().nextInt(threshold) % threshold == 0;
    }
}
