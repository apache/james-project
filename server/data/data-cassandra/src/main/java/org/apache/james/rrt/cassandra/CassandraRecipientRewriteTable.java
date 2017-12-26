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
package org.apache.james.rrt.cassandra;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.rrt.cassandra.tables.CassandraRecipientRewriteTableTable.DOMAIN;
import static org.apache.james.rrt.cassandra.tables.CassandraRecipientRewriteTableTable.MAPPING;
import static org.apache.james.rrt.cassandra.tables.CassandraRecipientRewriteTableTable.TABLE_NAME;
import static org.apache.james.rrt.cassandra.tables.CassandraRecipientRewriteTableTable.USER;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.github.steveash.guavate.Guavate;

public class CassandraRecipientRewriteTable extends AbstractRecipientRewriteTable {

    private final CassandraAsyncExecutor executor;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement insertStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement retrieveMappingStatement;
    private final PreparedStatement retrieveAllMappingsStatement;

    @Inject
    public CassandraRecipientRewriteTable(Session session, CassandraUtils cassandraUtils) {
        this.executor = new CassandraAsyncExecutor(session);
        this.cassandraUtils = cassandraUtils;
        this.insertStatement = prepareInsertStatement(session);
        this.deleteStatement = prepareDelete(session);
        this.retrieveMappingStatement = prepareRetrieveMappingStatement(session);
        this.retrieveAllMappingsStatement = prepareRetrieveAllMappingStatement(session);
    }

    private PreparedStatement prepareRetrieveAllMappingStatement(Session session) {
        return session.prepare(select(USER, DOMAIN, MAPPING)
            .from(TABLE_NAME));
    }

    private PreparedStatement prepareRetrieveMappingStatement(Session session) {
        return session.prepare(select(MAPPING)
            .from(TABLE_NAME)
            .where(eq(USER, bindMarker(USER)))
            .and(eq(DOMAIN, bindMarker(DOMAIN))));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(USER, bindMarker(USER)))
            .and(eq(DOMAIN, bindMarker(DOMAIN)))
            .and(eq(MAPPING, bindMarker(MAPPING))));
    }

    private PreparedStatement prepareInsertStatement(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(USER, bindMarker(USER))
            .value(DOMAIN, bindMarker(DOMAIN))
            .value(MAPPING, bindMarker(MAPPING)));
    }

    @Override
    protected void addMappingInternal(String user, String domain, Mapping mapping) throws RecipientRewriteTableException {
        executor.executeVoid(insertStatement.bind()
            .setString(USER, getFixedUser(user))
            .setString(DOMAIN, getFixedDomain(domain))
            .setString(MAPPING, mapping.asString()))
            .join();
    }

    @Override
    protected void removeMappingInternal(String user, String domain, Mapping mapping) throws RecipientRewriteTableException {
        executor.executeVoid(deleteStatement.bind()
                .setString(USER, getFixedUser(user))
                .setString(DOMAIN, getFixedDomain(domain))
                .setString(MAPPING, mapping.asString()))
            .join();
    }

    @Override
    protected Mappings getUserDomainMappingsInternal(String user, String domain) throws RecipientRewriteTableException {
        return retrieveMappings(user, domain)
            .orElse(null);
    }

    private Optional<Mappings> retrieveMappings(String user, String domain) {
        List<String> mappings = executor.execute(retrieveMappingStatement.bind()
            .setString(USER, getFixedUser(user))
            .setString(DOMAIN, getFixedDomain(domain)))
            .thenApply(resultSet -> cassandraUtils.convertToStream(resultSet)
                .map(row -> row.getString(MAPPING))
                .collect(Guavate.toImmutableList()))
            .join();

        return MappingsImpl.fromCollection(mappings).toOptional();
    }

    @Override
    protected Map<String, Mappings> getAllMappingsInternal() throws RecipientRewriteTableException {
        Map<String, Mappings> map = executor.execute(retrieveAllMappingsStatement.bind())
            .thenApply(resultSet -> cassandraUtils.convertToStream(resultSet)
                .map(row -> new UserMapping(row.getString(USER), row.getString(DOMAIN), row.getString(MAPPING)))
                .collect(Guavate.toImmutableMap(
                    UserMapping::asKey,
                    UserMapping::toMapping,
                    Mappings::union)))
            .join();
        return map.isEmpty() ? null : map;
    }

    private static class UserMapping {

        private final String user;
        private final String domain;
        private final String mapping;

        public UserMapping(String user, String domain, String mapping) {
            this.user = user;
            this.domain = domain;
            this.mapping = mapping;
        }

        public String getUser() {
            return user;
        }

        public String getDomain() {
            return domain;
        }

        public String getMapping() {
            return mapping;
        }

        public Mappings toMapping() {
            return MappingsImpl.fromRawString(getMapping());
        }

        public String asKey() {
            return getUser() + "@" + getDomain();
        }
    }

    @Override
    protected String mapAddressInternal(String user, String domain) throws RecipientRewriteTableException {
        Mappings mappings = retrieveMappings(user, domain)
            .orElseGet(() -> retrieveMappings(WILDCARD, domain)
                .orElseGet(() -> retrieveMappings(user, WILDCARD)
                    .orElseGet(() -> MappingsImpl.empty())));

        return !mappings.isEmpty() ? mappings.serialize() : null;
    }

}
