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

package org.apache.james.mailbox.store.quota;

import static org.apache.james.mailbox.store.mail.AbstractMessageMapper.UNLIMITED;

import jakarta.inject.Inject;

import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CurrentQuotaCalculator {
    private static final int NO_CONCURRENCY = 1;

    private final MailboxSessionMapperFactory factory;
    private final QuotaRootResolver quotaRootResolver;

    @Inject
    public CurrentQuotaCalculator(MailboxSessionMapperFactory factory,
                                  QuotaRootResolver quotaRootResolver) {
        this.factory = factory;
        this.quotaRootResolver = quotaRootResolver;
    }

    public Mono<CurrentQuotas> recalculateCurrentQuotas(QuotaRoot quotaRoot, MailboxSession session) {
        MessageMapper mapper = factory.getMessageMapper(session);

        return Flux.from(quotaRootResolver.retrieveAssociatedMailboxes(quotaRoot, session))
            .flatMap(mailbox -> mapper.findInMailboxReactive(mailbox, MessageRange.all(), MessageMapper.FetchType.METADATA, UNLIMITED), NO_CONCURRENCY)
            .map(message -> new CurrentQuotas(QuotaCountUsage.count(1), QuotaSizeUsage.size(message.getFullContentOctets())))
            .reduce(CurrentQuotas.emptyQuotas(), CurrentQuotas::increase);
    }
}