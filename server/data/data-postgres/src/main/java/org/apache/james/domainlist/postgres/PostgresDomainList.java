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

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.postgres.utils.JamesPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AbstractDomainList;
import org.jooq.exception.DataAccessException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresDomainList extends AbstractDomainList {
    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresDomainList(DNSService dnsService, JamesPostgresConnectionFactory postgresConnectionFactory) {
        super(dnsService);
        this.postgresExecutor = new PostgresExecutor(postgresConnectionFactory.getConnection(Optional.empty()));;
    }

    @Override
    public void addDomain(Domain domain) throws DomainListException {
        postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(PostgresDomainModule.PostgresDomainTable.TABLE_NAME, PostgresDomainModule.PostgresDomainTable.DOMAIN)
                .values(domain.asString())))
            .onErrorMap(DataAccessException.class, e -> new DomainListException(domain.name() + " already exists."))
            .block();

    }

    @Override
    protected List<Domain> getDomainListInternal() throws DomainListException {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(PostgresDomainModule.PostgresDomainTable.TABLE_NAME)))
            .map(record -> Domain.of(record.get(PostgresDomainModule.PostgresDomainTable.DOMAIN)))
            .collectList()
            .block();
    }

    @Override
    protected boolean containsDomainInternal(Domain domain) throws DomainListException {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.selectFrom(PostgresDomainModule.PostgresDomainTable.TABLE_NAME)
            .where(PostgresDomainModule.PostgresDomainTable.DOMAIN.eq(domain.asString()))))
            .blockOptional()
            .isPresent();
    }

    @Override
    protected void doRemoveDomain(Domain domain) throws DomainListException {
        postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(PostgresDomainModule.PostgresDomainTable.TABLE_NAME)
            .where(PostgresDomainModule.PostgresDomainTable.DOMAIN.eq(domain.asString()))))
            .onErrorMap(DataAccessException.class, e -> new DomainListException(domain.name() + " was not found"))
            .block();
    }
}
