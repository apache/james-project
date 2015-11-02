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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.StringTokenizer;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class MappingsImpl implements Mappings {

    public static MappingsImpl empty() {
        return builder().build();
    }
    
    public static MappingsImpl fromRawString(String raw) {
        return fromCollection(mappingToCollection(raw));
    }
    
    private static ArrayList<String> mappingToCollection(String rawMapping) {
        ArrayList<String> map = new ArrayList<String>();
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
            mappings.add(MappingImpl.of(mapping));
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
        return FluentIterable.from(mappings).transform(new Function<Mapping, String>() {
            @Override
            public String apply(Mapping input) {
                return input.asString();
            }
        });
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

}