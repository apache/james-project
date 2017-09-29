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

package org.apache.james.mailbox.store.json.event;

import java.io.IOException;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.json.MailboxACLJsonConverter;
import org.apache.james.mailbox.store.json.event.dto.MailboxDataTransferObject;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxIdDeserialisationException;
import org.apache.james.mailbox.store.mail.model.MailboxIdDeserializer;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

public class MailboxConverter {

    private final static Logger LOGGER = LoggerFactory.getLogger(MailboxConverter.class);

    private final MailboxIdDeserializer mailboxIdDeserializer;

    public MailboxConverter(MailboxIdDeserializer mailboxIdDeserializer) {
        this.mailboxIdDeserializer = mailboxIdDeserializer;
    }

    public Mailbox retrieveMailbox(MailboxDataTransferObject mailboxDataTransferObject) {
        SimpleMailbox mailbox = new SimpleMailbox(new MailboxPath(mailboxDataTransferObject.getNamespace(),
            mailboxDataTransferObject.getUser(),
            mailboxDataTransferObject.getName()),
            mailboxDataTransferObject.getUidValidity());
        try {
            mailbox.setACL(MailboxACLJsonConverter.toACL(mailboxDataTransferObject.getSerializedACL()));
            mailbox.setMailboxId(mailboxIdDeserializer.deserialize(mailboxDataTransferObject.getSerializedMailboxId()));
        } catch (IOException e) {
            LOGGER.warn("Failed to deserialize ACL", e);
        } catch (MailboxIdDeserialisationException e) {
            LOGGER.warn("Failed to deserialize mailbox ID", e);
        }
        return mailbox;
    }

    public MailboxDataTransferObject convertMailboxDataTransferObject(Mailbox mailbox) {
        return MailboxDataTransferObject.builder()
            .serializedMailboxId(mailbox.getMailboxId().serialize())
            .namespace(mailbox.getNamespace())
            .user(mailbox.getUser())
            .name(mailbox.getName())
            .uidValidity(mailbox.getUidValidity())
            .serializedACL(getSerializedACL(mailbox))
            .build();
    }

    public MailboxDataTransferObject extractMailboxDataTransferObject(MailboxListener.Event event) {
        if (event instanceof EventFactory.MailboxAware) {
            return convertMailboxDataTransferObject(((EventFactory.MailboxAware) event).getMailbox());
        } else {
            throw new RuntimeException("Unsupported event class : " + event.getClass().getCanonicalName());
        }
    }

    private String getSerializedACL(Mailbox mailbox) {
        try {
            return MailboxACLJsonConverter.toJson(mailbox.getACL());
        } catch (JsonProcessingException e) {
            return "{\"entries\":{}}";
        }
    }

}
