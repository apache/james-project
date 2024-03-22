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

package org.apache.james.mailbox.cassandra.quota.migration;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.user.api.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraPerUserMaxQuotaManagerMigration implements Migration {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraPerUserMaxQuotaManagerMigration.class);
    private final UsersRepository usersRepository;
    private final DomainList domainDao;
    private final MaxQuotaManager oldMaxQuotaManager;
    private final MaxQuotaManager newMaxQuotaManager;
    private UserQuotaRootResolver userQuotaRootResolver;

    @Inject
    public CassandraPerUserMaxQuotaManagerMigration(UsersRepository usersRepository, DomainList domainDao,
                                                    @Named("old") MaxQuotaManager oldMaxQuotaManager, @Named("new")  MaxQuotaManager newMaxQuotaManager,
                                                    UserQuotaRootResolver userQuotaRootResolver) {
        this.usersRepository = usersRepository;
        this.domainDao = domainDao;
        this.oldMaxQuotaManager = oldMaxQuotaManager;
        this.newMaxQuotaManager = newMaxQuotaManager;
        this.userQuotaRootResolver = userQuotaRootResolver;
    }

    @Override
    public void apply() throws InterruptedException {
        Mono.from(oldMaxQuotaManager.getGlobalMaxMessageReactive()).flatMap(quotaCountLimit -> Mono.from(newMaxQuotaManager.setGlobalMaxMessageReactive(quotaCountLimit))).block();
        Mono.from(oldMaxQuotaManager.getGlobalMaxStorageReactive()).flatMap(quotaSizeLimit -> Mono.from(newMaxQuotaManager.setGlobalMaxStorageReactive(quotaSizeLimit))).block();

        Flux.fromIterable(getDomains())
            .flatMap(domain -> migrateDomainQuotaLimit(domain))
            .then()
            .block();

        Flux.from(usersRepository.listReactive())
            .map(userQuotaRootResolver::forUser)
            .flatMap(quotaRoot -> migrateUserQuotaLimit(quotaRoot))
            .then()
            .block();
    }

    private Mono<Void> migrateDomainQuotaLimit(Domain domain) {
        return Mono.from(oldMaxQuotaManager.getDomainMaxMessageReactive(domain))
            .flatMap(quotaCountLimit -> Mono.from(newMaxQuotaManager.setDomainMaxMessageReactive(domain, quotaCountLimit)))
            .then(Mono.from(oldMaxQuotaManager.getDomainMaxStorageReactive(domain))
                .flatMap(quotaSizeLimit -> Mono.from(newMaxQuotaManager.setDomainMaxStorageReactive(domain, quotaSizeLimit))));
    }

    private Mono<Void> migrateUserQuotaLimit(QuotaRoot quotaRoot) {
        return Mono.from(oldMaxQuotaManager.listMaxMessagesDetailsReactive(quotaRoot))
            .filter(map -> map.containsKey(Quota.Scope.User))
            .map(map -> map.get(Quota.Scope.User))
            .flatMap(quotaCountLimit -> Mono.from(newMaxQuotaManager.setMaxMessageReactive(quotaRoot, quotaCountLimit)))
            .then(Mono.from(oldMaxQuotaManager.listMaxStorageDetailsReactive(quotaRoot))
                .filter(map -> map.containsKey(Quota.Scope.User))
                .map(map -> map.get(Quota.Scope.User))
                .flatMap(quotaSizeLimit -> Mono.from(newMaxQuotaManager.setMaxStorageReactive(quotaRoot, quotaSizeLimit))));
    }

    private List<Domain> getDomains() {
        try {
            return domainDao.getDomains();
        } catch (DomainListException ex) {
            throw new RuntimeException(ex);
        }
    }
}
