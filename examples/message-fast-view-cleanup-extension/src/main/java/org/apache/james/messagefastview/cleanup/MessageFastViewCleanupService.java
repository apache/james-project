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

package org.apache.james.messagefastview.cleanup;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MessageFastViewCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageFastViewCleanupService.class);
    private static final int MESSAGE_IDS_PER_SECOND = 1000;
    private static final Funnel<CharSequence> BLOOM_FILTER_FUNNEL = Funnels.stringFunnel(StandardCharsets.US_ASCII);
    public static final int EXPECTED_BLOOM_FILTER_INSERTIONS = 50000000;
    public static final double FALSE_POSITIVE_PROBABLITY = 0.01;

    private final CassandraMessageDAO messageDAO;
    private final CassandraMessageFastViewDAO messageFastViewDAO;

    @Inject
    public MessageFastViewCleanupService(CassandraMessageDAO messageDAO, CassandraMessageFastViewDAO messageFastViewDAO) {
        this.messageDAO = messageDAO;
        this.messageFastViewDAO = messageFastViewDAO;
    }

    public Mono<Long> cleanup() {
        String salt = UUID.randomUUID().toString();
        return populatedBloomFilter(salt)
            .flatMap(bloomFilter -> cleanup(bloomFilter, salt));
    }

    private Mono<Long> cleanup(BloomFilter<CharSequence> bloomFilter, String salt) {
        AtomicLong totalDeleted = new AtomicLong();
        return Flux.from(messageFastViewDAO.getAllMessageIds())
            .filter(messageId -> !bloomFilter.mightContain(salt + messageId.serialize()))
            .flatMap(messageId -> Mono.from(messageFastViewDAO.delete(messageId))
                    .doOnSuccess(any -> LOGGER.info("Total records deleted: {}", totalDeleted.incrementAndGet())),
                MESSAGE_IDS_PER_SECOND)
            .then()
            .then(Mono.fromCallable(totalDeleted::get))
            .doFinally(any -> LOGGER.info("Message fast view cleanup complete. Total deleted: {}", totalDeleted.get()));
    }

    private Mono<BloomFilter<CharSequence>> populatedBloomFilter(String salt) {
        return Mono.fromCallable(() -> BloomFilter.create(
                BLOOM_FILTER_FUNNEL,
                EXPECTED_BLOOM_FILTER_INSERTIONS,
                FALSE_POSITIVE_PROBABLITY))
            .flatMap(bloomFilter -> messageDAO.getAllMessageIds()
                .concatMap(messageId -> Mono.fromRunnable(() -> bloomFilter.put(salt + messageId.serialize())))
                .then()
                .thenReturn(bloomFilter));
    }
}
