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

package org.apache.james.mailrepository.api;

import java.util.Collection;
import java.util.Iterator;

import jakarta.mail.MessagingException;

import org.apache.mailet.Mail;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

/**
 * Interface for a Repository to store Mails.
 */
public interface MailRepository {

    /**
     * @return Number of mails stored in that repository
     */
    long size() throws MessagingException;

    default Publisher<Long> sizeReactive() {
        return Mono.fromCallable(this::size);
    }

    /**
     * Stores a message in this repository.
     * 
     * @param mc
     *            the mail message to store
     */
    MailKey store(Mail mc) throws MessagingException;

    /**
     * List string keys of messages in repository.
     * 
     * @return an <code>Iterator</code> over the list of keys in the repository
     * 
     */
    Iterator<MailKey> list() throws MessagingException;

    /**
     * Retrieves a message given a key. At the moment, keys can be obtained from
     * list() in superinterface Store.Repository
     * 
     * @param key
     *            the key of the message to retrieve
     * @return the mail corresponding to this key, null if none exists
     */
    Mail retrieve(MailKey key) throws MessagingException;

    /**
     * Removes a message identified by key.
     * 
     * @param key
     *            the key of the message to be removed from the repository
     */
    void remove(MailKey key) throws MessagingException;

    /**
     * Removes some messages identified by keys.
     */
    default void remove(Collection<MailKey> keys) throws MessagingException {
        for (MailKey key: keys) {
            remove(key);
        }
    }

    /**
     * Removes all mails from this repository
     *
     * @throws MessagingException
     */
    void removeAll() throws MessagingException;

}
