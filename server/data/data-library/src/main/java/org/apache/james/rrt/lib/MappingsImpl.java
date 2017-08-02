/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.rrt.lib;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.james.rrt.lib.Mapping.Type;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class MappingsImpl implements Mappings, Serializable {

    private static final long serialVersionUID = 1L;

    public static MappingsImpl empty() {
        return builder().build();
    }
    
    public static MappingsImpl fromRawString(String raw) {
        return fromCollection(mappingToCollection(raw));
    }
    
    private static ArrayList<String> mappingToCollection(String rawMapping) {
        ArrayList<String> map = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(rawMapping, RecipientRewriteTableUtil.getSeparator(rawMapping));
        while (tokenizer.hasMoreTokens()) {
            final String raw = tokenizer.nextToken().trim();
            map.add(raw);
        }
        return map;
    }
    
    public static MappingsImpl fromCollection(Collection<String> mappings) {
        Builder builder = builder();
        for (String mapping: mappings) {
            builder.add(mapping);
        }
        return builder.build();
    }
    
    public static MappingsImpl fromMappings(Iterable<Mapping> mappings) {
        Builder builder = builder();
        for (Mapping mapping: mappings) {
            builder.add(mapping);
        }
        return builder.build();
    }
    
    public static Builder from(Mappings from) {
        Builder builder = new Builder();
        builder.addAll(from);
        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        
        private final ImmutableList.Builder<Mapping> mappings;
        
        private Builder() {
            mappings = ImmutableList.builder();
        }

        public Builder add(String mapping) {
            return add(MappingImpl.of(mapping));
        }

        public Builder add(Mapping mapping) {
            mappings.add(mapping);
            return this;
        }

        
        public Builder addAll(Mappings mappings) {
            this.mappings.addAll(mappings);
            return this;
        }
        
        public MappingsImpl build() {
            return new MappingsImpl(mappings.build());
        }
        
    }
    
    private final ImmutableList<Mapping> mappings;

    private MappingsImpl(Collection<Mapping> mappings) {
        this.mappings = ImmutableList.copyOf(mappings);
    }
    
    @Override
    public Iterable<String> asStrings() {
        return FluentIterable.from(mappings).transform(Mapping::asString);
    }

    @Override
    public boolean contains(String mapping) {
        return mappings.contains(mapping);
    }

    @Override
    public int size() {
        return mappings.size();
    }

    @Override
    public Mappings remove(String mappingAsString) {
        MappingImpl mapping = MappingImpl.of(mappingAsString);
        if (mappings.contains(mapping)) {
            ArrayList<Mapping> updatedMappings = Lists.newArrayList(mappings);
            updatedMappings.remove(mapping);
            return new MappingsImpl(updatedMappings);
        }
        return this;
    }

    @Override
    public boolean isEmpty() {
        return mappings.isEmpty();
    }
    
    @Override
    public Iterator<Mapping> iterator() {
        return mappings.iterator();
    }
    
    @Override
    public String serialize() {
        return Joiner.on(';').join(asStrings());
    }
    
    private Predicate<Mapping> hasType(final Mapping.Type type) {
        return mapping -> mapping.getType().equals(type);
    }
    
    @Override
    public boolean contains(Type type) {
        Preconditions.checkNotNull(type);
        return FluentIterable.from(mappings).anyMatch(hasType(type));
    }
    
    @Override
    public Mappings select(Type type) {
        Preconditions.checkNotNull(type);
        return fromMappings(FluentIterable.from(mappings).filter(hasType(type)));
    }
    
    
    @Override
    public Mappings exclude(Type type) {
        Preconditions.checkNotNull(type);
        return fromMappings(FluentIterable.from(mappings).filter(Predicates.not(hasType(type))));
    }
 
    @Override
    public Mapping getError() {
        Mappings errors = select(Type.Error);
        Preconditions.checkState(!errors.isEmpty());
        return Iterables.getFirst(errors, null);
    }

    @Override
    public Optional<Mappings> toOptional() {
        if (isEmpty()) {
            return Optional.absent();
        }
        return Optional.<Mappings> of(this);
    }

    @Override
    public Mappings union(Mappings mappings) {
        Preconditions.checkState(mappings != null, "mappings is mandatory");
        return from(this).addAll(mappings).build();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mappings);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MappingsImpl) {
            MappingsImpl other = (MappingsImpl) obj;
            return Objects.equal(mappings, other.mappings);
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass()).add("mappings", mappings).toString();
    }
}