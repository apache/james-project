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

package org.apache.james.jmap.postgres.filtering;

import static org.apache.james.jmap.postgres.filtering.PostgresFilteringProjectionModule.PostgresFilteringProjectionTable.AGGREGATE_ID;
import static org.apache.james.jmap.postgres.filtering.PostgresFilteringProjectionModule.PostgresFilteringProjectionTable.EVENT_ID;
import static org.apache.james.jmap.postgres.filtering.PostgresFilteringProjectionModule.PostgresFilteringProjectionTable.RULES;
import static org.apache.james.jmap.postgres.filtering.PostgresFilteringProjectionModule.PostgresFilteringProjectionTable.TABLE_NAME;

import java.util.List;

import javax.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.RuleDTO;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.jmap.api.filtering.Version;
import org.apache.james.jmap.api.filtering.impl.FilteringAggregateId;
import org.jooq.JSON;
import org.jooq.Record;
import org.reactivestreams.Publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class PostgresFilteringProjectionDAO {
    private final PostgresExecutor postgresExecutor;
    private final ObjectMapper objectMapper;

    @Inject
    public PostgresFilteringProjectionDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
        objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
    }

    public Publisher<Rules> listRulesForUser(Username username) {
        return postgresExecutor.executeRow(dslContext -> dslContext.selectFrom(TABLE_NAME)
            .where(AGGREGATE_ID.eq(new FilteringAggregateId(username).asAggregateKey())))
            .handle((row, sink) -> {
                try {
                    Rules rules = parseRules(row);
                    sink.next(rules);
                } catch (JsonProcessingException e) {
                    sink.error(e);
                }
            });
    }

    public Mono<Void> upsert(AggregateId aggregateId, EventId eventId, ImmutableList<Rule> rules) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(AGGREGATE_ID, aggregateId.asAggregateKey())
            .set(EVENT_ID, eventId.value())
            .set(RULES, convertToJooqJson(rules))
            .onConflict(AGGREGATE_ID)
            .doUpdate()
            .set(EVENT_ID, eventId.value())
            .set(RULES, convertToJooqJson(rules))));
    }

    public Publisher<Version> getVersion(Username username) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(EVENT_ID)
            .from(TABLE_NAME)
            .where(AGGREGATE_ID.eq(new FilteringAggregateId(username).asAggregateKey()))))
            .map(this::parseVersion);
    }

    private Rules parseRules(Record record) throws JsonProcessingException {
        List<RuleDTO> ruleDTOS = objectMapper.readValue(record.get(RULES).data(), new TypeReference<>() {});
        return new Rules(RuleDTO.toRules(ruleDTOS), parseVersion(record));
    }

    private Version parseVersion(Record record) {
        return new Version(record.get(EVENT_ID));
    }

    private JSON convertToJooqJson(List<Rule> rules) {
        try {
            return JSON.json(objectMapper.writeValueAsString(RuleDTO.from(rules)));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
