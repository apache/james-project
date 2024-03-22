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

import java.time.Clock;
import java.time.Instant;

import jakarta.inject.Inject;

import org.apache.james.queue.rabbitmq.EnqueuedItem;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.BucketId;
import org.apache.james.queue.rabbitmq.view.cassandra.model.EnqueuedItemWithSlicingContext;
import org.apache.mailet.Mail;

import reactor.core.publisher.Mono;

public class CassandraMailQueueMailStore {

    private final EnqueuedMailsDAO enqueuedMailsDao;
    private final BrowseStartDAO browseStartDao;
    private final ContentStartDAO contentStartDAO;
    private final CassandraMailQueueViewConfiguration configuration;
    private final Clock clock;

    @Inject
    CassandraMailQueueMailStore(EnqueuedMailsDAO enqueuedMailsDao,
                                BrowseStartDAO browseStartDao,
                                ContentStartDAO contentStartDAO, CassandraMailQueueViewConfiguration configuration,
                                Clock clock) {
        this.enqueuedMailsDao = enqueuedMailsDao;
        this.browseStartDao = browseStartDao;
        this.contentStartDAO = contentStartDAO;
        this.configuration = configuration;
        this.clock = clock;
    }

    Mono<Void> storeMail(EnqueuedItem enqueuedItem) {
        EnqueuedItemWithSlicingContext enqueuedItemAndSlicing = addSliceContext(enqueuedItem);

        return enqueuedMailsDao.insert(enqueuedItemAndSlicing);
    }

    Mono<Void> initializeBrowseStart(MailQueueName mailQueueName) {
        return browseStartDao
            .insertInitialBrowseStart(mailQueueName, currentSliceStartInstant());
    }

    Mono<Void> initializeContentStart(MailQueueName mailQueueName) {
        return browseStartDao.findBrowseStart(mailQueueName)
            .flatMap(browseStart ->
                contentStartDAO.insertInitialContentStart(mailQueueName, browseStart));
    }

    private EnqueuedItemWithSlicingContext addSliceContext(EnqueuedItem enqueuedItem) {
        Mail mail = enqueuedItem.getMail();

        return EnqueuedItemWithSlicingContext.builder()
            .enqueuedItem(enqueuedItem)
            .slicingContext(EnqueuedItemWithSlicingContext.SlicingContext
                .of(computedBucketId(mail), currentSliceStartInstant()))
            .build();
    }

    private Instant currentSliceStartInstant() {
        long sliceSize = configuration.getSliceWindow().getSeconds();
        long sliceId = clock.instant().getEpochSecond() / sliceSize;
        return Instant.ofEpochSecond(sliceId * sliceSize);
    }

    private BucketId computedBucketId(Mail mail) {
        return BucketId.of(mail.getName(), configuration.getBucketCount());
    }
}
