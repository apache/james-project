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
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.event.EventSerializer;
import org.apache.james.mailbox.store.json.event.EventIntermediate;
import org.apache.james.mailbox.store.json.event.MailboxConverter;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxId;

public class JacksonBasedEventSerializer<Id extends MailboxId> implements EventSerializer {

    private final MailboxConverter<Id> mailboxConverter;
    private final EventFactory<Id> eventFactory;
    private final ObjectMapper objectMapper;

    public JacksonBasedEventSerializer(MailboxConverter<Id> mailboxConverter, ObjectMapper objectMapper) {
        this.mailboxConverter = mailboxConverter;
        this.eventFactory = new EventFactory<Id>();
        this.objectMapper = objectMapper;
    }

    public byte[] serializeEvent(MailboxListener.Event event) throws Exception {
        EventIntermediate eventIntermediate = new EventIntermediate(event, mailboxConverter.extractMailboxIntermediate(event));
        return objectMapper.writeValueAsBytes(eventIntermediate);
    }

    public MailboxListener.Event deSerializeEvent(byte[] serializedEvent) throws Exception {
        EventIntermediate eventIntermediate = objectMapper.readValue(serializedEvent, EventIntermediate.class);
        Mailbox<Id> mailbox = mailboxConverter.retrieveMailbox(eventIntermediate.getMailboxIntermediate());
        switch (eventIntermediate.getEventType()) {
            case ADDED:
                return eventFactory.added(eventIntermediate.getSessionDeserialized(),
                    eventIntermediate.getMetatdataDeserialized(),
                    mailbox);
            case DELETED:
                return eventFactory.expunged(eventIntermediate.getSessionDeserialized(),
                    eventIntermediate.getMetatdataDeserialized(),
                    mailbox);
            case FLAGS:
                return eventFactory.flagsUpdated(eventIntermediate.getSessionDeserialized(),
                    eventIntermediate.getUids(),
                    mailbox,
                    eventIntermediate.getFlagsDeserialized());
            case MAILBOX_ADDED:
                return eventFactory.mailboxAdded(eventIntermediate.getSessionDeserialized(), mailbox);
            case MAILBOX_DELETED:
                return eventFactory.mailboxDeleted(eventIntermediate.getSessionDeserialized(), mailbox);
            case MAILBOX_RENAMED:
                return eventFactory.mailboxRenamed(eventIntermediate.getSessionDeserialized(),
                    eventIntermediate.getFrom(),
                    mailbox);
            default:
                throw new Exception("Can not deserialize unknown event");
        }
    }
}
