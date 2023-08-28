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

package org.apache.james.jmap.api.upload;

import java.io.InputStream;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.jmap.api.model.Upload;
import org.apache.james.jmap.api.model.UploadId;
import org.apache.james.jmap.api.model.UploadMetaData;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class UploadServiceDefaultImpl implements UploadService {
    private final UploadRepository uploadRepository;
    private final UploadUsageRepository uploadUsageRepository;
    private final JmapUploadQuotaConfiguration jmapUploadQuotaConfiguration;

    @Inject
    @Singleton
    public UploadServiceDefaultImpl(UploadRepository uploadRepository,
                                    UploadUsageRepository uploadUsageRepository,
                                    JmapUploadQuotaConfiguration jmapUploadQuotaConfiguration) {
        this.uploadRepository = uploadRepository;
        this.uploadUsageRepository = uploadUsageRepository;
        this.jmapUploadQuotaConfiguration = jmapUploadQuotaConfiguration;
    }

    @Override
    public Publisher<UploadMetaData> upload(InputStream data, ContentType contentType, Username user) {
        return Mono.from(uploadRepository.upload(data, contentType, user))
            .flatMap(upload -> Mono.from(uploadUsageRepository.increaseSpace(user, QuotaSizeUsage.size(upload.sizeAsLong())))
                .thenReturn(upload))
            .doOnSuccess(uploaded -> cleanupUploadIfNeeded(user)
                .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                .subscribe());
    }

    @Override
    public Publisher<Upload> retrieve(UploadId id, Username user) {
        return uploadRepository.retrieve(id, user);
    }

    private Mono<Void> cleanupUploadIfNeeded(Username username) {
        return Mono.from(uploadUsageRepository.getSpaceUsage(username))
            .map(QuotaSizeUsage::asLong)
            .filter(quotaExceededPredicate())
            .switchIfEmpty(Mono.empty())
            .flatMap(quotaExceeded -> cleanupUpload(username, quotaExceeded));
    }

    private Predicate<Long> quotaExceededPredicate() {
        return currentUploadUsage -> currentUploadUsage > jmapUploadQuotaConfiguration.getUploadQuotaLimitInBytes();
    }

    private Mono<Void> cleanupUpload(Username username, long currentUploadUsage) {
        long minimumSpaceToClean = currentUploadUsage - jmapUploadQuotaConfiguration.getUploadQuotaLimitInBytes() / 2;
        AtomicLong cleanedSpace = new AtomicLong(0L);

        return Flux.from(uploadRepository.listUploads(username))
            .sort(Comparator.comparing(UploadMetaData::uploadDate))
            .concatMap(deleteUpload(username, minimumSpaceToClean, cleanedSpace))
            .map(UploadMetaData::sizeAsLong)
            .reduce(Long::sum)
            .filter(totalSpaceUsed -> totalSpaceUsed != currentUploadUsage)
            .flatMap(totalSpaceUsed -> Mono.from(uploadUsageRepository.resetSpace(username, QuotaSizeUsage.size(totalSpaceUsed))))
            .then();
    }

    private Function<UploadMetaData, Publisher<? extends UploadMetaData>> deleteUpload(Username username, long minimumSpaceToClean, AtomicLong cleanedSpace) {
        return upload -> {
            if (cleanedSpace.get() < minimumSpaceToClean) {
                return Mono.from(uploadRepository.delete(upload.uploadId(), username))
                    .then(Mono.from(uploadUsageRepository.decreaseSpace(username, QuotaSizeUsage.size(upload.sizeAsLong()))))
                    .then(Mono.fromCallable(() -> cleanedSpace.addAndGet(upload.sizeAsLong())))
                    .then(Mono.empty());
            } else {
                return Mono.fromCallable(() -> upload);
            }
        };
    }
}
