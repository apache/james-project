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

package org.apache.james.jmap.memory.upload;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.jmap.api.upload.UploadUsageRepository;

import reactor.core.publisher.Mono;

public class InMemoryUploadUsageRepository implements UploadUsageRepository {

    private static final QuotaSizeUsage DEFAULT_QUOTA_SIZE_USAGE = QuotaSizeUsage.size(0);

    private final Map<Username, AtomicReference<QuotaSizeUsage>> cache;

    public InMemoryUploadUsageRepository() {
        cache = new ConcurrentHashMap<>();
    }

    @Override
    public Mono<Void> increaseSpace(Username username, QuotaSizeUsage usage) {
        return updateSpace(username, usage.asLong());
    }

    @Override
    public Mono<Void> decreaseSpace(Username username, QuotaSizeUsage usage) {
        return updateSpace(username, Math.negateExact(usage.asLong()));
    }

    private Mono<Void> updateSpace(Username username, long amount) {
        return Mono.fromRunnable(() -> {
            AtomicReference<QuotaSizeUsage> quotaSizeUsageAtomicReference = cache.get(username);
            if (Objects.isNull(quotaSizeUsageAtomicReference)) {
                cache.put(username, new AtomicReference<>(QuotaSizeUsage.size(amount)));
            } else {
                quotaSizeUsageAtomicReference.updateAndGet(quotaSizeUsage -> quotaSizeUsage.add(amount));
            }
        });
    }

    @Override
    public Mono<QuotaSizeUsage> getSpaceUsage(Username username) {
        return Mono.just(cache.getOrDefault(username, new AtomicReference<>(DEFAULT_QUOTA_SIZE_USAGE)))
            .map(quotaSizeUsageAtomicReference -> quotaSizeUsageAtomicReference.get());
    }

    @Override
    public Mono<Void> resetSpace(Username username, QuotaSizeUsage usage) {
        return getSpaceUsage(username).flatMap(quotaSizeUsage -> Mono.from(decreaseSpace(username, quotaSizeUsage)))
            .then(increaseSpace(username, usage));
    }
}
