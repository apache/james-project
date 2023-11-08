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

package org.apache.james.mailbox.jpa.mail.dao;

import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PostgresMailboxDAO {
    Mono<Mailbox> create(MailboxPath mailboxPath, UidValidity uidValidity);

    Mono<MailboxId> rename(Mailbox mailbox);

    Mono<Void> delete(MailboxId mailboxId);

    Mono<Mailbox> findMailboxByPath(MailboxPath mailboxPath);

    Mono<Mailbox> findMailboxById(MailboxId id);

    Flux<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query);

    Mono<Boolean> hasChildren(Mailbox mailbox, char delimiter);

    Flux<Mailbox> getAll();
}
