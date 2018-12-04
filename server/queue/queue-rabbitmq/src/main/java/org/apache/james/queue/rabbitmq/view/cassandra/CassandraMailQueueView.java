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

import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.rabbitmq.EnqueuedItem;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.api.DeleteCondition;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.EventsourcingConfigurationManagement;
import org.apache.james.queue.rabbitmq.view.cassandra.model.EnqueuedItemWithSlicingContext;
import org.apache.james.queue.rabbitmq.view.cassandra.model.MailKey;
import org.apache.mailet.Mail;

import reactor.core.publisher.Mono;

public class CassandraMailQueueView implements MailQueueView {

    public static class Factory implements MailQueueView.Factory {
        private final CassandraMailQueueMailStore storeHelper;
        private final CassandraMailQueueBrowser cassandraMailQueueBrowser;
        private final CassandraMailQueueMailDelete cassandraMailQueueMailDelete;

        @Inject
        public Factory(CassandraMailQueueMailStore storeHelper,
                       CassandraMailQueueBrowser cassandraMailQueueBrowser,
                       CassandraMailQueueMailDelete cassandraMailQueueMailDelete,
                       EventsourcingConfigurationManagement eventsourcingConfigurationManagement,
                       CassandraMailQueueViewConfiguration configuration) {
            this.storeHelper = storeHelper;
            this.cassandraMailQueueBrowser = cassandraMailQueueBrowser;
            this.cassandraMailQueueMailDelete = cassandraMailQueueMailDelete;

            eventsourcingConfigurationManagement.registerConfiguration(configuration);
        }

        @Override
        public MailQueueView create(MailQueueName mailQueueName) {
            return new CassandraMailQueueView(storeHelper, mailQueueName, cassandraMailQueueBrowser, cassandraMailQueueMailDelete);
        }
    }

    private final CassandraMailQueueMailStore storeHelper;
    private final CassandraMailQueueBrowser cassandraMailQueueBrowser;
    private final CassandraMailQueueMailDelete cassandraMailQueueMailDelete;

    private final MailQueueName mailQueueName;

    CassandraMailQueueView(CassandraMailQueueMailStore storeHelper,
                           MailQueueName mailQueueName,
                           CassandraMailQueueBrowser cassandraMailQueueBrowser,
                           CassandraMailQueueMailDelete cassandraMailQueueMailDelete) {
        this.mailQueueName = mailQueueName;
        this.storeHelper = storeHelper;
        this.cassandraMailQueueBrowser = cassandraMailQueueBrowser;
        this.cassandraMailQueueMailDelete = cassandraMailQueueMailDelete;
    }

    @Override
    public void initialize(MailQueueName mailQueueName) {
        storeHelper.initializeBrowseStart(mailQueueName).block();
    }

    @Override
    public CompletableFuture<Void> storeMail(EnqueuedItem enqueuedItem) {
        return storeHelper.storeMail(enqueuedItem).toFuture();
    }

    @Override
    public ManageableMailQueue.MailQueueIterator browse() {
        return new CassandraMailQueueBrowser.CassandraMailQueueIterator(
            cassandraMailQueueBrowser.browse(mailQueueName)
                .toIterable()
                .iterator());
    }

    @Override
    public long getSize() {
        return cassandraMailQueueBrowser.browseReferences(mailQueueName).count().block();
    }

    @Override
    public long delete(DeleteCondition deleteCondition) {
        if (deleteCondition instanceof DeleteCondition.WithName) {
            DeleteCondition.WithName nameDeleteCondition = (DeleteCondition.WithName) deleteCondition;
            return delete(MailKey.of(nameDeleteCondition.getName())).map(any -> 1L).block();
        }
        return browseThenDelete(deleteCondition);
    }

    private long browseThenDelete(DeleteCondition deleteCondition) {
        return cassandraMailQueueBrowser.browseReferences(mailQueueName)
            .map(EnqueuedItemWithSlicingContext::getEnqueuedItem)
            .filter(mailReference -> deleteCondition.shouldBeDeleted(mailReference.getMail()))
            .map(mailReference -> cassandraMailQueueMailDelete.considerDeleted(mailReference.getMail(), mailQueueName))
            .count()
            .doOnTerminate(() -> cassandraMailQueueMailDelete.updateBrowseStart(mailQueueName))
            .block();
    }

    private Mono<Void> delete(MailKey mailKey) {
        return cassandraMailQueueMailDelete.considerDeleted(mailKey, mailQueueName);
    }

    @Override
    public CompletableFuture<Boolean> isPresent(Mail mail) {
        return cassandraMailQueueMailDelete.isDeleted(mail, mailQueueName)
                .map(bool -> !bool)
                .toFuture();
    }
}
