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

package org.apache.james.mailbox;

import java.io.Serializable;
import java.util.List;

import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.UpdatedFlags;


/**
 * Listens to <code>Mailbox</code> events.<br>
 * Note that listeners may be removed asynchronously.
 */
public interface MailboxListener {

    enum ListenerType {
        ONCE,
        EACH_NODE,
        MAILBOX
    }

    enum ExecutionMode {
        SYNCHRONOUS,
        ASYNCHRONOUS
    }

    ListenerType getType();

    ExecutionMode getExecutionMode();

    /**
     * Informs this listener about the given event.
     * 
     * @param event
     *            not null
     */
    void event(Event event);

    /**
     * A mailbox event.
     */
    abstract class MailboxEvent implements Event, Serializable {
        private final MailboxSession session;
        private final MailboxPath path;

        public MailboxEvent(MailboxSession session, MailboxPath path) {
            this.session = session;
            this.path = path;
        }

        /**
         * Gets the {@link MailboxSession} in which's context the {@link MailboxEvent}
         * happened
         * 
         * @return session
         */
        public MailboxSession getSession() {
            return session;
        }

        /**
         * Return the path of the Mailbox this event belongs to.
         * 
         * @return path
         */
        public MailboxPath getMailboxPath() {
            return path;
        }
    }

    /**
     * Indicates that mailbox has been deleted.
     */
    class MailboxDeletion extends MailboxEvent {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public MailboxDeletion(MailboxSession session, MailboxPath path) {
            super(session, path);
        }
    }

    /**
     * Indicates that a mailbox has been Added.
     */
    class MailboxAdded extends MailboxEvent {
        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public MailboxAdded(MailboxSession session, MailboxPath path) {
            super(session, path);
        }
    }

    /**
     * Indicates that a mailbox has been renamed.
     */
    abstract class MailboxRenamed extends MailboxEvent {
        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public MailboxRenamed(MailboxSession session, MailboxPath path) {
            super(session, path);
        }

        /**
         * Gets the new name for this mailbox.
         * 
         * @return name, not null
         */
        public abstract MailboxPath getNewPath();
    }


    /**
     * A mailbox event related to updated ACL
     */
    class MailboxACLUpdated extends MailboxEvent {
        private final ACLDiff aclDiff;
        private static final long serialVersionUID = 1L;

        public MailboxACLUpdated(MailboxSession session, MailboxPath path, ACLDiff aclDiff) {
            super(session, path);
            this.aclDiff = aclDiff;
        }

        public ACLDiff getAclDiff() {
            return aclDiff;
        }

    }
    
    /**
     * A mailbox event related to a message.
     */
    abstract class MessageEvent extends MailboxEvent {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public MessageEvent(MailboxSession session, MailboxPath path) {
            super(session, path);
        }

        /**
         * Gets the message UIDs for the subject of this event.
         * 
         * @return message uids
         */
        public abstract List<MessageUid> getUids();
    }

    abstract class MetaDataHoldingEvent extends MessageEvent {

        public MetaDataHoldingEvent(MailboxSession session, MailboxPath path) {
            super(session, path);
        }

        /**
         * Return the flags which were set for the afected message
         *
         * @return flags
         */
        public abstract MessageMetaData getMetaData(MessageUid uid);

    }

    abstract class Expunged extends MetaDataHoldingEvent {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public Expunged(MailboxSession session, MailboxPath path) {
            super(session, path);
        }
        
        /**
         * Return the flags which were set for the added message
         * 
         * @return flags
         */
        public abstract MessageMetaData getMetaData(MessageUid uid);
    }

    /**
     * A mailbox event related to updated flags
     */
    abstract class FlagsUpdated extends MessageEvent {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public FlagsUpdated(MailboxSession session, MailboxPath path) {
            super(session, path);
        }

        public abstract List<UpdatedFlags> getUpdatedFlags();
    }

    /**
     * A mailbox event related to added message
     */
    abstract class Added extends MetaDataHoldingEvent {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public Added(MailboxSession session, MailboxPath path) {
            super(session, path);
        }
        
        /**
         * Return the flags which were set for the added message
         * 
         * @return flags
         */
        public abstract MessageMetaData getMetaData(MessageUid uid);
        
    }
    
}
