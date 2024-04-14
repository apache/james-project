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

package org.apache.james.eventsourcing.eventstore.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.cql.{BatchStatementBuilder, BatchType, BoundStatement, PreparedStatement, Row, Statement}
import com.datastax.oss.driver.api.querybuilder.QueryBuilder
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, insertInto, update}
import jakarta.inject.Inject
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.eventsourcing.eventstore.{History, JsonEventSerializer}
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreTable.{AGGREGATE_ID, EVENT, EVENTS_TABLE, EVENT_ID, SNAPSHOT}
import org.apache.james.eventsourcing.{AggregateId, Event, EventId}
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

class EventStoreDao @Inject() (val session: CqlSession,
                               val jsonEventSerializer: JsonEventSerializer) {
  private val cassandraAsyncExecutor = new CassandraAsyncExecutor(session)
  private val insert = prepareInsert(session)
  private val insertSnapshot = prepareInsertSnapshot(session)
  private val select = prepareSelect(session)
  private val selectFrom = prepareSelectFrom(session)
  private val selectSnapshot = prepareSelectSnapshot(session)
  private val deleteByAggregateId = prepareDelete(session)
  private val executionProfile = JamesExecutionProfiles.getLWTProfile(session)

  private def prepareInsert(session: CqlSession): PreparedStatement =
    session.prepare(
      insertInto(EVENTS_TABLE)
        .value(AGGREGATE_ID, bindMarker(AGGREGATE_ID))
        .value(EVENT_ID, bindMarker(EVENT_ID))
        .value(EVENT, bindMarker(EVENT))
        .ifNotExists
        .build())

  private def prepareInsertSnapshot(session: CqlSession): PreparedStatement =
    session.prepare(
      update(EVENTS_TABLE)
        .setColumn(SNAPSHOT, bindMarker(SNAPSHOT))
        .whereColumn(AGGREGATE_ID).isEqualTo(bindMarker(AGGREGATE_ID))
        .build())

  private def prepareSelect(session: CqlSession): PreparedStatement =
    session.prepare(QueryBuilder
      .selectFrom(EVENTS_TABLE)
      .column(EVENT)
      .whereColumn(AGGREGATE_ID).isEqualTo(bindMarker(AGGREGATE_ID))
      .build())

  private def prepareSelectSnapshot(session: CqlSession): PreparedStatement =
    session.prepare(QueryBuilder
      .selectFrom(EVENTS_TABLE)
      .column(SNAPSHOT)
      .whereColumn(AGGREGATE_ID).isEqualTo(bindMarker(AGGREGATE_ID))
      .build())

  private def prepareSelectFrom(session: CqlSession): PreparedStatement =
    session.prepare(QueryBuilder
      .selectFrom(EVENTS_TABLE)
      .column(EVENT)
      .whereColumn(AGGREGATE_ID).isEqualTo(bindMarker(AGGREGATE_ID))
      .whereColumn(EVENT_ID).isGreaterThanOrEqualTo(bindMarker(EVENT_ID))
      .build())

  private def prepareDelete(session: CqlSession): PreparedStatement =
    session.prepare(QueryBuilder.deleteFrom(EVENTS_TABLE)
      .whereColumn(AGGREGATE_ID).isEqualTo(bindMarker(AGGREGATE_ID))
      .build())

  private[cassandra] def appendAll(events: Iterable[Event], lastSnapShot: Option[EventId]): SMono[Boolean] =
    SMono(cassandraAsyncExecutor.executeReturnApplied(appendQuery(events))
      .map(_.booleanValue()))
      .flatMap((success: Boolean) => lastSnapShot
        .filter(_ => success)
        .map(id => SMono(cassandraAsyncExecutor.executeVoid(insertSnapshot(events.head.getAggregateId, id))))
        .getOrElse(SMono.empty)
        .`then`(SMono.just(success)))

  private def appendQuery(events: Iterable[Event]): Statement[_] =
    if (events.size == 1)
      insertEvent(events.head)
    else {
      val batch: BatchStatementBuilder = new BatchStatementBuilder(BatchType.LOGGED)
      events.foreach((event: Event) => batch.addStatement(insertEvent(event)))
      batch.build()
    }

  private def insertEvent(event: Event): BoundStatement =
    insert
      .bind()
      .setString(AGGREGATE_ID, event.getAggregateId.asAggregateKey)
      .setInt(EVENT_ID, event.eventId.serialize)
      .setString(EVENT, jsonEventSerializer.serialize(event))

  private def insertSnapshot(aggregateId: AggregateId, snapshotId: EventId): BoundStatement =
    insertSnapshot
      .bind()
      .setString(AGGREGATE_ID, aggregateId.asAggregateKey)
      .setInt(SNAPSHOT,snapshotId.serialize)

  private[cassandra] def getEventsOfAggregate(aggregateId: AggregateId): SMono[History] =
    asHistory(cassandraAsyncExecutor.executeRows(select.bind()
      .set(AGGREGATE_ID, aggregateId.asAggregateKey, TypeCodecs.TEXT)
      .setExecutionProfile(executionProfile)))

  private[cassandra] def getEventsOfAggregate(aggregateId: AggregateId, snapshotId: EventId): SMono[History] =
    asHistory(cassandraAsyncExecutor.executeRows(selectFrom.bind()
      .set(AGGREGATE_ID, aggregateId.asAggregateKey, TypeCodecs.TEXT)
      .setInt(EVENT_ID, snapshotId.value)
      .setExecutionProfile(executionProfile)))

  private def asHistory(rows: Publisher[Row]): SMono[History] =
    SFlux[Row](rows)
      .concatMap(toEvent)
      .collectSeq()
      .map(_.toList)
      .map(History.of(_))

  private[cassandra] def getSnapshot(aggregateId: AggregateId): SMono[EventId] =
    SMono(cassandraAsyncExecutor.executeSingleRow(selectSnapshot.bind()
      .set(AGGREGATE_ID, aggregateId.asAggregateKey, TypeCodecs.TEXT)))
      .map(row => EventId.fromSerialized(row.get(0, TypeCodecs.INT)))

  def delete(aggregateId: AggregateId): SMono[Unit] =
    SMono(cassandraAsyncExecutor.executeVoid(deleteByAggregateId
      .bind()
      .setString(AGGREGATE_ID, aggregateId.asAggregateKey)))
      .`then`()

  private def toEvent(row: Row): SMono[Event] = SMono.fromCallable(() => jsonEventSerializer.deserialize(row.get(0, TypeCodecs.TEXT)))
    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
}