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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;

public class MemoryRecipientRewriteTable extends AbstractRecipientRewriteTable {

    private final Map<MappingSource, Set<Mapping>> table = new HashMap<>();

    private Mappings toMappings(Set<Mapping> mappings) {
        return mappings == null ? MappingsImpl.empty() : MappingsImpl.fromMappings(mappings.stream());
    }

    @Override
    public void addMapping(MappingSource source, Mapping mapping) {
        table.computeIfAbsent(source, s -> new LinkedHashSet<>()).add(mapping);
    }

    @Override
    public void removeMapping(MappingSource source, Mapping mapping) {
        Set<Mapping> mappings = table.get(source);
        if (mappings != null) {
            mappings.remove(mapping);
            if (mappings.isEmpty()) {
                table.remove(source);
            }
        }
    }

    @Override
    public Mappings getStoredMappings(MappingSource mappingSource) {
        return toMappings(table.get(mappingSource));
    }

    @Override
    public Map<MappingSource, Mappings> getAllMappings() {
        return table.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey(), e -> toMappings(e.getValue())));
    }
}
