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

package org.apache.james.quota.search.scanning;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRatio;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.quota.search.QuotaClause;

import com.google.common.collect.ImmutableMap;

public class ClauseConverter {

    private final UserQuotaRootResolver quotaRootResolver;
    private final QuotaManager quotaManager;
    private final Map<Class<? extends QuotaClause>, Function<QuotaClause, Predicate<Username>>> toPredicates;

    @Inject
    public ClauseConverter(UserQuotaRootResolver quotaRootResolver, QuotaManager quotaManager) {
        this.quotaRootResolver = quotaRootResolver;
        this.quotaManager = quotaManager;
        this.toPredicates = ImmutableMap.of(
            QuotaClause.And.class, this::andToPredicate,
            QuotaClause.LessThan.class, this::lessThanToPredicate,
            QuotaClause.MoreThan.class, this::moreThanToPredicate,
            QuotaClause.HasDomain.class, this::hasDomainToPredicate);
    }

    public Predicate<Username> andToPredicate(QuotaClause.And and) {
        return and.getClauses()
            .stream()
            .map(this::toPredicate)
            .reduce((p1, p2) -> (user -> p1.test(user) && p2.test(user)))
            .orElse(user -> true);
    }

    private Predicate<Username> toPredicate(QuotaClause clause) {
        return toPredicates.get(clause.getClass())
            .apply(clause);
    }

    private Predicate<Username> moreThanToPredicate(QuotaClause clause) {
        QuotaClause.MoreThan moreThan = (QuotaClause.MoreThan) clause;
        return user -> retrieveUserRatio(user) >= moreThan.getQuotaBoundary().getRatio();
    }

    private Predicate<Username> lessThanToPredicate(QuotaClause clause) {
        QuotaClause.LessThan lessThan = (QuotaClause.LessThan) clause;
        return user -> retrieveUserRatio(user) <= lessThan.getQuotaBoundary().getRatio();
    }

    private Predicate<Username> hasDomainToPredicate(QuotaClause clause) {
        QuotaClause.HasDomain hasDomain = (QuotaClause.HasDomain) clause;
        return user -> user.getDomainPart()
            .map(hasDomain.getDomain()::equals)
            .orElse(false);
    }

    private Predicate<Username> andToPredicate(QuotaClause clause) {
        QuotaClause.And and = (QuotaClause.And) clause;
        return andToPredicate(and);
    }

    private double retrieveUserRatio(Username username) {
        try {
            QuotaRoot quotaRoot = quotaRootResolver.forMailAddress(username);
            QuotaManager.Quotas quotas = quotaManager.getQuotas(quotaRoot);
            Quota<QuotaSizeLimit, QuotaSizeUsage> storageQuota = quotas.getStorageQuota();
            Quota<QuotaCountLimit, QuotaCountUsage> messageQuota = quotas.getMessageQuota();

            return QuotaRatio.from(storageQuota, messageQuota).max();
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }
}
