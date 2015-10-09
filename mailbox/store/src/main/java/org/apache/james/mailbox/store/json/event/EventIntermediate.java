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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class EventIntermediate {
    @JsonProperty("a")
    public EventType eventType;
    @JsonProperty("b")
    public MailboxIntermediate mailboxIntermediate;
    @JsonProperty("c")
    public MailboxSessionIntermediate sessionProxy;
    @JsonProperty("d")
    public List<Long> uids;
    @JsonProperty("e")
    public Map<Long, MessageMetaDataIntermediate> metaDataProxyMap;
    @JsonProperty("f")
    public List<UpdatedFlagsIntermediate> updatedFlagsIntermediate;
    @JsonProperty("g")
    public MailboxPathIntermediate from;

    private static final Logger LOG = LoggerFactory.getLogger(EventIntermediate.class);

    public EventIntermediate() {

    }

    @JsonIgnore
    public MailboxIntermediate getMailboxIntermediate() {
        return mailboxIntermediate;
    }

    @JsonIgnore
    public MailboxSession getSessionDeserialized() {
        return sessionProxy.getMailboxSession();
    }

    @JsonIgnore
    public List<Long> getUids() {
        return uids;
    }

    @JsonIgnore
    public SortedMap<Long, MessageMetaData> getMetatdataDeserialized() {
        if(metaDataProxyMap != null) {
            TreeMap<Long, MessageMetaData> result = new TreeMap<Long, MessageMetaData>();
            Set<Map.Entry<Long, MessageMetaDataIntermediate>> entrySet = metaDataProxyMap.entrySet();
            for (Map.Entry<Long, MessageMetaDataIntermediate> entry : entrySet) {
                result.put(entry.getKey(), entry.getValue().getMetadata());
            }
            return result;
        } else {
            LOG.warn("Event serialization problem : No metadata");
            return null;
        }
    }

    @JsonIgnore
    public List<UpdatedFlags> getFlagsDeserialized() {
        List<UpdatedFlags> result = new ArrayList<UpdatedFlags>();
        for(UpdatedFlagsIntermediate proxy : updatedFlagsIntermediate) {
            result.add(proxy.getUpdatedFlags());
        }
        return result;
    }

    @JsonIgnore
    public MailboxPath getFrom() {
        return from.getPath();
    }

    @JsonIgnore
    public EventType getEventType() {
        return eventType;
    }

    public EventIntermediate(MailboxListener.Event event, MailboxIntermediate mailboxIntermediate) throws Exception {
        if (event instanceof MailboxListener.Added) {
            constructEventAddedProxy(EventType.ADDED, event.getSession(), mailboxIntermediate, ((MailboxListener.Added) event).getUids(), (MailboxListener.Added) event);
        } else if (event instanceof MailboxListener.Expunged) {
            constructEventDeletedProxy(EventType.DELETED, event.getSession(), mailboxIntermediate, ((MailboxListener.Expunged) event).getUids(), (MailboxListener.Expunged) event);
        } else if (event instanceof MailboxListener.FlagsUpdated) {
            constructFalgsUpdatedProxy(event.getSession(), mailboxIntermediate, ((MailboxListener.FlagsUpdated) event).getUids(), ((MailboxListener.FlagsUpdated) event).getUpdatedFlags());
        } else if ( event instanceof MailboxListener.MailboxRenamed) {
            constructMailboxRenamedProxy(event.getSession(), mailboxIntermediate, event.getMailboxPath());
        } else if (event instanceof MailboxListener.MailboxDeletion) {
            constructMailboxEventProxy(EventType.MAILBOX_DELETED, event.getSession(), mailboxIntermediate);
        } else if (event instanceof MailboxListener.MailboxAdded) {
            constructMailboxEventProxy(EventType.MAILBOX_ADDED, event.getSession(), mailboxIntermediate);
        } else {
            throw new Exception("You are trying to serialize an event that can't be serialized");
        }
    }

    protected Mailbox retrieveUnderlyingMailbox(MailboxListener.Event event) {
        if (event instanceof EventFactory.MailboxAware) {
            return ((EventFactory.MailboxAware) event).getMailbox();
        } else {
            throw new RuntimeException("Unsupported event class : " + event.getClass().getCanonicalName());
        }
    }

    private void constructMailboxEventProxy(EventType eventType,
                                            MailboxSession mailboxSession,
                                            MailboxIntermediate mailboxIntermediate) {
        this.eventType = eventType;
        this.sessionProxy = new MailboxSessionIntermediate(mailboxSession);
        this.mailboxIntermediate = mailboxIntermediate;
    }

    private void constructMailboxRenamedProxy(MailboxSession mailboxSession,
                                              MailboxIntermediate mailboxIntermediate,
                                              MailboxPath from) {
        this.eventType = EventType.MAILBOX_RENAMED;
        this.sessionProxy = new MailboxSessionIntermediate(mailboxSession);
        this.mailboxIntermediate = mailboxIntermediate;
        this.from = new MailboxPathIntermediate(from);
    }

    private void constructFalgsUpdatedProxy(MailboxSession session,
                                            MailboxIntermediate mailboxIntermediate,
                                            List<Long> uids,
                                            List<UpdatedFlags> updatedFlagsList) {
        this.eventType = EventType.FLAGS;
        this.sessionProxy = new MailboxSessionIntermediate(session);
        this.mailboxIntermediate = mailboxIntermediate;
        this.uids = uids;
        this.updatedFlagsIntermediate = new ArrayList<UpdatedFlagsIntermediate>();
        for(UpdatedFlags updatedFlags : updatedFlagsList) {
            updatedFlagsIntermediate.add(new UpdatedFlagsIntermediate(updatedFlags));
        }
    }

    private void constructEventAddedProxy(EventType eventType,
                                          MailboxSession mailboxSession,
                                          MailboxIntermediate mailboxIntermediate,
                                          List<Long> uids,
                                          MailboxListener.Added event) {
        this.eventType = eventType;
        this.sessionProxy = new MailboxSessionIntermediate(mailboxSession);
        this.mailboxIntermediate = mailboxIntermediate;
        this.metaDataProxyMap = new HashMap<Long, MessageMetaDataIntermediate>();
        this.uids = uids;
        for(Long uid : uids) {
            this.metaDataProxyMap.put(uid, new MessageMetaDataIntermediate(
                event.getMetaData(uid)
            ));
        }
    }

    private void constructEventDeletedProxy(EventType eventType,
                                            MailboxSession mailboxSession,
                                            MailboxIntermediate mailboxIntermediate,
                                            List<Long> uids,
                                            MailboxListener.Expunged event) {
        this.eventType = eventType;
        this.sessionProxy = new MailboxSessionIntermediate(mailboxSession);
        this.mailboxIntermediate = mailboxIntermediate;
        this.metaDataProxyMap = new HashMap<Long, MessageMetaDataIntermediate>();
        this.uids = uids;
        for (Long uid : uids) {
            this.metaDataProxyMap.put(uid, new MessageMetaDataIntermediate(event.getMetaData(uid)));
        }
    }

}