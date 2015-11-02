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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class MappingsImpl implements Mappings {

    public static MappingsImpl empty() {
        return new MappingsImpl(new ArrayList<String>());
    }
    
    public static MappingsImpl fromRawString(String raw) {
        return new MappingsImpl(mappingToCollection(raw));
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
    
    public static Mappings fromCollection(Collection<String> mappings) {
        return new MappingsImpl(mappings);
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
        
        private final ImmutableList.Builder<String> mappings;
        
        private Builder() {
            mappings = ImmutableList.builder();
        }

        public Builder add(String mapping) {
            mappings.add(mapping);
            return this;
        }

        public Builder addAll(Mappings mappings) {
            this.mappings.addAll(mappings.asStrings());
            return this;
        }
        
        public Mappings build() {
            return new MappingsImpl(mappings.build());
        }
        
    }
    
    private final ImmutableList<String> mappings;

    private MappingsImpl(Collection<String> mappings) {
        this.mappings = ImmutableList.copyOf(mappings);
    }
    
    @Override
    public Iterable<String> asStrings() {
        return mappings;
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
    public Mappings remove(String mapping) {
        if (mappings.contains(mapping)) {
            ArrayList<String> updatedMappings = Lists.newArrayList(mappings);
            updatedMappings.remove(mapping);
            return MappingsImpl.fromCollection(updatedMappings);
        }
        return this;
    }

    @Override
    public boolean isEmpty() {
        return mappings.isEmpty();
    }

}