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

package org.apache.james.mailbox.store;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;

/**
 * {@link MailboxPathLocker} implementation which helps to synchronize the access the
 * same MailboxPath. This is done using one {@link ReentrantReadWriteLock}
 * per {@link MailboxPath} so its only usable in a single JVM.
 */
public final class JVMMailboxPathLocker implements MailboxPathLocker {
    private final ConcurrentHashMap<MailboxPath, ReadWriteLock> paths = new ConcurrentHashMap<>();

    @Override
    public <T> T executeWithLock(MailboxPath path, LockAwareExecution<T> execution, LockType writeLock) throws MailboxException {
        try {
            lock(path, writeLock);
            return execution.execute();
        } finally {
            unlock(path, writeLock);
        }
    }

    private void lock(MailboxPath path, LockType lockType) {
        ReadWriteLock lock = paths.get(path);
        if (lock == null) {
            lock = new ReentrantReadWriteLock();
            ReadWriteLock storedLock = paths.putIfAbsent(path, lock);
            if (storedLock != null) {
                lock = storedLock;
            }
        }
        getLock(lock, lockType).lock();
    }

    private void unlock(MailboxPath path, LockType lockType) {
        ReadWriteLock lock = paths.get(path);

        if (lock != null) {
            getLock(lock, lockType).unlock();
        }
    }

    private Lock getLock(ReadWriteLock lock, LockType lockType) {
        switch (lockType) {
            case Write:
                return lock.writeLock();
            case Read:
                return lock.readLock();
            default:
                throw new NotImplementedException("Unsupported lock tuype " + lockType);
        }
    }
}
