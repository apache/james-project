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

package org.apache.james.rrt.memory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;

public class MemoryRecipientRewriteTable extends AbstractRecipientRewriteTable {

    private static class InMemoryMappingEntry {
        private final String user;
        private final String domain;
        private final String mapping;

        public InMemoryMappingEntry(String user, String domain, String mapping) {
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

        @Override
        public boolean equals(Object o) {
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }

            InMemoryMappingEntry that = (InMemoryMappingEntry) o;

            return Objects.equal(this.user, that.user)
                && Objects.equal(this.domain, that.domain)
                && Objects.equal(this.mapping, that.mapping);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(user, domain, mapping);
        }
    }

    private final List<InMemoryMappingEntry> mappingEntries;

    public MemoryRecipientRewriteTable() {
        mappingEntries = new ArrayList<>();
    }

    @Override
    protected void addMappingInternal(String user, String domain, String mapping) throws RecipientRewriteTableException {
        mappingEntries.add(new InMemoryMappingEntry(getFixedUser(user), getFixedDomain(domain), mapping));
    }

    @Override
    protected void removeMappingInternal(String user, String domain, String mapping) throws RecipientRewriteTableException {
        mappingEntries.remove(new InMemoryMappingEntry(getFixedUser(user), getFixedDomain(domain), mapping));
    }

    @Override
    protected Mappings getUserDomainMappingsInternal(String user, String domain) throws RecipientRewriteTableException {
        return retrieveMappings(user, domain)
            .orNull();
    }

    @Override
    protected String mapAddressInternal(String user, String domain) throws RecipientRewriteTableException {
        Mappings mappings = retrieveMappings(user, domain)
            .or(retrieveMappings(WILDCARD, domain)
                .or(retrieveMappings(user, WILDCARD)
                    .or(MappingsImpl.empty())));

        return !mappings.isEmpty() ? mappings.serialize() : null;
    }

    @Override
    protected Map<String, Mappings> getAllMappingsInternal() throws RecipientRewriteTableException {
        if (mappingEntries.isEmpty()) {
            return null;
        }
        Map<String, Collection<Mappings>> userMappingsMap = Multimaps.transformEntries(
            Multimaps.index(mappingEntries, InMemoryMappingEntry::asKey),
            (Maps.EntryTransformer<String, InMemoryMappingEntry, Mappings>)
                (s, mappingEntry) -> MappingsImpl.fromRawString(mappingEntry.getMapping()))
            .asMap();
        return Maps.transformEntries(userMappingsMap,
            (s, mappingsList) -> {
                Mappings result = MappingsImpl.empty();
                for (Mappings mappings : mappingsList) {
                    result = result.union(mappings);
                }
                return result;
            });
    }

    private Optional<Mappings> retrieveMappings(final String user, final String domain) {
        List<String> userEntries = Lists.newArrayList(
            Iterables.transform(
                Iterables.filter(mappingEntries,
                    mappingEntry -> user.equals(mappingEntry.getUser()) && domain.equals(mappingEntry.getDomain())),
                InMemoryMappingEntry::getMapping));
        return MappingsImpl.fromCollection(userEntries).toOptional();
    }

}
