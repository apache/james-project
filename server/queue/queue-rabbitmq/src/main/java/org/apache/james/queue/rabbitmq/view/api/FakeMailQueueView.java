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

package org.apache.james.queue.rabbitmq.view.api;

import java.time.Instant;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.rabbitmq.EnqueueId;
import org.apache.james.queue.rabbitmq.EnqueuedItem;
import org.apache.james.queue.rabbitmq.MailQueueName;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class FakeMailQueueView<V extends ManageableMailQueue.MailQueueItemView> implements MailQueueView<V> {
    public static class Factory implements MailQueueView.Factory {
        @Override
        public MailQueueView create(MailQueueName mailQueueName) {
            return new FakeMailQueueView();
        }
    }

    @Override
    public void initialize(MailQueueName mailQueueName) {

    }

    @Override
    public Mono<Void> storeMail(EnqueuedItem enqueuedItem) {
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> isPresent(EnqueueId id) {
        return Mono.just(true);
    }

    @Override
    public Mono<Long> delete(DeleteCondition deleteCondition) {
        return Mono.just(0L);
    }

    @Override
    public ManageableMailQueue.MailQueueIterator browse() {
        throw new NotImplementedException();
    }

    @Override
    public Flux<V> browseReactive() {
        throw new NotImplementedException();
    }

    @Override
    public Flux<V> browseOlderThanReactive(Instant olderThan) {
        throw new NotImplementedException();
    }

    @Override
    public long getSize() {
        return 0;
    }
}
