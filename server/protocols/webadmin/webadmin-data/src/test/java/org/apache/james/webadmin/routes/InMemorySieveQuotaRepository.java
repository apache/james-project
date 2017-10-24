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

package org.apache.james.webadmin.routes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.sieverepository.api.SieveQuotaRepository;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;

public class InMemorySieveQuotaRepository implements SieveQuotaRepository {

    private Optional<Long> globalQuota = Optional.empty();

    private Map<String, Long> userQuota = new ConcurrentHashMap<>();

    @Override
    public boolean hasQuota() {
        return globalQuota.isPresent();
    }

    @Override
    public long getQuota() throws QuotaNotFoundException {
        return globalQuota.orElseThrow(QuotaNotFoundException::new);
    }

    @Override
    public void setQuota(long quota) {
        this.globalQuota = Optional.of(quota);
    }

    @Override
    public void removeQuota() throws QuotaNotFoundException {
        if (!globalQuota.isPresent()) {
            throw new QuotaNotFoundException();
        }
        globalQuota = Optional.empty();
    }

    @Override
    public boolean hasQuota(String user) {
        return userQuota.containsKey(user);
    }

    @Override
    public long getQuota(String user) throws QuotaNotFoundException {
        return Optional.ofNullable(userQuota.get(user))
            .orElseThrow(QuotaNotFoundException::new);
    }

    @Override
    public void setQuota(String user, long quota) {
        userQuota.put(user, quota);
    }

    @Override
    public void removeQuota(String user) throws QuotaNotFoundException {
        Optional<Long> quotaValue = Optional.ofNullable(userQuota.get(user));
        if (!quotaValue.isPresent()) {
            throw new QuotaNotFoundException();
        }
        userQuota.remove(user);
    }
}
