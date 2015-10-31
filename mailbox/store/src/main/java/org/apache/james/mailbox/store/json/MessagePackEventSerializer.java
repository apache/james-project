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
import org.msgpack.jackson.dataformat.MessagePackFactory;

/**
 * Message Pack ( http://msgpack.org/ ) Event Serializer
 */
public class MessagePackEventSerializer<Id extends MailboxId> extends JacksonBasedEventSerializer<Id> {

    public MessagePackEventSerializer(MailboxConverter<Id> mailboxConverter) {
        super(mailboxConverter, new ObjectMapper(new MessagePackFactory()));
    }
}
