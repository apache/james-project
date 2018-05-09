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

package org.apache.james.eventsourcing.cassandra.dto;

import org.apache.james.eventsourcing.Event;
import org.testcontainers.shaded.com.google.common.base.Preconditions;

public class OtherTestEventDTOModule implements EventDTOModule {

    public static final String OTHER_TYPE = "other-type";

    @Override
    public String getType() {
        return OTHER_TYPE;
    }

    @Override
    public Class<? extends EventDTO> getDTOClass() {
        return OtherTestEventDTO.class;
    }

    @Override
    public Class<? extends Event> getEventClass() {
        return OtherEvent.class;
    }

    @Override
    public EventDTO toDTO(Event event) {
        Preconditions.checkArgument(event instanceof OtherEvent);
        OtherEvent otherEvent = (OtherEvent) event;

        return new OtherTestEventDTO(
            OTHER_TYPE,
            otherEvent.getPayload(),
            otherEvent.eventId().serialize(),
            otherEvent.getAggregateId().getId());
    }
}
