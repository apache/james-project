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

package org.apache.james.mailbox.store.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.store.event.EventSerializer;
import org.apache.james.mailbox.store.json.event.EventConverter;
import org.apache.james.mailbox.store.json.event.dto.EventDataTransferObject;
import org.apache.james.mailbox.store.mail.model.MailboxId;

public class JacksonEventSerializer<Id extends MailboxId> implements EventSerializer {

    private final EventConverter<Id> eventConverter;
    private final ObjectMapper objectMapper;

    public JacksonEventSerializer(EventConverter<Id> eventConverter, ObjectMapper objectMapper) {
        this.eventConverter = eventConverter;
        this.objectMapper = objectMapper;
    }

    public byte[] serializeEvent(MailboxListener.Event event) throws Exception {
        return objectMapper.writeValueAsBytes(eventConverter.convertToDataTransferObject(event));
    }

    public MailboxListener.Event deSerializeEvent(byte[] serializedEvent) throws Exception {
        EventDataTransferObject eventDataTransferObject = objectMapper.readValue(serializedEvent, EventDataTransferObject.class);
        return eventConverter.retrieveEvent(eventDataTransferObject);
    }
}
