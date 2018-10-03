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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.model.MailKey;
import org.apache.mailet.Mail;

public class CassandraMailQueueMailDelete {

    private final DeletedMailsDAO deletedMailsDao;
    private final BrowseStartDAO browseStartDao;
    private final CassandraMailQueueBrowser cassandraMailQueueBrowser;
    private final CassandraMailQueueViewConfiguration configuration;
    private final ThreadLocalRandom random;

    @Inject
    CassandraMailQueueMailDelete(DeletedMailsDAO deletedMailsDao,
                                 BrowseStartDAO browseStartDao,
                                 CassandraMailQueueBrowser cassandraMailQueueBrowser,
                                 CassandraMailQueueViewConfiguration configuration,
                                 ThreadLocalRandom random) {
        this.deletedMailsDao = deletedMailsDao;
        this.browseStartDao = browseStartDao;
        this.cassandraMailQueueBrowser = cassandraMailQueueBrowser;
        this.configuration = configuration;
        this.random = random;
    }

    CompletableFuture<Void> considerDeleted(Mail mail, MailQueueName mailQueueName) {
        return deletedMailsDao
            .markAsDeleted(mailQueueName, MailKey.fromMail(mail))
            .thenRunAsync(() -> maybeUpdateBrowseStart(mailQueueName));
    }

    CompletableFuture<Boolean> isDeleted(Mail mail, MailQueueName mailQueueName) {
        return deletedMailsDao.isDeleted(mailQueueName, MailKey.fromMail(mail));
    }

    CompletableFuture<Void> updateBrowseStart(MailQueueName mailQueueName) {
        return findNewBrowseStart(mailQueueName)
            .thenCompose(newBrowseStart -> updateNewBrowseStart(mailQueueName, newBrowseStart));
    }

    private void maybeUpdateBrowseStart(MailQueueName mailQueueName) {
        if (shouldUpdateBrowseStart()) {
            updateBrowseStart(mailQueueName).join();
        }
    }

    private CompletableFuture<Optional<Instant>> findNewBrowseStart(MailQueueName mailQueueName) {
        return cassandraMailQueueBrowser.browseReferences(mailQueueName)
            .map(enqueuedItem -> enqueuedItem.getSlicingContext().getTimeRangeStart())
            .completableFuture()
            .thenApply(Stream::findFirst);
    }

    private CompletableFuture<Void> updateNewBrowseStart(MailQueueName mailQueueName, Optional<Instant> maybeNewBrowseStart) {
        return maybeNewBrowseStart
            .map(newBrowseStartInstant -> browseStartDao.updateBrowseStart(mailQueueName, newBrowseStartInstant))
            .orElse(CompletableFuture.completedFuture(null));
    }

    private boolean shouldUpdateBrowseStart() {
        int threshold = configuration.getUpdateBrowseStartPace();
        return Math.abs(random.nextInt()) % threshold == 0;
    }
}
