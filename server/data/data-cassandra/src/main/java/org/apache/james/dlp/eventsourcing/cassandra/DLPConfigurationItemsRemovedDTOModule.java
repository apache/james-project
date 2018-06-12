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

package org.apache.james.dlp.eventsourcing.cassandra;

import org.apache.james.dlp.eventsourcing.events.ConfigurationItemsRemoved;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;

import com.google.common.base.Preconditions;

public class DLPConfigurationItemsRemovedDTOModule implements EventDTOModule {
    private static final String DLP_CONFIGURATION_CLEAR = "dlp-configuration-clear";

    @Override
    public String getType() {
        return DLP_CONFIGURATION_CLEAR;
    }

    @Override
    public Class<? extends EventDTO> getDTOClass() {
        return DLPConfigurationItemsRemovedDTO.class;
    }

    @Override
    public Class<? extends Event> getEventClass() {
        return ConfigurationItemsRemoved.class;
    }

    @Override
    public EventDTO toDTO(Event event) {
        Preconditions.checkArgument(event instanceof ConfigurationItemsRemoved);
        return DLPConfigurationItemsRemovedDTO
            .from((ConfigurationItemsRemoved) event, DLP_CONFIGURATION_CLEAR);
    }
}
