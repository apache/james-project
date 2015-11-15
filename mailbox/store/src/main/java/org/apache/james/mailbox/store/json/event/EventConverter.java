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

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.json.event.dto.EventDataTransferObject;
import org.apache.james.mailbox.store.json.event.dto.EventType;
import org.apache.james.mailbox.store.json.event.dto.MailboxDataTransferObject;
import org.apache.james.mailbox.store.json.event.dto.MailboxPathDataTransferObject;
import org.apache.james.mailbox.store.json.event.dto.MailboxSessionDataTransferObject;
import org.apache.james.mailbox.store.json.event.dto.MessageMetaDataDataTransferObject;
import org.apache.james.mailbox.store.json.event.dto.UpdatedFlagsDataTransferObject;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class EventConverter<Id extends MailboxId> {

    private static final Logger LOG = LoggerFactory.getLogger(EventConverter.class);

    private final EventFactory<Id> eventFactory;
    private final MailboxConverter<Id> mailboxConverter;

    public EventConverter(MailboxConverter<Id> mailboxConverter) {
        this.eventFactory = new EventFactory<Id>();
        this.mailboxConverter = mailboxConverter;
    }

    public EventDataTransferObject convertToDataTransferObject(MailboxListener.Event event) throws Exception {
        MailboxDataTransferObject mailboxDataTransferObject = mailboxConverter.extractMailboxDataTransferObject(event);
        if (event instanceof MailboxListener.Added) {
            return constructMeteDataHoldingEventProxy(EventType.ADDED,
                event.getSession(),
                mailboxDataTransferObject,
                ((MailboxListener.Added) event).getUids(),
                (MailboxListener.Added) event);
        } else if (event instanceof MailboxListener.Expunged) {
            return constructMeteDataHoldingEventProxy(EventType.DELETED,
                event.getSession(), mailboxDataTransferObject,
                ((MailboxListener.Expunged) event).getUids(),
                (MailboxListener.Expunged) event);
        } else if (event instanceof MailboxListener.FlagsUpdated) {
            return constructFalgsUpdatedProxy(event.getSession(),
                mailboxDataTransferObject,
                ((MailboxListener.FlagsUpdated) event).getUids(),
                ((MailboxListener.FlagsUpdated) event).getUpdatedFlags());
        } else if ( event instanceof MailboxListener.MailboxRenamed) {
            return constructMailboxRenamedProxy(event.getSession(),
                mailboxDataTransferObject,
                event.getMailboxPath());
        } else if (event instanceof MailboxListener.MailboxDeletion) {
            return constructMailboxEventProxy(EventType.MAILBOX_DELETED,
                event.getSession(),
                mailboxDataTransferObject);
        } else if (event instanceof MailboxListener.MailboxAdded) {
            return constructMailboxEventProxy(EventType.MAILBOX_ADDED,
                event.getSession(),
                mailboxDataTransferObject);
        } else {
            throw new Exception("You are trying to serialize an event that can't be serialized");
        }
    }

    public MailboxListener.Event retrieveEvent(EventDataTransferObject eventDataTransferObject) throws Exception {
        Mailbox<Id> mailbox = mailboxConverter.retrieveMailbox(eventDataTransferObject.getMailbox());
        switch (eventDataTransferObject.getType()) {
            case ADDED:
                return eventFactory.added(eventDataTransferObject.getSession().getMailboxSession(),
                    retrieveMetadata(eventDataTransferObject.getMetaDataProxyMap()),
                    mailbox);
            case DELETED:
                return eventFactory.expunged(eventDataTransferObject.getSession().getMailboxSession(),
                    retrieveMetadata(eventDataTransferObject.getMetaDataProxyMap()),
                    mailbox);
            case FLAGS:
                return eventFactory.flagsUpdated(eventDataTransferObject.getSession().getMailboxSession(),
                    eventDataTransferObject.getUids(),
                    mailbox,
                    retrieveUpdatedFlags(eventDataTransferObject.getUpdatedFlags()));
            case MAILBOX_ADDED:
                return eventFactory.mailboxAdded(eventDataTransferObject.getSession().getMailboxSession(), mailbox);
            case MAILBOX_DELETED:
                return eventFactory.mailboxDeleted(eventDataTransferObject.getSession().getMailboxSession(), mailbox);
            case MAILBOX_RENAMED:
                return eventFactory.mailboxRenamed(eventDataTransferObject.getSession().getMailboxSession(),
                    eventDataTransferObject.getFrom().getPath(),
                    mailbox);
            default:
                throw new Exception("Can not deserialize unknown event");
        }
    }

    private EventDataTransferObject constructMailboxEventProxy(EventType eventType,
                                                               MailboxSession mailboxSession,
                                                               MailboxDataTransferObject mailboxIntermediate) {
        return EventDataTransferObject.builder()
            .type(eventType)
            .session(new MailboxSessionDataTransferObject(mailboxSession))
            .mailbox(mailboxIntermediate)
            .build();
    }

    private EventDataTransferObject constructMailboxRenamedProxy(MailboxSession mailboxSession,
                                                                 MailboxDataTransferObject mailboxIntermediate,
                                                                 MailboxPath from) {
        return EventDataTransferObject.builder()
            .type(EventType.MAILBOX_RENAMED)
            .session(new MailboxSessionDataTransferObject(mailboxSession))
            .mailbox(mailboxIntermediate)
            .from(new MailboxPathDataTransferObject(from))
            .build();
    }

    private EventDataTransferObject constructFalgsUpdatedProxy(MailboxSession session,
                                                               MailboxDataTransferObject mailboxIntermediate,
                                                               List<Long> uids,
                                                               List<UpdatedFlags> updatedFlagsList) {
        ArrayList<UpdatedFlagsDataTransferObject> updatedFlagsDataTransferObjects = new ArrayList<UpdatedFlagsDataTransferObject>();
        for(UpdatedFlags updatedFlags : updatedFlagsList) {
            updatedFlagsDataTransferObjects.add(new UpdatedFlagsDataTransferObject(updatedFlags));
        }
        return EventDataTransferObject.builder()
            .type(EventType.FLAGS)
            .session(new MailboxSessionDataTransferObject(session))
            .mailbox(mailboxIntermediate)
            .uids(uids)
            .updatedFlags(updatedFlagsDataTransferObjects)
            .build();
    }

    private EventDataTransferObject constructMeteDataHoldingEventProxy(EventType eventType,
                                                                       MailboxSession mailboxSession,
                                                                       MailboxDataTransferObject mailboxIntermediate,
                                                                       List<Long> uids,
                                                                       MailboxListener.MetaDataHoldingEvent event) {
        HashMap<Long, MessageMetaDataDataTransferObject> metaDataProxyMap = new HashMap<Long, MessageMetaDataDataTransferObject>();
        for(Long uid : uids) {
            metaDataProxyMap.put(uid, new MessageMetaDataDataTransferObject(
                event.getMetaData(uid)
            ));
        }
        return EventDataTransferObject.builder()
            .type(eventType)
            .session(new MailboxSessionDataTransferObject(mailboxSession))
            .mailbox(mailboxIntermediate)
            .uids(uids)
            .metaData(metaDataProxyMap)
            .build();
    }

    private SortedMap<Long, MessageMetaData> retrieveMetadata(Map<Long, MessageMetaDataDataTransferObject> metaDataProxyMap) {
        if(metaDataProxyMap != null) {
            TreeMap<Long, MessageMetaData> result = new TreeMap<Long, MessageMetaData>();
            Set<Map.Entry<Long, MessageMetaDataDataTransferObject>> entrySet = metaDataProxyMap.entrySet();
            for (Map.Entry<Long, MessageMetaDataDataTransferObject> entry : entrySet) {
                result.put(entry.getKey(), entry.getValue().getMetadata());
            }
            return result;
        } else {
            LOG.warn("Event serialization problem : No metadata");
            return null;
        }
    }

    private List<UpdatedFlags> retrieveUpdatedFlags(List<UpdatedFlagsDataTransferObject> updatedFlagsDataTransferObject) {
        List<UpdatedFlags> result = new ArrayList<UpdatedFlags>();
        for(UpdatedFlagsDataTransferObject proxy : updatedFlagsDataTransferObject) {
            result.add(proxy.retrieveUpdatedFlags());
        }
        return result;
    }

}
