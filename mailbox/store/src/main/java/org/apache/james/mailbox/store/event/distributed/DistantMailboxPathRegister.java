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

package org.apache.james.mailbox.store.event.distributed;

import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.publisher.Topic;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

public class DistantMailboxPathRegister implements MailboxPathRegister {
    private static final int DEFAULT_MAX_RETRY = 1000;
    private final ConcurrentHashMap<MailboxPath, Long> registeredMailboxPathCount;
    private final DistantMailboxPathRegisterMapper mapper;
    private final Topic topic;
    private final Timer timer;
    private final int maxRetry;
    private final long schedulerPeriodInS;

    public DistantMailboxPathRegister(DistantMailboxPathRegisterMapper mapper, long schedulerPeriodInS) {
        this(mapper, DEFAULT_MAX_RETRY, schedulerPeriodInS);
    }

    public DistantMailboxPathRegister(DistantMailboxPathRegisterMapper mapper, int maxRetry, long schedulerPeriodInS) {
        this.maxRetry = maxRetry;
        this.mapper = mapper;
        this.registeredMailboxPathCount = new ConcurrentHashMap<>();
        this.topic = new Topic(UUID.randomUUID().toString());
        this.timer = new Timer();
        this.schedulerPeriodInS = schedulerPeriodInS;
    }

    @PostConstruct
    public void init() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Set<Map.Entry<MailboxPath, Long>> snapshot = ImmutableSet.copyOf(registeredMailboxPathCount.entrySet());
                for(Map.Entry<MailboxPath, Long> entry : snapshot) {
                    if (entry.getValue() > 0) {
                        mapper.doRegister(entry.getKey(), topic);
                    }
                }
            }
        }, 0L, schedulerPeriodInS * 1000);
    }

    @PreDestroy
    public void destroy() {
        timer.cancel();
        timer.purge();
    }

    @Override
    public Set<Topic> getTopics(MailboxPath mailboxPath) {
        return mapper.getTopics(mailboxPath);
    }

    @Override
    public Topic getLocalTopic() {
        return topic;
    }

    @Override
    public void register(MailboxPath path) throws MailboxException {
        int count = 0;
        boolean success = false;
        while (count < maxRetry && !success) {
            count ++;
            success = tryRegister(path);
        }
        if (!success) {
            throw new MailboxException(maxRetry + " reached while trying to register " + path);
        }
    }

    @Override
    public void unregister(MailboxPath path) throws MailboxException {
        int count = 0;
        boolean success = false;
        while (count < maxRetry && !success) {
            count ++;
            success = tryUnregister(path);
        }
        if (!success) {
            throw new MailboxException(maxRetry + " reached while trying to unregister " + path);
        }
    }

    @Override
    public void doCompleteUnRegister(MailboxPath mailboxPath) {
        registeredMailboxPathCount.remove(mailboxPath);
        mapper.doUnRegister(mailboxPath, topic);
    }

    @Override
    public void doRename(MailboxPath oldPath, MailboxPath newPath) throws MailboxException {
        try {
            int count = 0;
            boolean success = false;
            while (count < maxRetry && !success) {
                success = tryCountTransfer(oldPath, newPath);
            }
            if (!success) {
                throw new MailboxException(maxRetry + " reached while trying to rename " + oldPath + " in " + newPath);
            }
        } finally {
            doCompleteUnRegister(oldPath);
        }
    }

    private boolean tryCountTransfer(MailboxPath oldPath, MailboxPath newPath) throws MailboxException {
        Long oldEntry = registeredMailboxPathCount.get(oldPath);
        if (oldEntry == null) {
            throw new MailboxException("Renamed entry does not exists");
        }
        Long entry = registeredMailboxPathCount.get(newPath);
        if (entry != null) {
            return registeredMailboxPathCount.replace(newPath, entry, oldEntry + entry);
        } else {
            if (registeredMailboxPathCount.putIfAbsent(newPath, oldEntry) == null) {
                mapper.doRegister(newPath, topic);
                return true;
            }
            return false;
        }
    }

    private boolean tryRegister(MailboxPath path) {
        Long entry = registeredMailboxPathCount.get(path);
        Long newEntry = entry;
        if (newEntry == null) {
            newEntry = 0L;
        }
        newEntry++;
        if (entry != null) {
            return registeredMailboxPathCount.replace(path, entry, newEntry);
        } else {
            if (registeredMailboxPathCount.putIfAbsent(path, newEntry) == null) {
                mapper.doRegister(path, topic);
                return true;
            }
            return false;
        }
    }

    private boolean tryUnregister(MailboxPath path) throws MailboxException {
        Long entry = registeredMailboxPathCount.get(path);
        Long newEntry = entry;
        if (newEntry == null) {
            throw new MailboxException("Removing a non registered mailboxPath");
        }
        newEntry--;
        if (newEntry != 0) {
            return registeredMailboxPathCount.replace(path, entry, newEntry);
        } else {
            if (registeredMailboxPathCount.remove(path, entry)) {
                mapper.doUnRegister(path, topic);
                return true;
            }
            return false;
        }
    }

    @VisibleForTesting
    ConcurrentHashMap<MailboxPath, Long> getRegisteredMailboxPathCount() {
        return registeredMailboxPathCount;
    }
}
