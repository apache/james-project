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

package org.apache.james.rrt.postgres;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Domain;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class PostgresRecipientRewriteTable extends AbstractRecipientRewriteTable {
    private PostgresRecipientRewriteTableDAO postgresRecipientRewriteTableDAO;

    @Inject
    public PostgresRecipientRewriteTable(PostgresRecipientRewriteTableDAO postgresRecipientRewriteTableDAO) {
        this.postgresRecipientRewriteTableDAO = postgresRecipientRewriteTableDAO;
    }

    @Override
    public void addMapping(MappingSource source, Mapping mapping) {
        postgresRecipientRewriteTableDAO.addMapping(source, mapping).block();
    }

    @Override
    public void removeMapping(MappingSource source, Mapping mapping) {
        postgresRecipientRewriteTableDAO.removeMapping(source, mapping).block();
    }

    @Override
    public Mappings getStoredMappings(MappingSource source) {
        return postgresRecipientRewriteTableDAO.getMappings(source).block();
    }

    @Override
    public Map<MappingSource, Mappings> getAllMappings() {
        return postgresRecipientRewriteTableDAO.getAllMappings()
            .collect(ImmutableMap.toImmutableMap(
                Pair::getLeft,
                pair -> MappingsImpl.fromMappings(pair.getRight()),
                Mappings::union))
            .block();
    }

    @Override
    protected Mappings mapAddress(String user, Domain domain) {
        return postgresRecipientRewriteTableDAO.getMappings(MappingSource.fromUser(user, domain))
            .filter(Predicate.not(Mappings::isEmpty))
            .blockOptional()
            .orElse(postgresRecipientRewriteTableDAO.getMappings(MappingSource.fromDomain(domain)).block());
    }

    @Override
    public Stream<MappingSource> listSources(Mapping mapping) {
        Preconditions.checkArgument(listSourcesSupportedType.contains(mapping.getType()),
            "Not supported mapping of type %s", mapping.getType());

        return postgresRecipientRewriteTableDAO.getSources(mapping).toStream();
    }

}
