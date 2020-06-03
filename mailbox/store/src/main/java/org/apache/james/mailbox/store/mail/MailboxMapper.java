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
package org.apache.james.mailbox.store.mail;

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.transaction.Mapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Mapper for {@link Mailbox} actions. A {@link MailboxMapper} has a lifecycle from the start of a request 
 * to the end of the request.
 *
 */
public interface MailboxMapper extends Mapper {

    /**
     * Create a {@link Mailbox} with the given {@link MailboxPath} and uid to the underlying storage
     */
    Mono<Mailbox> create(MailboxPath mailboxPath, UidValidity uidValidity);

    /**
     * Rename the given {@link Mailbox} to the underlying storage
     */
    Mono<MailboxId> rename(Mailbox mailbox);
    
    /**
     * Delete the given {@link Mailbox} from the underlying storage
     */
    Mono<Void> delete(Mailbox mailbox);

  
    /**
     * Return the {@link Mailbox} for the given name
     */
    Mono<Mailbox> findMailboxByPath(MailboxPath mailboxName);

    default Mono<Boolean> pathExists(MailboxPath mailboxName) {
        return findMailboxByPath(mailboxName).hasElement();
    }

    /**
     * Return the {@link Mailbox} for the given name
     */
    Mono<Mailbox> findMailboxById(MailboxId mailboxId);

    /**
     * Return a List of {@link Mailbox} for the given userName and matching the right
     */
    Flux<Mailbox> findNonPersonalMailboxes(Username userName, Right right);

    /**
     * Return a List of {@link Mailbox} which name is like the given name
     */
    Flux<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query);

    /**
     * Return if the given {@link Mailbox} has children
     * 
     * @param mailbox not null
     * @param delimiter path delimiter
     * @return true when the mailbox has children, false otherwise
     */
    Mono<Boolean> hasChildren(Mailbox mailbox, char delimiter);

    /**
     * Update the ACL of the stored mailbox.
     * @param mailbox Mailbox for whom we want to update ACL
     * @param mailboxACLCommand Update to perform
     */
    Mono<ACLDiff> updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand);

    /**
     * Reset the ACL of the stored mailbox.
     *  @param mailbox Mailbox for whom we want to update ACL
     * @param mailboxACL New value of the ACL for this mailbox
     * @return
     */
    Mono<ACLDiff> setACL(Mailbox mailbox, MailboxACL mailboxACL);

    /**
     * Return a unmodifable {@link List} of all {@link Mailbox}
     */
    Flux<Mailbox> list();
}
