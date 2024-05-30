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

import static org.apache.james.util.ReactorUtils.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE;
import static reactor.core.scheduler.Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * {@link MailboxPathLocker} implementation which helps to synchronize the access the
 * same MailboxPath. This is done using one {@link ReentrantReadWriteLock}
 * per {@link MailboxPath} so its only usable in a single JVM.
 */
public final class JVMMailboxPathLocker implements MailboxPathLocker {
    private final ConcurrentHashMap<MailboxPath, StampedLock> paths = new ConcurrentHashMap<>();

    private static final int TTL_SECONDS = 60;
    private static final boolean DAEMON = true;
    public static final Scheduler LOCKER_WRAPPER = Schedulers.newBoundedElastic(DEFAULT_BOUNDED_ELASTIC_SIZE, DEFAULT_BOUNDED_ELASTIC_QUEUESIZE,
        "jvm-path-locker", TTL_SECONDS, DAEMON);

    @Override
    public <T> T executeWithLock(MailboxPath path, LockAwareExecution<T> execution, LockType writeLock) throws MailboxException {
        Lock lock = lock(path, writeLock);
        try {
            return execution.execute();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T> Publisher<T> executeReactiveWithLockReactive(MailboxPath path, Publisher<T> execution, LockType lockType) {
        StampedLock stampedLock = getStampedLock(path);
        switch (lockType) {
            case Read:
                return Flux.using(stampedLock::readLock,
                        stamp -> Mono.from(execution),
                        stampedLock::unlockRead)
                    .subscribeOn(LOCKER_WRAPPER);
            case Write:
                return Flux.using(stampedLock::writeLock,
                        stamp -> Mono.from(execution),
                        stampedLock::unlockWrite)
                    .subscribeOn(LOCKER_WRAPPER);
            default:
                throw new RuntimeException("Lock type not supported");
        }
    }

    private Lock lock(MailboxPath path, LockType lockType) {
        StampedLock stampedLock = getStampedLock(path);
        Lock lock = getLock(stampedLock, lockType);
        lock.lock();
        return lock;
    }

    private StampedLock getStampedLock(MailboxPath path) {
        StampedLock stampedLock = paths.get(path);
        if (stampedLock == null) {
            stampedLock = new StampedLock();
            StampedLock storedLock = paths.putIfAbsent(path, stampedLock);
            if (storedLock != null) {
                stampedLock = storedLock;
            }
        }
        return stampedLock;
    }

    private Lock getLock(StampedLock lock, LockType lockType) {
        switch (lockType) {
            case Write:
                return lock.asWriteLock();
            case Read:
                return lock.asReadLock();
            default:
                throw new NotImplementedException("Unsupported lock tuype " + lockType);
        }
    }
}
