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

import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AbstractDomainList;
import org.apache.james.tables.CassandraDomainsTable;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;

public class CassandraDomainList extends AbstractDomainList {

    private Session session;

    @Inject
    @Resource
    public void setSession(Session session) {
        this.session = session;
    }

    @Override
    protected List<String> getDomainListInternal() throws DomainListException {
        return CassandraUtils.convertToStream(session.execute(select(CassandraDomainsTable.DOMAIN).from(CassandraDomainsTable.TABLE_NAME)))
            .map(row -> row.getString(CassandraDomainsTable.DOMAIN))
            .collect(Collectors.toList());
    }

    @Override
    public boolean containsDomain(String domain) throws DomainListException {
        return session.execute(select(CassandraDomainsTable.DOMAIN)
            .from(CassandraDomainsTable.TABLE_NAME)
            .where(eq(CassandraDomainsTable.DOMAIN, domain.toLowerCase())))
            .one() != null;
    }

    @Override
    public void addDomain(String domain) throws DomainListException {
        boolean executed = session.execute(insertInto(CassandraDomainsTable.TABLE_NAME)
            .ifNotExists()
            .value(CassandraDomainsTable.DOMAIN, domain.toLowerCase()))
            .one()
            .getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED);
        if (!executed) {
            throw new DomainListException(domain.toLowerCase() + " already exists.");
        }
    }

    @Override
    public void removeDomain(String domain) throws DomainListException {
        ResultSet resultSet = session.execute(delete()
            .from(CassandraDomainsTable.TABLE_NAME)
            .ifExists()
            .where(eq(CassandraDomainsTable.DOMAIN, domain.toLowerCase())));
        if (!resultSet.one().getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED)) {
            throw new DomainListException(domain + " was not found");
        }
    }

}