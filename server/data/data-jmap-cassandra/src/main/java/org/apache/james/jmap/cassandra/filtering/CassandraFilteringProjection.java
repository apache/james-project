package org.apache.james.jmap.cassandra.filtering;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.jmap.cassandra.filtering.CassandraFilteringProjectionModule.AGGREGATE_ID;
import static org.apache.james.jmap.cassandra.filtering.CassandraFilteringProjectionModule.EVENT_ID;
import static org.apache.james.jmap.cassandra.filtering.CassandraFilteringProjectionModule.RULES;
import static org.apache.james.jmap.cassandra.filtering.CassandraFilteringProjectionModule.TABLE_NAME;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.ReactiveSubscriber;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.jmap.api.filtering.Version;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.jmap.api.filtering.impl.FilteringAggregateId;
import org.apache.james.jmap.api.filtering.impl.IncrementalRuleChange;
import org.apache.james.jmap.api.filtering.impl.RuleSetDefined;
import org.reactivestreams.Publisher;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class CassandraFilteringProjection implements EventSourcingFilteringManagement.ReadProjection {
    private final CassandraAsyncExecutor executor;

    private final PreparedStatement insertStatement;
    private final PreparedStatement readStatement;
    private final PreparedStatement readVersionStatement;
    private final ObjectMapper objectMapper;

    @Inject
    public CassandraFilteringProjection(CqlSession session) {
        executor = new CassandraAsyncExecutor(session);

        insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(AGGREGATE_ID, bindMarker(AGGREGATE_ID))
            .value(EVENT_ID, bindMarker(EVENT_ID))
            .value(RULES, bindMarker(RULES))
            .build());
        readStatement = session.prepare(selectFrom(TABLE_NAME).all()
            .whereColumn(AGGREGATE_ID).isEqualTo(bindMarker(AGGREGATE_ID))
            .build());
        readVersionStatement = session.prepare(selectFrom(TABLE_NAME).column(EVENT_ID)
            .whereColumn(AGGREGATE_ID).isEqualTo(bindMarker(AGGREGATE_ID))
            .build());

        objectMapper = new ObjectMapper();
    }

    @Override
    public Publisher<Rules> listRulesForUser(Username username) {
        return executor.executeSingleRow(readStatement.bind()
            .setString(AGGREGATE_ID, new FilteringAggregateId(username).asAggregateKey()))
            .handle((row, sink) -> {
                try {
                    Rules rules = parseRules(row);
                    sink.next(rules);
                } catch (JsonProcessingException e) {
                    sink.error(e);
                }
            });
    }

    @Override
    public Publisher<Version> getLatestVersion(Username username) {
        return executor.executeSingleRow(readVersionStatement.bind()
            .setString(AGGREGATE_ID, new FilteringAggregateId(username).asAggregateKey()))
            .map(this::parseVersion);
    }

    @Override
    public Optional<ReactiveSubscriber> subscriber(Function<Username, Publisher<Rules>> ruleLoader) {
        return Optional.of(new ReactiveSubscriber() {
            @Override
            public Publisher<Void> handleReactive(Event event) {
                if (event instanceof RuleSetDefined) {
                    return persist((RuleSetDefined) event);
                }
                if (event instanceof IncrementalRuleChange) {
                    return persist((IncrementalRuleChange) event);
                }
                throw new RuntimeException("Unsupported event");
            }

            private Mono<Void> persist(RuleSetDefined ruleSetDefined) {
                return persistRules(ruleSetDefined.getAggregateId(), ruleSetDefined.eventId(), ruleSetDefined.getRules());
            }

            private Mono<Void> persistRules(AggregateId aggregateId, EventId eventId, ImmutableList<Rule> rules) {
                try {
                    return executor.executeVoid(insertStatement.bind()
                        .setString(AGGREGATE_ID, aggregateId.asAggregateKey())
                        .setInt(EVENT_ID, eventId.value())
                        .setString(RULES, objectMapper.writeValueAsString(RuleDTO.from(rules))));
                } catch (JsonProcessingException e) {
                    return Mono.error(e);
                }
            }

            private Mono<Void> persist(IncrementalRuleChange incrementalRuleChange) {
                FilteringAggregateId filteringAggregateId = (FilteringAggregateId) incrementalRuleChange.getAggregateId();
                return Mono.from(ruleLoader.apply(filteringAggregateId.getUsername()))
                    .flatMap(rules -> persistRules(filteringAggregateId, incrementalRuleChange.eventId(), ImmutableList.copyOf(rules.getRules())));
            }
        });
    }

    private Version parseVersion(Row row) {
        return new Version(row.getInt(EVENT_ID));
    }

    private Rules parseRules(Row row) throws JsonProcessingException {
        String serializedRules = row.getString(RULES);
        List<RuleDTO> ruleDTOS = objectMapper.readValue(serializedRules, new TypeReference<>() {});
        Version version = parseVersion(row);
        return new Rules(RuleDTO.toRules(ruleDTOS), version);
    }
}
