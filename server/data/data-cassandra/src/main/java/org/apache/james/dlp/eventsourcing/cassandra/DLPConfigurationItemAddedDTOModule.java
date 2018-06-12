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

import org.apache.james.dlp.eventsourcing.events.ConfigurationItemsAdded;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;

import com.google.common.base.Preconditions;

public class DLPConfigurationItemAddedDTOModule implements EventDTOModule {
    private static final String DLP_CONFIGURATION_STORE = "dlp-configuration-store";

    @Override
    public String getType() {
        return DLP_CONFIGURATION_STORE;
    }

    @Override
    public Class<? extends EventDTO> getDTOClass() {
        return DLPConfigurationItemAddedDTO.class;
    }

    @Override
    public Class<? extends Event> getEventClass() {
        return ConfigurationItemsAdded.class;
    }

    @Override
    public EventDTO toDTO(Event event) {
        Preconditions.checkArgument(event instanceof ConfigurationItemsAdded);
        return DLPConfigurationItemAddedDTO
            .from((ConfigurationItemsAdded) event, DLP_CONFIGURATION_STORE);
    }
}
