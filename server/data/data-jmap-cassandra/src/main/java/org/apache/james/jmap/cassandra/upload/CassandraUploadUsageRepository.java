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

package org.apache.james.jmap.cassandra.upload;

import java.util.Objects;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.jmap.api.upload.UploadUsageRepository;
import org.apache.james.mailbox.cassandra.quota.CassandraQuotaCurrentValueDao;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class CassandraUploadUsageRepository implements UploadUsageRepository {

    private CassandraQuotaCurrentValueDao cassandraQuotaCurrentValueDao;

    @Inject
    public CassandraUploadUsageRepository(CassandraQuotaCurrentValueDao cassandraQuotaCurrentValueDao) {
        this.cassandraQuotaCurrentValueDao = cassandraQuotaCurrentValueDao;
    }

    @Override
    public Publisher<Void> increaseSpace(Username username, QuotaSizeUsage usage) {
        return cassandraQuotaCurrentValueDao.increase(CassandraQuotaCurrentValueDao.QuotaKey.of(QuotaComponent.JMAP_UPLOADS, username.asString(), QuotaType.SIZE),
            usage.asLong());
    }

    @Override
    public Publisher<Void> decreaseSpace(Username username, QuotaSizeUsage usage) {
        return cassandraQuotaCurrentValueDao.decrease(CassandraQuotaCurrentValueDao.QuotaKey.of(QuotaComponent.JMAP_UPLOADS, username.asString(), QuotaType.SIZE),
            usage.asLong());
    }

    @Override
    public Publisher<QuotaSizeUsage> getSpaceUsage(Username username) {
        return cassandraQuotaCurrentValueDao.getQuotaCurrentValue(CassandraQuotaCurrentValueDao.QuotaKey.of(QuotaComponent.JMAP_UPLOADS, username.asString(), QuotaType.SIZE))
            .map(quotaCurrentValue -> QuotaSizeUsage.size(quotaCurrentValue.getCurrentValue())).defaultIfEmpty(QuotaSizeUsage.size(0));
    }

    @Override
    public Publisher<Void> resetSpace(Username username, QuotaSizeUsage usage) {
        return Mono.from(getSpaceUsage(username)).flatMap(quotaSizeUsage -> Mono.from(decreaseSpace(username, quotaSizeUsage)))
            .then(Mono.from(increaseSpace(username, usage)));
    }
}
