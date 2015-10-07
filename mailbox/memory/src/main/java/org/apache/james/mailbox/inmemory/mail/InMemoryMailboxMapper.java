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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;

public class InMemoryMailboxMapper implements MailboxMapper<InMemoryId> {
    
    private static final int INITIAL_SIZE = 128;
    private final Map<InMemoryId, Mailbox<InMemoryId>> mailboxesById;
    private final static AtomicLong IDS = new AtomicLong();

    public InMemoryMailboxMapper() {
        mailboxesById = new ConcurrentHashMap<InMemoryId, Mailbox<InMemoryId>>(INITIAL_SIZE);
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#delete(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    public void delete(Mailbox<InMemoryId> mailbox) throws MailboxException {
        mailboxesById.remove(mailbox.getMailboxId());
    }

    public void deleteAll() throws MailboxException {
        mailboxesById.clear();
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#findMailboxByPath(org.apache.james.mailbox.model.MailboxPath)
     */
    public synchronized Mailbox<InMemoryId> findMailboxByPath(MailboxPath path) throws MailboxException, MailboxNotFoundException {
        Mailbox<InMemoryId> result = null;
        for (final Mailbox<InMemoryId> mailbox:mailboxesById.values()) {
            MailboxPath mp = new MailboxPath(mailbox.getNamespace(), mailbox.getUser(), mailbox.getName());
            if (mp.equals(path)) {
                result = mailbox;
                break;
            }
        }
        if (result == null) {
            throw new MailboxNotFoundException(path);
        } else {
            return result;
        }
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#findMailboxWithPathLike(org.apache.james.mailbox.model.MailboxPath)
     */
    public List<Mailbox<InMemoryId>> findMailboxWithPathLike(MailboxPath path) throws MailboxException {
        final String regex = path.getName().replace("%", ".*");
        List<Mailbox<InMemoryId>> results = new ArrayList<Mailbox<InMemoryId>>();
        for (final Mailbox<InMemoryId> mailbox:mailboxesById.values()) {
            if (mailboxMatchesRegex(mailbox, path, regex)) {
                results.add(mailbox);
            }
        }
        return results;
    }

    private boolean mailboxMatchesRegex(Mailbox<InMemoryId> mailbox, MailboxPath path, String regex) {
        return mailbox.getNamespace().equals(path.getNamespace())
            && mailbox.getUser().equals(path.getUser())
            && mailbox.getName().matches(regex);
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#save(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    public void save(Mailbox<InMemoryId> mailbox) throws MailboxException {
        InMemoryId id = mailbox.getMailboxId();
        if (id == null) {
            id = InMemoryId.of(IDS.incrementAndGet());
            ((SimpleMailbox<InMemoryId>) mailbox).setMailboxId(id);
        }
        mailboxesById.put(id, mailbox);
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
    public boolean hasChildren(Mailbox<InMemoryId> mailbox, char delimiter) throws MailboxException,
            MailboxNotFoundException {
        String mailboxName = mailbox.getName() + delimiter;
        for (final Mailbox<InMemoryId> box:mailboxesById.values()) {
            if (box.getName().startsWith(mailboxName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#list()
     */
    public List<Mailbox<InMemoryId>> list() throws MailboxException {
        return new ArrayList<Mailbox<InMemoryId>>(mailboxesById.values());
    }

    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public void updateACL(Mailbox<InMemoryId> mailbox, MailboxACL.MailboxACLCommand mailboxACLCommand) throws MailboxException{
        mailbox.setACL(mailbox.getACL().apply(mailboxACLCommand));
    }
}
