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

package org.apache.james.mailbox.jpa.mail;

import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.mail.MailboxMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TransactionalMailboxMapper implements MailboxMapper {
    private final JPAMailboxMapper wrapped;

    public TransactionalMailboxMapper(JPAMailboxMapper wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public Mono<Mailbox> create(MailboxPath mailboxPath, UidValidity uidValidity) {
        return wrapped.executeReactive(wrapped.create(mailboxPath, uidValidity));
    }

    @Override
    public Mono<MailboxId> rename(Mailbox mailbox) {
        return wrapped.executeReactive(wrapped.rename(mailbox));
    }

    @Override
    public Mono<Void> delete(Mailbox mailbox) {
        return wrapped.executeReactive(wrapped.delete(mailbox));
    }

    @Override
    public Mono<Mailbox> findMailboxByPath(MailboxPath mailboxPath) {
        return wrapped.findMailboxByPath(mailboxPath);
    }

    @Override
    public Mono<Mailbox> findMailboxById(MailboxId mailboxId) {
        return wrapped.findMailboxById(mailboxId);
    }

    @Override
    public Flux<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query) {
        return wrapped.findMailboxWithPathLike(query);
    }

    @Override
    public Mono<Boolean> hasChildren(Mailbox mailbox, char delimiter) {
        return wrapped.hasChildren(mailbox, delimiter);
    }

    @Override
    public Mono<ACLDiff> updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) {
        return wrapped.updateACL(mailbox, mailboxACLCommand);
    }

    @Override
    public Mono<ACLDiff> setACL(Mailbox mailbox, MailboxACL mailboxACL) {
        return wrapped.setACL(mailbox, mailboxACL);
    }

    @Override
    public Flux<Mailbox> list() {
        return wrapped.list();
    }

    @Override
    public Flux<Mailbox> findNonPersonalMailboxes(Username userName, Right right) {
        return wrapped.findNonPersonalMailboxes(userName, right);
    }

}
