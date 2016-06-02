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

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.store.mail.AnnotationMapper;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;

public class InMemoryAnnotationMapper implements AnnotationMapper {
    private final InMemoryId mailboxId;
    private final Table<InMemoryId, String, String> mailboxesAnnotations;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public InMemoryAnnotationMapper(InMemoryId mailboxId) {
        this.mailboxId = mailboxId;
        mailboxesAnnotations = HashBasedTable.create();
    }

    @Override
    public void endRequest() {

    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    private Iterable<MailboxAnnotation> retrieveAllAnnotations(InMemoryId maiboxId) {
        lock.readLock().lock();
        try {
            return Iterables.transform(
                mailboxesAnnotations.row(maiboxId).entrySet(), 
                new Function<Map.Entry<String, String>, MailboxAnnotation>() {
                    public MailboxAnnotation apply(Entry<String, String> input) {
                        return MailboxAnnotation.newInstance(input.getKey(), input.getValue());
                    }
                });
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public List<MailboxAnnotation> getAllAnnotations() {
        return ImmutableList.copyOf(retrieveAllAnnotations(mailboxId));
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeys(final Set<String> keys) {
        return ImmutableList.copyOf(
            Iterables.filter(retrieveAllAnnotations(mailboxId),
                new Predicate<MailboxAnnotation>() {
                    public boolean apply(MailboxAnnotation input) {
                        return keys.contains(input.getKey());
                    }
            }));
    }

    @Override
    public void insertAnnotation(MailboxAnnotation mailboxAnnotation) {
        lock.writeLock().lock();
        try {
            mailboxesAnnotations.put(mailboxId, mailboxAnnotation.getKey(), mailboxAnnotation.getValue().get());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteAnnotation(String key) {
        lock.writeLock().lock();
        try {
            mailboxesAnnotations.remove(mailboxId, key);
        } finally {
            lock.writeLock().unlock();
        }
    }

}
