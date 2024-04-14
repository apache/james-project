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

package org.apache.james;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class RecipientRewriteTableUserEntityValidator implements UserEntityValidator {
    private static final Map<EntityType, Mapping.Type> TYPE_CORRESPONDENCE = ImmutableMap.of(
        EntityType.ALIAS, Mapping.Type.Alias,
        EntityType.GROUP, Mapping.Type.Group);

    private final RecipientRewriteTable rrt;

    @Inject
    public RecipientRewriteTableUserEntityValidator(RecipientRewriteTable rrt) {
        this.rrt = rrt;
    }

    @Override
    public Optional<ValidationFailure> canCreate(Username username, Set<EntityType> ignoredTypes) throws RecipientRewriteTableException {
        Mappings mappings = rrt.getStoredMappings(MappingSource.fromUser(username));

        return filterIgnored(mappings, ignoredTypes)
            .findFirst()
            .map(mapping -> new ValidationFailure("'" + username.asString() + "' already have associated mappings: " + mapping.asString()));
    }

    private Stream<Mapping> filterIgnored(Mappings mappings, Set<EntityType> ignoredTypes) {
        final ImmutableSet<Mapping.Type> types = ignoredTypes.stream()
            .flatMap(type -> Optional.ofNullable(TYPE_CORRESPONDENCE.get(type)).stream())
            .collect(ImmutableSet.toImmutableSet());

        return mappings.asStream()
            .filter(mapping -> !types.contains(mapping.getType()));
    }
}
