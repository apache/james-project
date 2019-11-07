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

package org.apache.james.sieverepository.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.sieverepository.api.SieveQuotaRepository;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;

public class InMemorySieveQuotaRepository implements SieveQuotaRepository {

    private Optional<QuotaSizeLimit> globalQuota = Optional.empty();

    private Map<Username, QuotaSizeLimit> userQuota = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean hasDefaultQuota() {
        return globalQuota.isPresent();
    }

    @Override
    public synchronized QuotaSizeLimit getDefaultQuota() throws QuotaNotFoundException {
        return globalQuota.orElseThrow(QuotaNotFoundException::new);
    }

    @Override
    public synchronized void setDefaultQuota(QuotaSizeLimit quota) {
        this.globalQuota = Optional.of(quota);
    }

    @Override
    public synchronized void removeQuota() throws QuotaNotFoundException {
        if (!globalQuota.isPresent()) {
            throw new QuotaNotFoundException();
        }
        globalQuota = Optional.empty();
    }

    @Override
    public synchronized boolean hasQuota(Username username) {
        return userQuota.containsKey(username);
    }

    @Override
    public QuotaSizeLimit getQuota(Username username) throws QuotaNotFoundException {
        return Optional.ofNullable(userQuota.get(username))
            .orElseThrow(QuotaNotFoundException::new);
    }

    @Override
    public synchronized void setQuota(Username username, QuotaSizeLimit quota) {
        userQuota.put(username, quota);
    }

    @Override
    public synchronized void removeQuota(Username username) throws QuotaNotFoundException {
        Optional<QuotaSizeLimit> quotaValue = Optional.ofNullable(userQuota.get(username));
        if (!quotaValue.isPresent()) {
            throw new QuotaNotFoundException();
        }
        userQuota.remove(username);
    }
}
