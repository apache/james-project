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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.domainlist.cassandra.tables.CassandraDomainsTable.DOMAIN;
import static org.apache.james.domainlist.cassandra.tables.CassandraDomainsTable.TABLE_NAME;

import java.util.List;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AbstractDomainList;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

public class CassandraDomainList extends AbstractDomainList {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement readAllStatement;
    private final PreparedStatement readStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement removeStatement;

    @Inject
    public CassandraDomainList(DNSService dnsService, Session session) {
        super(dnsService);
        this.executor = new CassandraAsyncExecutor(session);
        this.readAllStatement = prepareReadAllStatement(session);
        this.readStatement = prepareReadStatement(session);
        this.insertStatement = prepareInsertStatement(session);
        this.removeStatement = prepareRemoveStatement(session);
    }

    private PreparedStatement prepareRemoveStatement(Session session) {
        return session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(DOMAIN, bindMarker(DOMAIN))));
    }

    private PreparedStatement prepareInsertStatement(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(DOMAIN, bindMarker(DOMAIN)));
    }

    private PreparedStatement prepareReadStatement(Session session) {
        return session.prepare(select(DOMAIN)
            .from(TABLE_NAME)
            .where(eq(DOMAIN, bindMarker(DOMAIN))));
    }

    private PreparedStatement prepareReadAllStatement(Session session) {
        return session.prepare(select(DOMAIN)
            .from(TABLE_NAME));
    }

    @Override
    protected List<Domain> getDomainListInternal() throws DomainListException {
        return executor.executeRows(readAllStatement.bind())
            .map(row -> Domain.of(row.getString(DOMAIN)))
            .collectList()
            .block();
    }

    @Override
    protected boolean containsDomainInternal(Domain domain) throws DomainListException {
        return executor.executeSingleRowOptional(readStatement.bind()
                .setString(DOMAIN, domain.asString()))
            .block()
            .isPresent();
    }

    @Override
    public void addDomain(Domain domain) throws DomainListException {
        executor.executeVoid(insertStatement.bind()
            .setString(DOMAIN, domain.asString()))
            .block();
    }

    @Override
    public void doRemoveDomain(Domain domain) throws DomainListException {
        executor.executeVoid(removeStatement.bind()
            .setString(DOMAIN, domain.asString()))
            .block();
    }

}