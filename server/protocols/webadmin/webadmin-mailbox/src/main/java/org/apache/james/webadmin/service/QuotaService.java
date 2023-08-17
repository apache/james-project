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

package org.apache.james.webadmin.service;

import java.util.List;

import javax.inject.Inject;

import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.QuotaScope;
import org.apache.james.mailbox.cassandra.quota.CassandraQuotaLimitDao;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class QuotaService {

    private final CassandraQuotaLimitDao quotaLimitDao;

    @Inject
    public QuotaService(CassandraQuotaLimitDao quotaLimitDao) {
        this.quotaLimitDao = quotaLimitDao;
    }

    public Mono<QuotaLimit> getQuotaLimit(CassandraQuotaLimitDao.QuotaLimitKey quotaKey) {
        return quotaLimitDao.getQuotaLimit(quotaKey);
    }

    public List<QuotaLimit> getQuotaLimits(QuotaComponent quotaComponent, QuotaScope quotaScope, String identifier) {
        return quotaLimitDao.getQuotaLimits(quotaComponent, quotaScope, identifier).collectList().block();
    }

    public void saveQuotaLimits(List<QuotaLimit> quotaLimits) {
        Flux.fromIterable(quotaLimits).flatMap(quotaLimit -> quotaLimitDao.setQuotaLimit(quotaLimit)).collectList().block();
    }
}
