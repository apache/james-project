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

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;

import javax.inject.Inject;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mime4j.dom.Disposable;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.rabbitmq.EnqueueId;
import org.apache.james.queue.rabbitmq.EnqueuedItem;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.model.EnqueuedItemWithSlicingContext;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailQueueBrowser {

    static class CassandraMailQueueIterator implements ManageableMailQueue.MailQueueIterator {

        private final Iterator<CassandraMailQueueItemView> iterator;

        CassandraMailQueueIterator(Iterator<CassandraMailQueueItemView> iterator) {
            Preconditions.checkNotNull(iterator);

            this.iterator = iterator;
        }

        @Override
        public void close() {

        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public CassandraMailQueueItemView next() {
            return iterator.next();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMailQueueBrowser.class);

    private final BrowseStartDAO browseStartDao;
    private final DeletedMailsDAO deletedMailsDao;
    private final EnqueuedMailsDAO enqueuedMailsDao;
    private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
    private final CassandraMailQueueViewConfiguration configuration;
    private final Clock clock;

    @Inject
    CassandraMailQueueBrowser(BrowseStartDAO browseStartDao,
                              DeletedMailsDAO deletedMailsDao,
                              EnqueuedMailsDAO enqueuedMailsDao,
                              MimeMessageStore.Factory mimeMessageStoreFactory,
                              CassandraMailQueueViewConfiguration configuration,
                              Clock clock) {
        this.browseStartDao = browseStartDao;
        this.deletedMailsDao = deletedMailsDao;
        this.enqueuedMailsDao = enqueuedMailsDao;
        this.mimeMessageStore = mimeMessageStoreFactory.mimeMessageStore();
        this.configuration = configuration;
        this.clock = clock;
    }

    Flux<CassandraMailQueueItemView> browse(MailQueueName queueName) {
        return browseReferences(queueName)
            .flatMapSequential(this::toMailFuture)
            .map(CassandraMailQueueItemView::new);
    }

    Flux<CassandraMailQueueItemView> browseOlderThan(MailQueueName queueName, Instant olderThan) {
        return browseReferencesOlderThan(queueName, olderThan)
            .flatMapSequential(this::toMailFuture)
            .map(CassandraMailQueueItemView::new);
    }

    Flux<EnqueuedItemWithSlicingContext> browseReferencesOlderThan(MailQueueName queueName, Instant olderThan) {
        return browseStartDao.findBrowseStart(queueName)
            .flatMapMany(this::allSlicesStartingAt)
            .filter(slice -> slice.getStartSliceInstant().isBefore(olderThan))
            .flatMapSequential(slice -> browseSlice(queueName, slice))
            .filter(item -> item.getEnqueuedItem().getEnqueuedTime().isBefore(olderThan));
    }

    Flux<EnqueuedItemWithSlicingContext> browseReferences(MailQueueName queueName) {
        return browseStartDao.findBrowseStart(queueName)
            .flatMapMany(browseStart -> browseReferences(queueName, browseStart));
    }

    Flux<EnqueuedItemWithSlicingContext> browseReferences(MailQueueName queueName, Instant browseStart) {
        return allSlicesStartingAt(browseStart)
            .concatMap(slice -> browseSlice(queueName, slice));
    }

    private Mono<Pair<EnqueuedItem, Mail>> toMailFuture(EnqueuedItemWithSlicingContext enqueuedItemWithSlicingContext) {
        EnqueuedItem enqueuedItem = enqueuedItemWithSlicingContext.getEnqueuedItem();
        return mimeMessageStore.read(enqueuedItem.getPartsId())
            .map(mimeMessage -> Pair.of(enqueuedItem, toMail(enqueuedItem, mimeMessage)));
    }

    private Mail toMail(EnqueuedItem enqueuedItem, MimeMessage mimeMessage) {
        Mail mail = enqueuedItem.getMail();

        try {
            mail.setMessage(mimeMessage);
        } catch (MessagingException e) {
            LOGGER.error("error while setting mime message to mail {}", mail.getName(), e);
        }

        return mail;
    }

    private Flux<EnqueuedItemWithSlicingContext> browseSlice(MailQueueName queueName, Slice slice) {
        return allBucketIds()
            .concatMap(bucketId -> browseBucket(queueName, slice, bucketId), 4)
            .sort(Comparator.comparing(enqueuedMail -> enqueuedMail.getEnqueuedItem().getEnqueuedTime()));
    }

    private Flux<EnqueuedItemWithSlicingContext> browseBucket(MailQueueName queueName, Slice slice, BucketId bucketId) {
        return enqueuedMailsDao.selectEnqueuedMails(queueName, slice, bucketId)
            .filterWhen(mailReference -> deletedMailsDao.isStillEnqueued(queueName, mailReference.getEnqueuedItem().getEnqueueId()), 4);
    }

    private Flux<Slice> allSlicesStartingAt(Instant browseStart) {
        return Flux.fromStream(Slice.of(browseStart).allSlicesTill(clock.instant(), configuration.getSliceWindow()));
    }

    private Flux<BucketId> allBucketIds() {
        return Flux
            .range(0, configuration.getBucketCount())
            .map(BucketId::of);
    }

    public static class CassandraMailQueueItemView implements ManageableMailQueue.MailQueueItemView, Disposable {
        private final EnqueuedItem enqueuedItem;
        private final Mail mail;

        public CassandraMailQueueItemView(Pair<EnqueuedItem, Mail> pair) {
            this(pair.getLeft(), pair.getRight());
        }

        public CassandraMailQueueItemView(EnqueuedItem enqueuedItem, Mail mail) {
            this.enqueuedItem = enqueuedItem;
            this.mail = mail;
        }

        public EnqueueId getEnqueuedId() {
            return enqueuedItem.getEnqueueId();
        }

        public MimeMessagePartsId getEnqueuedPartsId() {
            return enqueuedItem.getPartsId();
        }

        @Override
        public Mail getMail() {
            return mail;
        }

        @Override
        public Optional<ZonedDateTime> getNextDelivery() {
            return Optional.empty();
        }

        @Override
        public void dispose() {
            LifecycleUtil.dispose(mail);
        }
    }
}
