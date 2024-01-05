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

import static org.apache.james.backends.postgres.utils.PoolPostgresExecutor.POOL_INJECT_NAME;
import static org.apache.james.rrt.postgres.PostgresRecipientRewriteTableModule.PostgresRecipientRewriteTableTable.DOMAIN_NAME;
import static org.apache.james.rrt.postgres.PostgresRecipientRewriteTableModule.PostgresRecipientRewriteTableTable.PK_CONSTRAINT_NAME;
import static org.apache.james.rrt.postgres.PostgresRecipientRewriteTableModule.PostgresRecipientRewriteTableTable.TABLE_NAME;
import static org.apache.james.rrt.postgres.PostgresRecipientRewriteTableModule.PostgresRecipientRewriteTableTable.TARGET_ADDRESS;
import static org.apache.james.rrt.postgres.PostgresRecipientRewriteTableModule.PostgresRecipientRewriteTableTable.USERNAME;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresRecipientRewriteTableDAO {
    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresRecipientRewriteTableDAO(@Named(POOL_INJECT_NAME) PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Void> addMapping(MappingSource source, Mapping mapping) {
        return postgresExecutor.executeVoid(dslContext ->
            Mono.from(dslContext.insertInto(TABLE_NAME, USERNAME, DOMAIN_NAME, TARGET_ADDRESS)
                .values(source.getFixedUser(),
                    source.getFixedDomain(),
                    mapping.asString())
                .onConflictOnConstraint(PK_CONSTRAINT_NAME)
                .doUpdate()
                .set(TARGET_ADDRESS, mapping.asString())));
    }

    public Mono<Void> removeMapping(MappingSource source, Mapping mapping) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(USERNAME.eq(source.getFixedUser()))
            .and(DOMAIN_NAME.eq(source.getFixedDomain()))
            .and(TARGET_ADDRESS.eq(mapping.asString()))));
    }

    public Mono<Mappings> getMappings(MappingSource source) {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)
                .where(USERNAME.eq(source.getFixedUser()))
                .and(DOMAIN_NAME.eq(source.getFixedDomain()))))
            .map(record -> record.get(TARGET_ADDRESS))
            .collect(ImmutableList.toImmutableList())
            .map(MappingsImpl::fromCollection);
    }

    public Flux<Pair<MappingSource, Mapping>> getAllMappings() {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)))
            .map(record -> Pair.of(
                MappingSource.fromUser(record.get(USERNAME), record.get(DOMAIN_NAME)),
                Mapping.of(record.get(TARGET_ADDRESS))));
    }

    public Flux<MappingSource> getSources(Mapping mapping) {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)
                .where(TARGET_ADDRESS.eq(mapping.asString()))))
            .map(record -> MappingSource.fromUser(record.get(USERNAME), record.get(DOMAIN_NAME)));
    }
}
