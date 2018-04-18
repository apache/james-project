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
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.james.rrt.lib.Mapping.Type;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class MappingsImpl implements Mappings, Serializable {

    private static final long serialVersionUID = 1L;

    private static class DefaultMappingOrderingPolicy {

        private static final Comparator<Mapping> MAPPING_COMPARATOR = Comparator
            .<Mapping, Integer>comparing(DefaultMappingOrderingPolicy::typeOrder)
            .thenComparing(Mapping::asString);

        private static int typeOrder(Mapping mapping) {
            return typeOrder(mapping.getType());
        }

        private static int typeOrder(Mapping.Type type) {
            switch (type) {
                case Domain:
                    return 1;
                case Group:
                    return 2;
                case Forward:
                    return 3;
                case Regex:
                    return 4;
                case Error:
                    return 4;
                case Address:
                    return 4;
            }
            throw new IllegalArgumentException("missing enum handling");
        }

        public Comparator<Mapping> comparator() {
            return MAPPING_COMPARATOR;
        }
    }



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
        return fromMappings(mappings.stream()
            .map(Mapping::of));
    }
    
    public static MappingsImpl fromMappings(Stream<Mapping> mappings) {
        return mappings
            .reduce(builder(), Builder::add, Builder::merge)
            .build();
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

        public static Builder merge(Builder builder1, Builder builder2) {
            return builder1.addAll(builder2.build());
        }
        
        private final ImmutableList.Builder<Mapping> mappings;
        
        private Builder() {
            mappings = ImmutableList.builder();
        }

        public Builder add(String mapping) {
            return add(Mapping.of(mapping));
        }

        public Builder add(Mapping mapping) {
            mappings.add(mapping);
            return this;
        }
        
        public Builder addAll(Mappings mappings) {
            this.mappings.addAll(mappings);
            return this;
        }
        
        public Builder addAll(Iterable<String> mappings) {
            mappings.forEach(this::add);
            return this;
        }
        
        public MappingsImpl build() {
            return new MappingsImpl(mappings.build()
                .stream()
                .sorted(new DefaultMappingOrderingPolicy().comparator())
                .collect(Guavate.toImmutableList()));
        }

    }
    
    private final ImmutableList<Mapping> mappings;

    private MappingsImpl(Collection<Mapping> mappings) {
        this.mappings = ImmutableList.copyOf(mappings);
    }
    
    @Override
    public Iterable<String> asStrings() {
        return mappings.stream()
            .map(Mapping::asString)
            .collect(Guavate.toImmutableList());
    }

    @Override
    public boolean contains(Mapping mapping) {
        return mappings.contains(mapping);
    }

    @Override
    public int size() {
        return mappings.size();
    }

    @Override
    public Mappings remove(Mapping mapping) {
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
        return mappings.stream()
            .anyMatch(hasType(type));
    }
    
    @Override
    public Mappings select(Type type) {
        Preconditions.checkNotNull(type);
        return fromMappings(mappings.stream()
            .filter(hasType(type)));
    }
    
    
    @Override
    public Mappings exclude(Type type) {
        Preconditions.checkNotNull(type);
        return fromMappings(mappings.stream()
            .filter(hasType(type).negate()));
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
            return Optional.empty();
        }
        return Optional.of(this);
    }

    @Override
    public Mappings union(Mappings mappings) {
        Preconditions.checkState(mappings != null, "mappings is mandatory");
        return from(this).addAll(mappings).build();
    }

    @Override
    public Stream<Mapping> asStream() {
        return mappings.stream();
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
        return "MappingsImpl{" +
            "mappings=" + mappings +
            '}';
    }
}