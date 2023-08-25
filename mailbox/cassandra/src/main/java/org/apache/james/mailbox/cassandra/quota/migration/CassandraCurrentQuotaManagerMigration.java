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

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.user.api.UsersRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraCurrentQuotaManagerMigration implements Migration {
    private final UsersRepository usersRepository;
    private final UserQuotaRootResolver userQuotaRootResolver;
    private final CurrentQuotaManager oldCurrentQuotaManager;
    private final CurrentQuotaManager newCurrentQuotaManager;

    @Inject
    public CassandraCurrentQuotaManagerMigration(UsersRepository usersRepository,
                                                 UserQuotaRootResolver userQuotaRootResolver,
                                                 @Named("old") CurrentQuotaManager oldCurrentQuotaManager,
                                                 CurrentQuotaManager newCurrentQuotaManager) {
        this.usersRepository = usersRepository;
        this.userQuotaRootResolver = userQuotaRootResolver;
        this.oldCurrentQuotaManager = oldCurrentQuotaManager;
        this.newCurrentQuotaManager = newCurrentQuotaManager;
    }

    @Override
    public void apply() throws InterruptedException {
        Flux.from(usersRepository.listReactive())
            .flatMap(this::migrateCurrentValue)
            .then()
            .block();
    }

    private Mono<Void> migrateCurrentValue(Username username) {
        QuotaRoot quotaRoot = userQuotaRootResolver.forUser(username);

        return Mono.from(oldCurrentQuotaManager.getCurrentQuotas(quotaRoot))
            .flatMap(currentQuotas -> Mono.from(newCurrentQuotaManager.setCurrentQuotas(QuotaOperation.from(quotaRoot, currentQuotas))));
    }
}
