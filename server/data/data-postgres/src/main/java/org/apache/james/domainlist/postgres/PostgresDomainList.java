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

package org.apache.james.domainlist.postgres;

import static org.apache.james.backends.postgres.utils.DefaultPostgresExecutor.DEFAULT_INJECT;
import static org.apache.james.domainlist.postgres.PostgresDomainModule.PostgresDomainTable.DOMAIN;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.backends.postgres.utils.DefaultPostgresExecutor;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AbstractDomainList;
import org.jooq.exception.DataAccessException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresDomainList extends AbstractDomainList {
    private final DefaultPostgresExecutor postgresExecutor;

    @Inject
    public PostgresDomainList(DNSService dnsService, @Named(DEFAULT_INJECT) DefaultPostgresExecutor postgresExecutor) {
        super(dnsService);
        this.postgresExecutor = postgresExecutor;
    }

    @Override
    public void addDomain(Domain domain) throws DomainListException {
        try {
            postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(PostgresDomainModule.PostgresDomainTable.TABLE_NAME, DOMAIN)
                    .values(domain.asString())))
                .block();
        } catch (DataAccessException exception) {
            throw new DomainListException(domain.name() + " already exists.");
        }
    }

    @Override
    protected List<Domain> getDomainListInternal() {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(PostgresDomainModule.PostgresDomainTable.TABLE_NAME)))
            .map(record -> Domain.of(record.get(DOMAIN)))
            .collectList()
            .block();
    }

    @Override
    protected boolean containsDomainInternal(Domain domain) {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.selectFrom(PostgresDomainModule.PostgresDomainTable.TABLE_NAME)
            .where(DOMAIN.eq(domain.asString()))))
            .blockOptional()
            .isPresent();
    }

    @Override
    protected void doRemoveDomain(Domain domain) throws DomainListException {
        boolean executed = postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.deleteFrom(PostgresDomainModule.PostgresDomainTable.TABLE_NAME)
                .where(DOMAIN.eq(domain.asString()))
                .returning(DOMAIN)))
            .blockOptional()
            .isPresent();

        if (!executed) {
            throw new DomainListException(domain.name() + " was not found");
        }
    }
}
