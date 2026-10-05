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

package org.apache.james.xodus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;

import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityIterable;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.StoreTransaction;

public class XodusRecipientRewriteTable extends AbstractRecipientRewriteTable {
    private static final String ENTITY_TYPE = "JamesRRTMapping";
    private static final String PROP_SOURCE = "source";
    private static final String PROP_MAPPING = "mapping";

    private final PersistentEntityStore entityStore;

    @Inject
    public XodusRecipientRewriteTable(PersistentEntityStore entityStore) {
        this.entityStore = entityStore;
    }

    @Override
    public void addMapping(MappingSource source, Mapping mapping) {
        entityStore.executeInTransaction(txn -> {
            for (Entity entity : findEntities(txn, source)) {
                String existing = (String) entity.getProperty(PROP_MAPPING);
                if (mapping.asString().equals(existing)) {
                    return; // already exists
                }
            }
            Entity entity = txn.newEntity(ENTITY_TYPE);
            entity.setProperty(PROP_SOURCE, source.asString());
            entity.setProperty(PROP_MAPPING, mapping.asString());
        });
    }

    @Override
    public void removeMapping(MappingSource source, Mapping mapping) {
        entityStore.executeInTransaction(txn -> {
            for (Entity entity : findEntities(txn, source)) {
                String existing = (String) entity.getProperty(PROP_MAPPING);
                if (mapping.asString().equals(existing)) {
                    entity.delete();
                }
            }
        });
    }

    @Override
    public Mappings getStoredMappings(MappingSource source) {
        return entityStore.computeInReadonlyTransaction(txn -> {
            List<Mapping> list = new ArrayList<>();
            for (Entity entity : findEntities(txn, source)) {
                String mapStr = (String) entity.getProperty(PROP_MAPPING);
                if (mapStr != null) {
                    list.add(Mapping.of(mapStr));
                }
            }
            return MappingsImpl.fromMappings(list.stream());
        });
    }

    @Override
    public Map<MappingSource, Mappings> getAllMappings() {
        return entityStore.computeInReadonlyTransaction(txn -> {
            Map<MappingSource, List<Mapping>> map = new HashMap<>();
            for (Entity entity : txn.getAll(ENTITY_TYPE)) {
                String src = (String) entity.getProperty(PROP_SOURCE);
                String mapStr = (String) entity.getProperty(PROP_MAPPING);
                if (src != null && mapStr != null) {
                    MappingSource source = MappingSource.parse(src);
                    map.computeIfAbsent(source, s -> new ArrayList<>()).add(Mapping.of(mapStr));
                }
            }
            Map<MappingSource, Mappings> result = new HashMap<>();
            map.forEach((k, v) -> result.put(k, MappingsImpl.fromMappings(v.stream())));
            return result;
        });
    }

    private EntityIterable findEntities(StoreTransaction txn, MappingSource source) {
        return txn.find(ENTITY_TYPE, PROP_SOURCE, source.asString());
    }
}
