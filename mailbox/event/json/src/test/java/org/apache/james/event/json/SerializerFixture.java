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

package org.apache.james.event.json;

import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;

public interface SerializerFixture {
    JsonSerialize DTO_JSON_SERIALIZE = new JsonSerialize(new TestId.Factory(), new TestMessageId.Factory());
    EventSerializer EVENT_SERIALIZER = new EventSerializer(new TestId.Factory(), new TestMessageId.Factory());

    String SERIALIZED_EVENT_ID = "6e0dd59d-660e-4d9b-b22f-0354479f47b4";
    Event.EventId EVENT_ID = Event.EventId.of(SERIALIZED_EVENT_ID);
}
