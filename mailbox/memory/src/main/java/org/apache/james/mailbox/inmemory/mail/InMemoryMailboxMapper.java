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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Objects;

public class InMemoryMailboxMapper implements MailboxMapper {
    
    private static final int INITIAL_SIZE = 128;
    private final ConcurrentHashMap<MailboxPath, Mailbox> mailboxesByPath;
    private final AtomicLong mailboxIdGenerator = new AtomicLong();

    public InMemoryMailboxMapper() {
        mailboxesByPath = new ConcurrentHashMap<>(INITIAL_SIZE);
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#delete(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    public void delete(Mailbox mailbox) throws MailboxException {
        mailboxesByPath.remove(mailbox.generateAssociatedPath());
    }

    public void deleteAll() throws MailboxException {
        mailboxesByPath.clear();
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#findMailboxByPath(org.apache.james.mailbox.model.MailboxPath)
     */
    public synchronized Mailbox findMailboxByPath(MailboxPath path) throws MailboxException {
        Mailbox result = mailboxesByPath.get(path);
        if (result == null) {
            throw new MailboxNotFoundException(path);
        } else {
            return new SimpleMailbox(result);
        }
    }

    public synchronized Mailbox findMailboxById(MailboxId id) throws MailboxException {
        InMemoryId mailboxId = (InMemoryId)id;
        for (Mailbox mailbox: mailboxesByPath.values()) {
            if (mailbox.getMailboxId().equals(mailboxId)) {
                return new SimpleMailbox(mailbox);
            }
        }
        throw new MailboxNotFoundException(mailboxId.serialize());
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#findMailboxWithPathLike(org.apache.james.mailbox.model.MailboxPath)
     */
    public List<Mailbox> findMailboxWithPathLike(MailboxPath path) throws MailboxException {
        final String regex = path.getName().replace("%", ".*");
        return mailboxesByPath.values()
            .stream()
            .filter(mailbox -> mailboxMatchesRegex(mailbox, path, regex))
            .map(SimpleMailbox::new)
            .collect(Guavate.toImmutableList());
    }

    private boolean mailboxMatchesRegex(Mailbox mailbox, MailboxPath path, String regex) {
        return Objects.equal(mailbox.getNamespace(), path.getNamespace())
            && Objects.equal(mailbox.getUser(), path.getUser())
            && mailbox.getName().matches(regex);
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#save(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    public MailboxId save(Mailbox mailbox) throws MailboxException {
        InMemoryId id = (InMemoryId) mailbox.getMailboxId();
        if (id == null) {
            id = InMemoryId.of(mailboxIdGenerator.incrementAndGet());
            mailbox.setMailboxId(id);
        } else {
            try {
                Mailbox mailboxWithPreviousName = findMailboxById(id);
                mailboxesByPath.remove(mailboxWithPreviousName.generateAssociatedPath());
            } catch (MailboxNotFoundException e) {
                // No need to remove the previous mailbox
            }
        }
        Mailbox previousMailbox = mailboxesByPath.putIfAbsent(mailbox.generateAssociatedPath(), mailbox);
        if (previousMailbox != null) {
            throw new MailboxExistsException(mailbox.getName());
        }
        return mailbox.getMailboxId();
    }

    /**
     * Do nothing
     */
    public void endRequest() {
        // Do nothing
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#hasChildren(org.apache.james.mailbox.store.mail.model.Mailbox, char)
     */
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

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#list()
     */
    public List<Mailbox> list() throws MailboxException {
        return new ArrayList<>(mailboxesByPath.values());
    }

    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public void updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) throws MailboxException{
        mailboxesByPath.get(mailbox.generateAssociatedPath()).setACL(mailbox.getACL().apply(mailboxACLCommand));
    }

    @Override
    public void setACL(Mailbox mailbox, MailboxACL mailboxACL) throws MailboxException {
        mailboxesByPath.get(mailbox.generateAssociatedPath()).setACL(mailboxACL);
    }
}
