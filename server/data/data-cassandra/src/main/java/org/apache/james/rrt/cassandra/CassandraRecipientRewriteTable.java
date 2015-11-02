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
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;

import com.datastax.driver.core.Session;
import com.google.common.base.Optional;

public class CassandraRecipientRewriteTable extends AbstractRecipientRewriteTable {

    private Session session;

    @Inject
    @Resource
    public void setSession(@Named("cassandra-session") Session session) {
        this.session = session;
    }

    @Override
    protected void addMappingInternal(String user, String domain, String mapping) throws RecipientRewriteTableException {
        session.execute(insertInto(TABLE_NAME)
            .value(USER, getFixedUser(user))
            .value(DOMAIN, getFixedDomain(domain))
            .value(MAPPING, mapping));
    }

    @Override
    protected void removeMappingInternal(String user, String domain, String mapping) throws RecipientRewriteTableException {
        session.execute(delete()
            .from(TABLE_NAME)
            .where(eq(USER, getFixedUser(user)))
            .and(eq(DOMAIN, getFixedDomain(domain)))
            .and(eq(MAPPING, mapping)));
    }

    @Override
    protected Mappings getUserDomainMappingsInternal(String user, String domain) throws RecipientRewriteTableException {
        return retrieveMappings(user, domain)
            .orNull();
    }

    private Optional<Mappings> retrieveMappings(String user, String domain) {
        List<String> mappings = session.execute(select(MAPPING)
                .from(TABLE_NAME)
                .where(eq(USER, getFixedUser(user)))
                .and(eq(DOMAIN, getFixedDomain(domain))))
            .all()
            .stream()
            .map(row -> row.getString(MAPPING))
            .collect(Collectors.toList());

        return MappingsImpl.fromCollection(mappings).toOptional();
    }

    @Override
    protected Map<String, Mappings> getAllMappingsInternal() throws RecipientRewriteTableException {
        Map<String, Mappings> map = session.execute(select(USER, DOMAIN, MAPPING)
            .from(TABLE_NAME))
            .all()
            .stream()
            .map(row -> new UserMapping(row.getString(USER), row.getString(DOMAIN), row.getString(MAPPING)))
            .collect(Collectors.toMap(UserMapping::asKey, 
                    userMapping -> MappingsImpl.fromRawString(userMapping.getMapping()),
                    (first, second) -> first.union(second)));
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

        public String asKey() {
            return getUser() + "@" + getDomain();
        }
    }

    @Override
    protected String mapAddressInternal(String user, String domain) throws RecipientRewriteTableException {
        Mappings mappings = retrieveMappings(user, domain)
            .or(() -> retrieveMappings(WILDCARD, domain)
                    .or(() -> retrieveMappings(user, WILDCARD)
                            .or(MappingsImpl.empty())));
        return !mappings.isEmpty() ? mappings.serialize() : null;
    }

}
