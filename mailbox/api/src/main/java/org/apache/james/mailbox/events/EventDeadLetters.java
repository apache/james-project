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

package org.apache.james.mailbox.events;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventDeadLetters {

    String REGISTERED_GROUP_CANNOT_BE_NULL = "registeredGroup cannot be null";
    String FAIL_DELIVERED_EVENT_CANNOT_BE_NULL = "failDeliveredEvent cannot be null";
    String FAIL_DELIVERED_ID_EVENT_CANNOT_BE_NULL = "failDeliveredEventId cannot be null";

    Mono<Void> store(Group registeredGroup, Event failDeliveredEvent);

    Mono<Void> remove(Group registeredGroup, Event.EventId failDeliveredEventId);

    Mono<Event> failedEvent(Group registeredGroup, Event.EventId failDeliveredEventId);

    Flux<Event.EventId> failedEventIds(Group registeredGroup);

    Flux<Group> groupsWithFailedEvents();
}
