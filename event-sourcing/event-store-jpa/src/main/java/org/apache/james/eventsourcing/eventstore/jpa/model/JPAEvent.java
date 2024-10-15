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
package org.apache.james.eventsourcing.eventstore.jpa.model;

import static org.apache.james.eventsourcing.eventstore.jpa.model.JPAEvent.DELETE_AGGREGATE_QUERY;
import static org.apache.james.eventsourcing.eventstore.jpa.model.JPAEvent.JPAEventId;
import static org.apache.james.eventsourcing.eventstore.jpa.model.JPAEvent.SELECT_AGGREGATE_QUERY;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.EventId;

import com.google.common.base.Objects;



/**
 * JPAEvent class for the James Event Sourcing to be used for JPA persistence.
 */
@Entity(name = "JPAEvent")
@Table(name = JPAEvent.JAMES_EVENTS, indexes = {
    @Index(name = "AGGREGATE_ID_INDEX", columnList = "AGGREGATE_ID")
})
@NamedQuery(name = SELECT_AGGREGATE_QUERY, query = "SELECT e FROM JPAEvent e WHERE e.aggregateId=:aggregateId")
@NamedQuery(name = DELETE_AGGREGATE_QUERY, query = "DELETE FROM JPAEvent e WHERE e.aggregateId=:aggregateId")
@IdClass(JPAEventId.class)
public class JPAEvent {
    public static final String SELECT_AGGREGATE_QUERY = "selectAggregateEvents";
    public static final String DELETE_AGGREGATE_QUERY = "deleteAggregateEvents";

    public static final String JAMES_EVENTS = "JAMES_EVENTS";

    public static class JPAEventId implements Serializable {

        private static final long serialVersionUID = 1L;

        private String aggregateId;

        private int eventId;

        public JPAEventId() {
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(aggregateId, eventId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final JPAEventId other = (JPAEventId) obj;
            return Objects.equal(this.aggregateId, other.aggregateId)
                && Objects.equal(this.eventId, other.eventId);
        }
    }

    @Id
    @Column(name = "AGGREGATE_ID", nullable = false, length = 100)
    private String aggregateId = "";

    @Id
    @Column(name = "EVENT_ID", nullable = false)
    private int eventId;

    @Lob
    @Column(name = "EVENT", nullable = false, length = 1048576000)
    private String event = "";

    /**
     * Default no-args constructor for JPA class enhancement.
     * The constructor need to be public or protected to be used by JPA.
     * See:  http://docs.oracle.com/javaee/6/tutorial/doc/bnbqa.html
     * Do not us this constructor, it is for JPA only.
     */
    protected JPAEvent() {
    }

    public JPAEvent(AggregateId aggregateId, EventId eventId, String event) {
        this.aggregateId = aggregateId.asAggregateKey();
        this.eventId = eventId.serialize();
        this.event = event;
    }

    public EventId getEventId() {
        return EventId.fromSerialized(eventId);
    }

    public String getEvent() {
        return event;
    }

}
