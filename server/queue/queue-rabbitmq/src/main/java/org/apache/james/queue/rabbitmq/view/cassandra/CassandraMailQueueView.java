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

import static org.apache.james.util.FunctionalUtils.negate;

import java.time.Instant;

import javax.inject.Inject;
import javax.mail.internet.MimeMessage;

import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.rabbitmq.EnqueueId;
import org.apache.james.queue.rabbitmq.EnqueuedItem;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.api.DeleteCondition;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.model.EnqueuedItemWithSlicingContext;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailQueueView implements MailQueueView<CassandraMailQueueBrowser.CassandraMailQueueItemView> {

    public static class Factory implements MailQueueView.Factory {
        private final CassandraMailQueueMailStore storeHelper;
        private final CassandraMailQueueBrowser cassandraMailQueueBrowser;
        private final CassandraMailQueueMailDelete cassandraMailQueueMailDelete;
        private final MimeMessageStore.Factory mimeMessageStoreFactory;

        @Inject
        public Factory(CassandraMailQueueMailStore storeHelper,
                       CassandraMailQueueBrowser cassandraMailQueueBrowser,
                       CassandraMailQueueMailDelete cassandraMailQueueMailDelete,
                       MimeMessageStore.Factory mimeMessageStoreFactory) {
            this.storeHelper = storeHelper;
            this.cassandraMailQueueBrowser = cassandraMailQueueBrowser;
            this.cassandraMailQueueMailDelete = cassandraMailQueueMailDelete;
            this.mimeMessageStoreFactory = mimeMessageStoreFactory;
        }

        @Override
        public MailQueueView create(MailQueueName mailQueueName) {
            return new CassandraMailQueueView(storeHelper, mailQueueName, cassandraMailQueueBrowser, cassandraMailQueueMailDelete,
                mimeMessageStoreFactory.mimeMessageStore());
        }
    }

    private static final int DELETION_CONCURRENCY = 8;

    private final CassandraMailQueueMailStore storeHelper;
    private final CassandraMailQueueBrowser cassandraMailQueueBrowser;
    private final CassandraMailQueueMailDelete cassandraMailQueueMailDelete;
    private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;

    private final MailQueueName mailQueueName;

    CassandraMailQueueView(CassandraMailQueueMailStore storeHelper,
                           MailQueueName mailQueueName,
                           CassandraMailQueueBrowser cassandraMailQueueBrowser,
                           CassandraMailQueueMailDelete cassandraMailQueueMailDelete, Store<MimeMessage, MimeMessagePartsId> mimeMessageStore) {
        this.mailQueueName = mailQueueName;
        this.storeHelper = storeHelper;
        this.cassandraMailQueueBrowser = cassandraMailQueueBrowser;
        this.cassandraMailQueueMailDelete = cassandraMailQueueMailDelete;
        this.mimeMessageStore = mimeMessageStore;
    }

    public Mono<Void> updateBrowseStart() {
        return cassandraMailQueueMailDelete.updateBrowseStartReactive(mailQueueName);
    }

    @Override
    public void initialize(MailQueueName mailQueueName) {
        storeHelper.initializeBrowseStart(mailQueueName).block();
        storeHelper.initializeContentStart(mailQueueName).block();
    }

    @Override
    public Mono<Void> storeMail(EnqueuedItem enqueuedItem) {
        return storeHelper.storeMail(enqueuedItem);
    }

    @Override
    public ManageableMailQueue.MailQueueIterator browse() {
        return new CassandraMailQueueBrowser.CassandraMailQueueIterator(
            browseReactive()
                .toIterable()
                .iterator());
    }

    @Override
    public Flux<CassandraMailQueueBrowser.CassandraMailQueueItemView> browseReactive() {
        return cassandraMailQueueBrowser.browse(mailQueueName);
    }

    @Override
    public Flux<CassandraMailQueueBrowser.CassandraMailQueueItemView> browseOlderThanReactive(Instant olderThan) {
        return cassandraMailQueueBrowser.browseOlderThan(mailQueueName, olderThan);
    }

    @Override
    public long getSize() {
        return getSizeReactive()
            .block();
    }

    @Override
    public Mono<Long> getSizeReactive() {
        return cassandraMailQueueBrowser.browseReferences(mailQueueName)
            .count();
    }

    @Override
    public Mono<Long> delete(DeleteCondition deleteCondition) {
        if (deleteCondition instanceof DeleteCondition.WithEnqueueId) {
            DeleteCondition.WithEnqueueId enqueueIdCondition = (DeleteCondition.WithEnqueueId) deleteCondition;
            return delete(enqueueIdCondition.getEnqueueId(), enqueueIdCondition.getBlobIds())
                .thenReturn(1L);
        }
        return browseThenDelete(deleteCondition);
    }

    private Mono<Long> browseThenDelete(DeleteCondition deleteCondition) {
        return cassandraMailQueueBrowser.browseReferences(mailQueueName)
            .map(EnqueuedItemWithSlicingContext::getEnqueuedItem)
            .filter(deleteCondition::shouldBeDeleted)
            .flatMap(mailReference -> cassandraMailQueueMailDelete.considerDeleted(mailReference.getEnqueueId(), mailQueueName)
                .then(Mono.from(mimeMessageStore.delete(mailReference.getPartsId()))), DELETION_CONCURRENCY)
            .count()
            .doOnNext(ignored -> cassandraMailQueueMailDelete.updateBrowseStart(mailQueueName));
    }

    private Mono<Void> delete(EnqueueId enqueueId,
                              MimeMessagePartsId blobIds) {
        return cassandraMailQueueMailDelete.considerDeleted(enqueueId, mailQueueName)
            .then(Mono.from(mimeMessageStore.delete(blobIds)));
    }

    @Override
    public Mono<Boolean> isPresent(EnqueueId id) {
        return cassandraMailQueueMailDelete.isDeleted(id, mailQueueName)
            .map(negate());
    }
}
