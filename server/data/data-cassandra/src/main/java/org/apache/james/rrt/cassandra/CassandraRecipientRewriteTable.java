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

import java.util.Map;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.util.OptionalUtils;

public class CassandraRecipientRewriteTable extends AbstractRecipientRewriteTable {
    private final CassandraRecipientRewriteTableDAO dao;

    @Inject
    public CassandraRecipientRewriteTable(CassandraRecipientRewriteTableDAO dao) {
        this.dao = dao;
    }

    @Override
    public void addMapping(MappingSource source, Mapping mapping) {
        dao.addMapping(source, mapping).block();
    }

    @Override
    public void removeMapping(MappingSource source, Mapping mapping) {
        dao.removeMapping(source, mapping).block();
    }

    @Override
    public Mappings getStoredMappings(MappingSource source) {
        return dao.retrieveMappings(source)
            .blockOptional()
            .orElse(MappingsImpl.empty());
    }

    @Override
    public Map<MappingSource, Mappings> getAllMappings() {
        return dao.getAllMappings().block();
    }

    @Override
    protected Mappings mapAddress(String user, Domain domain) {
        return OptionalUtils.orSuppliers(
            () -> dao.retrieveMappings(MappingSource.fromUser(user, domain)).blockOptional(),
            () -> dao.retrieveMappings(MappingSource.fromDomain(domain)).blockOptional())
                .orElse(MappingsImpl.empty());
    }
}
