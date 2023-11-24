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

package org.apache.james.domainlist.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.domainlist.cassandra.tables.CassandraDomainsTable.DOMAIN;
import static org.apache.james.domainlist.cassandra.tables.CassandraDomainsTable.TABLE_NAME;

import java.util.List;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AbstractDomainList;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;

public class CassandraDomainList extends AbstractDomainList {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement readAllStatement;
    private final PreparedStatement readStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement removeStatement;

    @Inject
    public CassandraDomainList(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.readAllStatement = session.prepare(selectFrom(TABLE_NAME)
            .column(DOMAIN)
            .build());

        this.readStatement = session.prepare(selectFrom(TABLE_NAME)
            .column(DOMAIN)
            .whereColumn(DOMAIN).isEqualTo(bindMarker(DOMAIN))
            .build());

        this.insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(DOMAIN, bindMarker(DOMAIN))
            .ifNotExists()
            .build());

        this.removeStatement = session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(DOMAIN).isEqualTo(bindMarker(DOMAIN))
            .ifExists()
            .build());
    }

    @Override
    protected List<Domain> getDomainListInternal() throws DomainListException {
        return executor.executeRows(readAllStatement.bind())
            .map(row -> Domain.of(row.get(0, TypeCodecs.TEXT)))
            .collectList()
            .block();
    }

    @Override
    protected boolean containsDomainInternal(Domain domain) throws DomainListException {
        return executor.executeSingleRowOptional(readStatement.bind()
                .set(DOMAIN, domain.asString(), TypeCodecs.TEXT))
            .block()
            .isPresent();
    }

    @Override
    public void addDomain(Domain domain) throws DomainListException {
        boolean executed = executor.executeReturnApplied(insertStatement.bind()
                .set(DOMAIN, domain.asString(), TypeCodecs.TEXT))
            .block();
        if (!executed) {
            throw new DomainListException(domain.name() + " already exists.");
        }
    }

    @Override
    public void doRemoveDomain(Domain domain) throws DomainListException {
        boolean executed = executor.executeReturnApplied(removeStatement.bind()
                .set(DOMAIN, domain.asString(), TypeCodecs.TEXT))
            .block();
        if (!executed) {
            throw new DomainListException(domain.name() + " was not found");
        }
    }

}