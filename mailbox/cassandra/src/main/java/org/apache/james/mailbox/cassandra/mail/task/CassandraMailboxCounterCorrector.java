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

package org.apache.james.mailbox.cassandra.mail.task;

import jakarta.inject.Inject;

import org.apache.james.mailbox.MailboxCounterCorrector;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;

import reactor.core.publisher.Mono;

public class CassandraMailboxCounterCorrector implements MailboxCounterCorrector {
    private final RecomputeMailboxCountersService service;

    @Inject
    public CassandraMailboxCounterCorrector(RecomputeMailboxCountersService service) {
        this.service = service;
    }

    @Override
    public Mono<Void> fixCountersFor(MessageManager mailbox) {
        try {
            return service.recomputeMailboxCounter(new RecomputeMailboxCountersService.Context(),
                    mailbox.getMailboxEntity(),
                    RecomputeMailboxCountersService.Options.trustMessageProjection())
                .then();
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }
}
