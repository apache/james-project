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

import static org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.BucketId;
import static org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.Slice;
import static org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.Slice.allSlicesTill;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.model.EnqueuedMail;
import org.apache.james.util.FluentFutureStream;

import com.google.common.base.Preconditions;

class CassandraMailQueueBrowser {

    static class CassandraMailQueueIterator implements ManageableMailQueue.MailQueueIterator {

        private final Iterator<ManageableMailQueue.MailQueueItemView> iterator;

        CassandraMailQueueIterator(Iterator<ManageableMailQueue.MailQueueItemView> iterator) {
            Preconditions.checkNotNull(iterator);

            this.iterator = iterator;
        }

        @Override
        public void close() {}

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public ManageableMailQueue.MailQueueItemView next() {
            return iterator.next();
        }
    }

    private final BrowseStartDAO browseStartDao;
    private final DeletedMailsDAO deletedMailsDao;
    private final EnqueuedMailsDAO enqueuedMailsDao;
    private final CassandraMailQueueViewConfiguration configuration;
    private final Clock clock;

    @Inject
    CassandraMailQueueBrowser(BrowseStartDAO browseStartDao,
                              DeletedMailsDAO deletedMailsDao,
                              EnqueuedMailsDAO enqueuedMailsDao,
                              CassandraMailQueueViewConfiguration configuration,
                              Clock clock) {
        this.browseStartDao = browseStartDao;
        this.deletedMailsDao = deletedMailsDao;
        this.enqueuedMailsDao = enqueuedMailsDao;
        this.configuration = configuration;
        this.clock = clock;
    }

    CompletableFuture<Stream<ManageableMailQueue.MailQueueItemView>> browse(MailQueueName queueName) {
        return browseReferences(queueName)
            .map(EnqueuedMail::getMail)
            .map(ManageableMailQueue.MailQueueItemView::new)
            .completableFuture();
    }

    FluentFutureStream<EnqueuedMail> browseReferences(MailQueueName queueName) {
        return FluentFutureStream.of(browseStartDao.findBrowseStart(queueName)
            .thenApply(this::allSlicesStartingAt))
            .map(slice -> browseSlice(queueName, slice), FluentFutureStream::unboxFluentFuture);
    }

    private FluentFutureStream<EnqueuedMail> browseSlice(MailQueueName queueName, Slice slice) {
        return FluentFutureStream.of(
            allBucketIds()
                .map(bucketId ->
                    browseBucket(queueName, slice, bucketId).completableFuture()),
            FluentFutureStream::unboxStream)
            .sorted(Comparator.comparing(EnqueuedMail::getEnqueuedTime));
    }

    private FluentFutureStream<EnqueuedMail> browseBucket(MailQueueName queueName, Slice slice, BucketId bucketId) {
        return FluentFutureStream.of(
            enqueuedMailsDao.selectEnqueuedMails(queueName, slice, bucketId))
                .thenFilter(mailReference -> deletedMailsDao.isStillEnqueued(queueName, mailReference.getMailKey()));
    }

    private Stream<Slice> allSlicesStartingAt(Optional<Instant> maybeBrowseStart) {
        return maybeBrowseStart
            .map(Slice::of)
            .map(startSlice -> allSlicesTill(startSlice, clock.instant(), configuration.getSliceWindow()))
            .orElse(Stream.empty());
    }

    private Stream<BucketId> allBucketIds() {
        return IntStream
            .range(0, configuration.getBucketCount())
            .mapToObj(BucketId::of);
    }
}
