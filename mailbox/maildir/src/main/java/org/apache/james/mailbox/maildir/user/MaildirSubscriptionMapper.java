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
package org.apache.james.mailbox.maildir.user;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.maildir.MaildirStore;
import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.model.Subscription;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class MaildirSubscriptionMapper extends NonTransactionalMapper implements SubscriptionMapper {

    private static final String FILE_SUBSCRIPTION = "subscriptions";
    private final MaildirStore store;
    
    public MaildirSubscriptionMapper(MaildirStore store) {
        this.store = store;
    }
    
    @Override
    public void delete(Subscription subscription) throws SubscriptionException {
        // TODO: we need some kind of file locking here
        Set<String> subscriptionNames = readSubscriptionsForUser(subscription.getUser());
        Set<String> newSubscriptions = Sets.difference(subscriptionNames, ImmutableSet.of(subscription.getMailbox()));
        boolean changed = subscriptionNames.size() != newSubscriptions.size();
        if (changed) {
            try {
                writeSubscriptions(new File(store.userRoot(subscription.getUser())), newSubscriptions);
            } catch (IOException e) {
                throw new SubscriptionException(e);
            }
        }
    }

    @Override
    public List<Subscription> findSubscriptionsForUser(Username user) throws SubscriptionException {
        Set<String> subscriptionNames = readSubscriptionsForUser(user);
        return subscriptionNames.stream()
            .map(subscription -> new Subscription(user, subscription))
            .collect(Guavate.toImmutableList());
    }

    @Override
    public void save(Subscription subscription) throws SubscriptionException {
        // TODO: we need some kind of file locking here
        Set<String> subscriptionNames = readSubscriptionsForUser(subscription.getUser());
        Set<String> newSubscriptions = ImmutableSet.<String>builder()
            .addAll(subscriptionNames)
            .add(subscription.getMailbox())
            .build();
        boolean changed = subscriptionNames.size() != newSubscriptions.size();
        if (changed) {
            try {
                writeSubscriptions(new File(store.userRoot(subscription.getUser())), newSubscriptions);
            } catch (IOException e) {
                throw new SubscriptionException(e);
            }
        }
    }
    
    /**
     * Read the subscriptions for a particular user
     * @param user The user to get the subscriptions for
     * @return A Set of names of subscribed mailboxes of the user
     */
    private Set<String> readSubscriptionsForUser(Username user) throws SubscriptionException {
        File userRoot = new File(store.userRoot(user));
        try {
            return readSubscriptions(userRoot);
        } catch (IOException e) {
            throw new SubscriptionException(e);
        }
    }

    /**
     * Read the names of the mailboxes which are subscribed from the specified folder
     * @param mailboxFolder The folder which contains the subscription file
     * @return A Set of names of subscribed mailboxes
     */
    private Set<String> readSubscriptions(File mailboxFolder) throws IOException {
        File subscriptionFile = new File(mailboxFolder, FILE_SUBSCRIPTION);
        if (!subscriptionFile.exists()) {
            return ImmutableSet.of();
        }
        try (FileReader fileReader = new FileReader(subscriptionFile)) {
            try (BufferedReader reader = new BufferedReader(fileReader)) {
                return reader.lines()
                    .filter(Predicate.not(Strings::isNullOrEmpty))
                    .collect(Guavate.toImmutableSet());
            }
        }
    }
    
    /**
     * Write the set of mailbox names into the subscriptions file in the specified folder
     * @param mailboxFolder Folder which contains the subscriptions file
     * @param subscriptions Set of names of subscribed mailboxes
     */
    private void writeSubscriptions(File mailboxFolder, Set<String> subscriptions) throws IOException {
        List<String> sortedSubscriptions = new ArrayList<>(subscriptions);
        Collections.sort(sortedSubscriptions);
        if (!mailboxFolder.exists()) {
            if (!mailboxFolder.mkdirs()) {
                throw new IOException("Could not create folder " + mailboxFolder);
            }
        }

        File subscriptionFile = new File(mailboxFolder, FILE_SUBSCRIPTION);
        if (!subscriptionFile.exists()) {
            if (!subscriptionFile.createNewFile()) {
                throw new IOException("Could not create file " + subscriptionFile);
            }
        }

        try (FileWriter fileWriter = new FileWriter(subscriptionFile)) {
            try (PrintWriter writer = new PrintWriter(fileWriter)) {
                sortedSubscriptions.forEach(writer::println);
            }
        }
    }

}
