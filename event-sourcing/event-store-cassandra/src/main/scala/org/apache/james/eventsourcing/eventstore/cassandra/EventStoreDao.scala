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
import com.datastax.oss.driver.api.core.cql.{BatchStatementBuilder, BatchType, BoundStatement, PreparedStatement, Row, Statement}
import com.datastax.oss.driver.api.querybuilder.QueryBuilder
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, insertInto}
import javax.inject.Inject
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.eventsourcing.eventstore.History
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreTable.{AGGREGATE_ID, EVENT, EVENTS_TABLE, EVENT_ID}
import org.apache.james.eventsourcing.{AggregateId, Event}
import reactor.core.scala.publisher.{SFlux, SMono}

class EventStoreDao @Inject() (val session: CqlSession,
                               val jsonEventSerializer: JsonEventSerializer) {
  private val cassandraAsyncExecutor = new CassandraAsyncExecutor(session)
  private val insert = prepareInsert(session)
  private val select = prepareSelect(session)
  private val executionProfile = JamesExecutionProfiles.getLWTProfile(session)

  private def prepareInsert(session: CqlSession): PreparedStatement =
    session.prepare(
      insertInto(EVENTS_TABLE)
        .value(AGGREGATE_ID, bindMarker(AGGREGATE_ID))
        .value(EVENT_ID, bindMarker(EVENT_ID))
        .value(EVENT, bindMarker(EVENT))
        .ifNotExists
        .build())

  private def prepareSelect(session: CqlSession): PreparedStatement =
    session.prepare(QueryBuilder
      .selectFrom(EVENTS_TABLE)
      .all()
      .whereColumn(AGGREGATE_ID).isEqualTo(bindMarker(AGGREGATE_ID))
      .build())

  private[cassandra] def appendAll(events: Iterable[Event]): SMono[Boolean] =
    SMono(cassandraAsyncExecutor.executeReturnApplied(appendQuery(events))
      .map(_.booleanValue()))

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

  private[cassandra] def getEventsOfAggregate(aggregateId: AggregateId): SMono[History] = {
    val preparedStatement = select.bind()
      .setString(AGGREGATE_ID, aggregateId.asAggregateKey)
      .setExecutionProfile(executionProfile)
    val rows: SFlux[Row] = SFlux[Row](cassandraAsyncExecutor.executeRows(preparedStatement))

    val events: SFlux[Event] = rows.map(toEvent)
    val listEvents: SMono[List[Event]] = events.collectSeq()
      .map(_.toList)

    listEvents.map(History.of(_))
  }

  private def toEvent(row: Row): Event = jsonEventSerializer.deserialize(row.getString(EVENT))
}