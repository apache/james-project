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
import org.apache.james.dlp.eventsourcing.events.ConfigurationItemsRemoved;
import org.apache.james.eventsourcing.eventstore.dto.EventDTOModule;

public interface DLPConfigurationModules {

    EventDTOModule<ConfigurationItemsAdded, DLPConfigurationItemAddedDTO> DLP_CONFIGURATION_STORE =
        EventDTOModule
            .forEvent(ConfigurationItemsAdded.class)
            .convertToDTO(DLPConfigurationItemAddedDTO.class)
            .toDomainObjectConverter(DLPConfigurationItemAddedDTO::toEvent)
            .toDTOConverter(DLPConfigurationItemAddedDTO::from)
            .typeName("dlp-configuration-store")
            .withFactory(EventDTOModule::new);

    EventDTOModule<ConfigurationItemsRemoved, DLPConfigurationItemsRemovedDTO> DLP_CONFIGURATION_CLEAR =
        EventDTOModule
            .forEvent(ConfigurationItemsRemoved.class)
            .convertToDTO(DLPConfigurationItemsRemovedDTO.class)
            .toDomainObjectConverter(DLPConfigurationItemsRemovedDTO::toEvent)
            .toDTOConverter(DLPConfigurationItemsRemovedDTO::from)
            .typeName("dlp-configuration-clear")
            .withFactory(EventDTOModule::new);


}
