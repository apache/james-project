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

package org.apache.james.mailbox.cassandra.mail.eventsourcing.acl;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.ReactiveSubscriber;
import org.apache.james.mailbox.cassandra.mail.CassandraUserMailboxRightsDAO;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class UserRightsDAOSubscriber implements ReactiveSubscriber {
    private final CassandraUserMailboxRightsDAO userRightsDAO;

    public UserRightsDAOSubscriber(CassandraUserMailboxRightsDAO userRightsDAO) {
        this.userRightsDAO = userRightsDAO;
    }

    @Override
    public Publisher<Void> handleReactive(EventWithState eventWithState) {
        Event event = eventWithState.event();
        if (event instanceof ACLUpdated) {
            ACLUpdated aclUpdated = (ACLUpdated) event;
            return userRightsDAO.update(aclUpdated.mailboxId(), aclUpdated.getAclDiff());
        }
        return Mono.empty();
    }
}
