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
package org.apache.james.mailbox.inmemory.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.mail.MailboxMapper;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class InMemoryMailboxMapper implements MailboxMapper {
    
    private static final int INITIAL_SIZE = 128;
    private final ConcurrentHashMap<MailboxPath, Mailbox> mailboxesByPath;
    private final AtomicLong mailboxIdGenerator = new AtomicLong();

    public InMemoryMailboxMapper() {
        mailboxesByPath = new ConcurrentHashMap<>(INITIAL_SIZE);
    }

    @Override
    public void delete(Mailbox mailbox) throws MailboxException {
        mailboxesByPath.remove(mailbox.generateAssociatedPath());
    }

    public void deleteAll() throws MailboxException {
        mailboxesByPath.clear();
    }

    @Override
    public synchronized Mailbox findMailboxByPath(MailboxPath path) throws MailboxException {
        Mailbox result = mailboxesByPath.get(path);
        if (result == null) {
            throw new MailboxNotFoundException(path);
        } else {
            return new Mailbox(result);
        }
    }

    @Override
    public synchronized Mailbox findMailboxById(MailboxId id) throws MailboxException {
        InMemoryId mailboxId = (InMemoryId) id;
        for (Mailbox mailbox: mailboxesByPath.values()) {
            if (mailbox.getMailboxId().equals(mailboxId)) {
                return new Mailbox(mailbox);
            }
        }
        throw new MailboxNotFoundException(mailboxId);
    }

    @Override
    public List<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query) {
        return mailboxesByPath.values()
            .stream()
            .filter(query::matches)
            .map(Mailbox::new)
            .collect(Guavate.toImmutableList());
    }

    @Override
    public Mailbox create(MailboxPath mailboxPath, UidValidity uidValidity) throws MailboxException {
        InMemoryId id = InMemoryId.of(mailboxIdGenerator.incrementAndGet());
        Mailbox mailbox = new Mailbox(mailboxPath, uidValidity, id);

        saveMailbox(mailbox);

        return mailbox;
    }

    @Override
    public MailboxId rename(Mailbox mailbox) throws MailboxException {
        Preconditions.checkNotNull(mailbox.getMailboxId(), "A mailbox we want to rename should have a defined mailboxId");

        InMemoryId id = (InMemoryId) mailbox.getMailboxId();
        Mailbox mailboxWithPreviousName = findMailboxById(id);

        saveMailbox(mailbox);
        mailboxesByPath.remove(mailboxWithPreviousName.generateAssociatedPath());

        return mailbox.getMailboxId();
    }

    private void saveMailbox(Mailbox mailbox) throws MailboxException {
        Mailbox previousMailbox = mailboxesByPath.putIfAbsent(mailbox.generateAssociatedPath(), mailbox);
        if (previousMailbox != null) {
            throw new MailboxExistsException(mailbox.getName());
        }
    }

    @Override
    public void endRequest() {
        // Do nothing
    }

    @Override
    public boolean hasChildren(Mailbox mailbox, char delimiter) throws MailboxException {
        String mailboxName = mailbox.getName() + delimiter;
        return mailboxesByPath.values()
            .stream()
            .anyMatch(box -> belongsToSameUser(mailbox, box) && box.getName().startsWith(mailboxName));
    }

    private boolean belongsToSameUser(Mailbox mailbox, Mailbox otherMailbox) {
        return Objects.equal(mailbox.getNamespace(), otherMailbox.getNamespace())
            && Objects.equal(mailbox.getUser(), otherMailbox.getUser());
    }

    @Override
    public List<Mailbox> list() throws MailboxException {
        return new ArrayList<>(mailboxesByPath.values());
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public ACLDiff updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) throws MailboxException {
        MailboxACL oldACL = mailbox.getACL();
        MailboxACL newACL = mailbox.getACL().apply(mailboxACLCommand);
        mailboxesByPath.get(mailbox.generateAssociatedPath()).setACL(newACL);
        return ACLDiff.computeDiff(oldACL, newACL);
    }

    @Override
    public ACLDiff setACL(Mailbox mailbox, MailboxACL mailboxACL) throws MailboxException {
        MailboxACL oldMailboxAcl = mailbox.getACL();
        mailboxesByPath.get(mailbox.generateAssociatedPath()).setACL(mailboxACL);
        return ACLDiff.computeDiff(oldMailboxAcl, mailboxACL);
    }

    @Override
    public List<Mailbox> findNonPersonalMailboxes(Username userName, Right right) throws MailboxException {
        return mailboxesByPath.values()
            .stream()
            .filter(mailbox -> hasRightOn(mailbox, userName, right))
            .collect(Guavate.toImmutableList());
    }

    private Boolean hasRightOn(Mailbox mailbox, Username userName, Right right) {
        return Optional.ofNullable(
            mailbox.getACL()
                .ofPositiveNameType(NameType.user)
                .get(MailboxACL.EntryKey.createUserEntryKey(userName)))
            .map(rights -> rights.contains(right))
            .orElse(false);
    }
}
