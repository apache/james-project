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

package org.apache.james.jmap.api.filtering;

import org.apache.james.eventsourcing.eventstore.dto.EventDTOModule;
import org.apache.james.jmap.api.filtering.impl.IncrementalRuleChange;
import org.apache.james.jmap.api.filtering.impl.RuleSetDefined;

public interface FilteringRuleSetDefineDTOModules {

    String TYPE = "filtering-rule-set-defined";
    String TYPE_INCREMENTAL = "filtering-increment";

    EventDTOModule<RuleSetDefined, FilteringRuleSetDefinedDTO> FILTERING_RULE_SET_DEFINED =
        EventDTOModule
            .forEvent(RuleSetDefined.class)
            .convertToDTO(FilteringRuleSetDefinedDTO.class)
            .toDomainObjectConverter(FilteringRuleSetDefinedDTO::toEvent)
            .toDTOConverter(FilteringRuleSetDefinedDTO::from)
            .typeName(TYPE)
            .withFactory(EventDTOModule::new);

    EventDTOModule<IncrementalRuleChange, FilteringIncrementalRuleChangeDTO> FILTERING_INCREMENT =
        EventDTOModule
            .forEvent(IncrementalRuleChange.class)
            .convertToDTO(FilteringIncrementalRuleChangeDTO.class)
            .toDomainObjectConverter(FilteringIncrementalRuleChangeDTO::toEvent)
            .toDTOConverter(FilteringIncrementalRuleChangeDTO::from)
            .typeName(TYPE_INCREMENTAL)
            .withFactory(EventDTOModule::new);

}
