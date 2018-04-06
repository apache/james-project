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
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Domain;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.util.OptionalUtils;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Objects;
import com.google.common.collect.Multimaps;

public class MemoryRecipientRewriteTable extends AbstractRecipientRewriteTable {

    private static class InMemoryMappingEntry {
        private final String user;
        private final Domain domain;
        private final Mapping mapping;

        public InMemoryMappingEntry(String user, Domain domain, Mapping mapping) {
            this.user = user;
            this.domain = domain;
            this.mapping = mapping;
        }

        public String getUser() {
            return user;
        }

        public Domain getDomain() {
            return domain;
        }

        public Mapping getMapping() {
            return mapping;
        }

        public String asKey() {
            return getUser() + "@" + getDomain().asString();
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
    protected void addMappingInternal(String user, Domain domain, Mapping mapping) {
        mappingEntries.add(new InMemoryMappingEntry(getFixedUser(user), getFixedDomain(domain), mapping));
    }

    @Override
    protected void removeMappingInternal(String user, Domain domain, Mapping mapping) {
        mappingEntries.remove(new InMemoryMappingEntry(getFixedUser(user), getFixedDomain(domain), mapping));
    }

    @Override
    protected Mappings getUserDomainMappingsInternal(String user, Domain domain) {
        return retrieveMappings(user, domain)
            .orElse(null);
    }

    @Override
    protected Mappings mapAddress(String user, Domain domain) {
        return OptionalUtils.orSuppliers(
            () -> retrieveMappings(user, domain),
            () -> retrieveMappings(WILDCARD, domain))
            .orElse(MappingsImpl.empty());
    }

    @Override
    protected Map<String, Mappings> getAllMappingsInternal() {
        if (mappingEntries.isEmpty()) {
            return null;
        }
        return Multimaps.index(mappingEntries, InMemoryMappingEntry::asKey)
            .asMap()
            .entrySet()
            .stream()
            .map(entry -> Pair.of(entry.getKey(), toMappings(entry.getValue())))
            .collect(Guavate.toImmutableMap(Pair::getKey, Pair::getValue));
    }

    private MappingsImpl toMappings(Collection<InMemoryMappingEntry> inMemoryMappingEntries) {
        return MappingsImpl.fromMappings(inMemoryMappingEntries
            .stream()
            .map(InMemoryMappingEntry::getMapping));
    }

    private Optional<Mappings> retrieveMappings(String user, Domain domain) {
        Stream<Mapping> userEntries = mappingEntries.stream()
            .filter(mappingEntry -> user.equals(mappingEntry.getUser()) && domain.equals(mappingEntry.getDomain()))
            .map(InMemoryMappingEntry::getMapping);
        return MappingsImpl.fromMappings(userEntries).toOptional();
    }

}
