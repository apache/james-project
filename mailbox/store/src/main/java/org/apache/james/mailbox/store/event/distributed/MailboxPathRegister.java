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

package org.apache.james.mailbox.store.event.distributed;

import java.util.Set;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.publisher.Topic;

/**
 * The TopicDispatcher allow you to :
 *
 *  - know to which queues you will need to send an event
 *  - get the topic this James instance will be pooling
 */
public interface MailboxPathRegister {

    /**
     * Given a MailboxPath, we want to get the different topics we need to send the event to.
     *
     * @param mailboxPath MailboxPath
     * @return List of topics concerned by this MailboxPath
     */
    Set<Topic> getTopics(MailboxPath mailboxPath);

    /**
     * Get the topic this James instance will consume
     *
     * @return The topic this James instance will consume
     */
    Topic getLocalTopic();

    void register(MailboxPath path) throws MailboxException;

    void unregister(MailboxPath path) throws MailboxException;

    void doCompleteUnRegister(MailboxPath mailboxPath);

    void doRename(MailboxPath oldPath, MailboxPath newPath) throws MailboxException;
}