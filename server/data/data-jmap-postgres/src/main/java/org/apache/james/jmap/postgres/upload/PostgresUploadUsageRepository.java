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

package org.apache.james.jmap.postgres.upload;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.james.backends.postgres.quota.PostgresQuotaCurrentValueDAO;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.jmap.api.upload.UploadUsageRepository;

import reactor.core.publisher.Mono;

public class PostgresUploadUsageRepository implements UploadUsageRepository {
    private static final QuotaSizeUsage DEFAULT_QUOTA_SIZE_USAGE = QuotaSizeUsage.size(0);

    private final PostgresQuotaCurrentValueDAO quotaCurrentValueDAO;

    @Inject
    @Singleton
    public PostgresUploadUsageRepository(PostgresQuotaCurrentValueDAO quotaCurrentValueDAO) {
        this.quotaCurrentValueDAO = quotaCurrentValueDAO;
    }

    @Override
    public Mono<Void> increaseSpace(Username username, QuotaSizeUsage usage) {
        return quotaCurrentValueDAO.increase(QuotaCurrentValue.Key.of(QuotaComponent.JMAP_UPLOADS, username.asString(), QuotaType.SIZE),
            usage.asLong());
    }

    @Override
    public Mono<Void> decreaseSpace(Username username, QuotaSizeUsage usage) {
        return quotaCurrentValueDAO.decrease(QuotaCurrentValue.Key.of(QuotaComponent.JMAP_UPLOADS, username.asString(), QuotaType.SIZE),
            usage.asLong());
    }

    @Override
    public Mono<QuotaSizeUsage> getSpaceUsage(Username username) {
        return quotaCurrentValueDAO.getQuotaCurrentValue(QuotaCurrentValue.Key.of(QuotaComponent.JMAP_UPLOADS, username.asString(), QuotaType.SIZE))
            .map(quotaCurrentValue -> QuotaSizeUsage.size(quotaCurrentValue.getCurrentValue())).defaultIfEmpty(DEFAULT_QUOTA_SIZE_USAGE);
    }

    @Override
    public Mono<Void> resetSpace(Username username, QuotaSizeUsage usage) {
        return getSpaceUsage(username)
            .switchIfEmpty(Mono.just(QuotaSizeUsage.ZERO))
            .flatMap(quotaSizeUsage -> decreaseSpace(username, QuotaSizeUsage.size(quotaSizeUsage.asLong() - usage.asLong())));
    }
}
