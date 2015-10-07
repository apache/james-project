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
package org.apache.james.mailbox.cassandra.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.james.mailbox.cassandra.CassandraClusterSingleton;
import org.apache.james.mailbox.store.user.model.Subscription;
import org.apache.james.mailbox.store.user.model.impl.SimpleSubscription;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Session;

/**
 * Runs tests for SubscriptionMapper.
 * 
 */
public class CassandraSubscriptionMapperTest {

    private static final Logger LOG = LoggerFactory.getLogger(CassandraSubscriptionMapperTest.class);
    private static final CassandraClusterSingleton CLUSTER = CassandraClusterSingleton.build();
    private static Session session;
    private static CassandraSubscriptionMapper mapper;
    private static Map<String, List<SimpleSubscription>> subscriptionList;
    private static final int USERS = 5;
    private static final int MAILBOX_NO = 5;

    @Before
    public void setUp() throws Exception {
        CLUSTER.ensureAllTables();
        CLUSTER.clearAllTables();
        session = CLUSTER.getConf();
        mapper = new CassandraSubscriptionMapper(session);
        fillSubscriptionList();
    }

    private static void fillSubscriptionList() {
        LOG.info("Creating subscription list");
        SimpleSubscription subscription;
        String user, mailbox;
        subscriptionList = new HashMap<String, List<SimpleSubscription>>();
        for (int i = 0; i < USERS; i++) {
            user = "user" + i;
            final List<SimpleSubscription> mailboxes = new ArrayList<SimpleSubscription>();
            subscriptionList.put(user, mailboxes);

            for (int j = 0; j < MAILBOX_NO; j++) {
                if (j == 0) {
                    mailbox = "INBOX";
                } else {
                    mailbox = "BOX" + j;
                }
                if ((i % 2 == 0) && (j > 0)) {
                    continue;
                }
                subscription = new SimpleSubscription(user, mailbox);
                mailboxes.add(subscription);
                mapper.save(subscription);
                LOG.info("Adding subscription " + subscription);
            }
        }
    }

    /**
     * Test of findMailboxSubscriptionForUser method, of class
     * CassandraSubscriptionMapper.
     */
    @Test
    public void testFindMailboxSubscriptionForUser() throws Exception {
        LOG.info("findMailboxSubscriptionForUser");

        final SimpleSubscription fake1 = new SimpleSubscription("user1", "FAKEBOX");
        final SimpleSubscription fake2 = new SimpleSubscription("fakeUser", "INBOX");

        for (String user : subscriptionList.keySet()) {
            LOG.info("Searching for all subscriptions for user:{}", user);
            for (SimpleSubscription subscription : subscriptionList.get(user)) {
                final Subscription result = mapper.findMailboxSubscriptionForUser(user, subscription.getMailbox());
                assertEquals(subscription.getMailbox(), result.getMailbox());
                assertEquals(subscription.getUser(), result.getUser());
            }
        }
        assertNull(mapper.findMailboxSubscriptionForUser(fake1.getUser(), fake1.getMailbox()));
        assertNull(mapper.findMailboxSubscriptionForUser(fake2.getUser(), fake2.getMailbox()));
    }

    /**
     * Test of save method, of class CassandraSubscriptionMapper.
     */
    @Test
    public void testSave() throws Exception {
        LOG.info("save");
        final List<SimpleSubscription> subscriptions = mapper.list();
        for (String user : subscriptionList.keySet()) {
            for (Subscription subscription : subscriptionList.get(user)) {
                assertTrue(containSubscription(subscriptions, subscription));
            }
        }
    }

    /**
     * Test of findSubscriptionsForUser method, of class
     * CassandraSubscriptionMapper.
     */
    @Test
    public void testFindSubscriptionsForUser() throws Exception {
        LOG.info("findSubscriptionsForUser");
        final SimpleSubscription fake2 = new SimpleSubscription("fakeUser", "INBOX");
        for (String user : subscriptionList.keySet()) {
            LOG.info("Searching for all subscriptions for user: " + user);
            final List<Subscription> found = mapper.findSubscriptionsForUser(user);
            assertEquals(subscriptionList.get(user).size(), found.size());
            // TODO: patch Subscription to implement equals
            // assertTrue(subscriptionList.get(user).containsAll(foundSubscriptions));
            // assertTrue(foundSubscriptions.containsAll(subscriptionList.get(user)));
            // assertFalse(foundSubscriptions.contains(fake1));
            // assertFalse(foundSubscriptions.contains(fake2));
        }
        // TODO: check what value we should return in case of no subscriptions:
        // null or empty list
        assertEquals(mapper.findSubscriptionsForUser(fake2.getMailbox()).size(), 0);

    }

    /**
     * Test of delete method, of class CassandraSubscriptionMapper.
     */
    @Test
    public void testDelete() throws Exception {
        LOG.info("delete");
        for (String user : subscriptionList.keySet()) {
            LOG.info("Deleting subscriptions for user: " + user);
            for (SimpleSubscription subscription : subscriptionList.get(user)) {
                LOG.info("Deleting subscription : " + subscription);
                mapper.delete(subscription);
                assertFalse(containSubscription(mapper.list(), subscription));
            }
        }
        fillSubscriptionList();
    }

    private boolean containSubscription(List<SimpleSubscription> subscriptions, Subscription subscription) {
        for (SimpleSubscription s : subscriptions) {
            if (subscription.getMailbox().equals(s.getMailbox()) && subscription.getUser().equals(s.getUser())) {
                return true;
            }
        }
        return false;
    }

}
