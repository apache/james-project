/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.mailbox.cassandra.mail.eventsourcing.acl;

import java.util.Objects;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;
import org.apache.james.mailbox.cassandra.ids.CassandraId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

class ACLUpdatedDTO implements EventDTO {

    static ACLUpdatedDTO from(ACLUpdated event, String type) {
        Preconditions.checkNotNull(event);

        return new ACLUpdatedDTO(
                event.eventId().serialize(),
                event.getAggregateId().asAggregateKey(),
                type,
                ACLDiffDTO.fromACLDiff(event.getAclDiff()));
    }

    static ACLUpdatedDTO from(ACLUpdated event) {
        return from(event, ACLModule.UPDATE_TYPE_NAME);
    }

    private final int eventId;
    private final String aggregateKey;
    private final String type;
    private final ACLDiffDTO aclDiff;

    @JsonCreator
    ACLUpdatedDTO(@JsonProperty("eventId") int eventId,
                  @JsonProperty("aggregateKey") String aggregateKey,
                  @JsonProperty("type") String type,
                  @JsonProperty("aclDiff") ACLDiffDTO aclDiff) {
        this.eventId = eventId;
        this.aggregateKey = aggregateKey;
        this.type = type;
        this.aclDiff = aclDiff;
    }

    @JsonIgnore
    public ACLUpdated toEvent() {
        return new ACLUpdated(
            new MailboxAggregateId(CassandraId.of(aggregateKey)),
            EventId.fromSerialized(eventId),
            aclDiff.asACLDiff());
    }

    public int getEventId() {
        return eventId;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public String getType() {
        return type;
    }

    public ACLDiffDTO getAclDiff() {
        return aclDiff;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ACLUpdatedDTO) {
            ACLUpdatedDTO that = (ACLUpdatedDTO) o;

            return Objects.equals(this.eventId, that.eventId)
                && Objects.equals(this.aggregateKey, that.aggregateKey)
                && Objects.equals(this.type, that.type)
                && Objects.equals(this.aclDiff, that.aclDiff);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(eventId, aggregateKey, type, aclDiff);
    }
}
