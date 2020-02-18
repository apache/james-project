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

import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.transaction.Mapper;

public class TransactionalMailboxMapper implements MailboxMapper {
    private final JPAMailboxMapper wrapped;

    public TransactionalMailboxMapper(JPAMailboxMapper wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void endRequest() {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public MailboxId create(Mailbox mailbox) throws MailboxException {
        return wrapped.execute(() -> wrapped.create(mailbox));
    }

    @Override
    public Mailbox create(MailboxPath mailboxPath, long uidValidity) throws MailboxException {
        return wrapped.execute(() -> wrapped.create(mailboxPath, uidValidity));
    }

    @Override
    public MailboxId rename(Mailbox mailbox) throws MailboxException {
        return wrapped.execute(() -> wrapped.rename(mailbox));
    }

    @Override
    public void delete(Mailbox mailbox) throws MailboxException {
        wrapped.execute(Mapper.toTransaction(() -> wrapped.delete(mailbox)));
    }

    @Override
    public Mailbox findMailboxByPath(MailboxPath mailboxPath) throws MailboxException, MailboxNotFoundException {
        return wrapped.findMailboxByPath(mailboxPath);
    }

    @Override
    public Mailbox findMailboxById(MailboxId mailboxId) throws MailboxException, MailboxNotFoundException {
        return wrapped.findMailboxById(mailboxId);
    }

    @Override
    public List<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query) throws MailboxException {
        return wrapped.findMailboxWithPathLike(query);
    }

    @Override
    public boolean hasChildren(Mailbox mailbox, char delimiter) throws MailboxException, MailboxNotFoundException {
        return wrapped.hasChildren(mailbox, delimiter);
    }

    @Override
    public ACLDiff updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) throws MailboxException {
        return wrapped.updateACL(mailbox, mailboxACLCommand);
    }

    @Override
    public ACLDiff setACL(Mailbox mailbox, MailboxACL mailboxACL) throws MailboxException {
        return wrapped.setACL(mailbox, mailboxACL);
    }

    @Override
    public List<Mailbox> list() throws MailboxException {
        return wrapped.list();
    }

    @Override
    public List<Mailbox> findNonPersonalMailboxes(Username userName, Right right) throws MailboxException {
        return wrapped.findNonPersonalMailboxes(userName, right);
    }

}
