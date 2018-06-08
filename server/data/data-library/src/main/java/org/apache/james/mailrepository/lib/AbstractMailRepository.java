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

package org.apache.james.mailrepository.lib;

import java.io.IOException;
import java.util.Collection;

import javax.mail.MessagingException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.repository.api.Initializable;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

/**
 * This class represent an AbstractMailRepository. All MailRepositories should
 * extend this class.
 */
public abstract class AbstractMailRepository implements MailRepository, Configurable, Initializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMailRepository.class);

    /**
     * Whether 'deep debugging' is turned on.
     */
    protected static final boolean DEEP_DEBUG = false;

    /**
     * A lock used to control access to repository elements, locking access
     * based on the key
     */
    private final Lock lock = new Lock();

    @Override
    public void configure(HierarchicalConfiguration configuration) throws ConfigurationException {
        doConfigure(configuration);
    }

    protected void doConfigure(HierarchicalConfiguration config) throws ConfigurationException {

    }

    /**
     * Releases a lock on a message identified the key
     * 
     * @param key
     *            the key of the message to be unlocked
     * 
     * @return true if successfully released the lock, false otherwise
     */
    @Override
    public boolean unlock(MailKey key) {
        return lock.unlock(key);
    }

    /**
     * Obtains a lock on a message identified by key
     * 
     * @param key
     *            the key of the message to be locked
     * 
     * @return true if successfully obtained the lock, false otherwise
     */
    @Override
    public boolean lock(MailKey key) {
        return lock.lock(key);
    }

    @Override
    public MailKey store(Mail mc) throws MessagingException {
        boolean wasLocked = true;
        MailKey key = MailKey.forMail(mc);
        try {
            synchronized (this) {
                wasLocked = lock.isLocked(key);
                if (!wasLocked) {
                    // If it wasn't locked, we want a lock during the store
                    lock(key);
                }
            }
            internalStore(mc);
            return key;
        } catch (MessagingException e) {
            LOGGER.error("Exception caught while storing mail {}", key, e);
            throw e;
        } catch (Exception e) {
            LOGGER.error("Exception caught while storing mail {}", key, e);
            throw new MessagingException("Exception caught while storing mail " + key, e);
        } finally {
            if (!wasLocked) {
                // If it wasn't locked, we need to unlock now
                unlock(key);
                synchronized (this) {
                    notify();
                }
            }
        }
    }

    protected abstract void internalStore(Mail mc) throws MessagingException, IOException;

    @Override
    public void remove(Mail mail) throws MessagingException {
        remove(MailKey.forMail(mail));
    }

    @Override
    public void remove(Collection<Mail> mails) throws MessagingException {
        for (Mail mail : mails) {
            remove(mail);
        }
    }

    @Override
    public void remove(MailKey key) throws MessagingException {
        if (lock(key)) {
            try {
                internalRemove(key);
            } finally {
                unlock(key);
            }
        } else {
            throw new MessagingException("Cannot lock " + key + " to remove it");
        }
    }

    protected abstract void internalRemove(MailKey key) throws MessagingException;

    @Override
    public long size() throws MessagingException {
        return Iterators.size(list());
    }

    @Override
    public void removeAll() throws MessagingException {
        ImmutableList.copyOf(list())
            .forEach(Throwing.<MailKey>consumer(this::remove).sneakyThrow());
    }
}
