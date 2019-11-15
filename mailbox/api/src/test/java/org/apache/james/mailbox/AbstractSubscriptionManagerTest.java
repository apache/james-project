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
package org.apache.james.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Abstract base class to test {@link SubscriptionManager} implementations
 * 
 *
 */
public abstract class AbstractSubscriptionManagerTest {

    private static final Username USER1 = Username.of("test");
    private static final String MAILBOX1 = "test1";
    private static final String MAILBOX2 = "test2";
    private SubscriptionManager manager;
    private MailboxSession session;

    protected abstract SubscriptionManager createSubscriptionManager();

    @BeforeEach
    void setup() {
        manager = createSubscriptionManager();
        session = MailboxSessionUtil.create(USER1);
        manager.startProcessingRequest(session);
    }

    @AfterEach
    void teardown() throws SubscriptionException {
        manager.unsubscribe(session, MAILBOX1);
        manager.unsubscribe(session, MAILBOX2);
        manager.endProcessingRequest(session);
    }
    
    @Test
    void user1ShouldNotHaveAnySubscriptionByDefault() throws SubscriptionException {
        assertThat(manager.subscriptions(session)).isEmpty();
    }
    
    
    @Test
    void user1ShouldBeAbleToSubscribeOneMailbox() throws SubscriptionException {
        manager.subscribe(session, MAILBOX1);

        assertThat(manager.subscriptions(session)).containsExactly(MAILBOX1);
    }

    @Test
    void subscribeShouldBeIdempotent() throws SubscriptionException {
        manager.subscribe(session, MAILBOX1);
        manager.subscribe(session, MAILBOX1);
        
        assertThat(manager.subscriptions(session)).containsExactly(MAILBOX1);
    }
    
    @Test
    void user1ShouldBeAbleToSubscribeTwoMailbox() throws SubscriptionException {
        manager.subscribe(session, MAILBOX1);
        manager.subscribe(session, MAILBOX2);
        
        assertThat(manager.subscriptions(session)).containsOnly(MAILBOX1, MAILBOX2);
    }
    
    @Test
    void user1ShouldBeAbleToUnsubscribeOneMailbox() throws SubscriptionException {
        manager.subscribe(session, MAILBOX1);
        manager.subscribe(session, MAILBOX2);

        manager.unsubscribe(session, MAILBOX1);
        
        assertThat(manager.subscriptions(session)).containsExactly(MAILBOX2);
    }
    
    @Test
    void unsubscribeShouldBeIdempotent() throws SubscriptionException {
        manager.subscribe(session, MAILBOX1);
        manager.subscribe(session, MAILBOX2);
        manager.unsubscribe(session, MAILBOX1);
        manager.unsubscribe(session, MAILBOX1);
        
        assertThat(manager.subscriptions(session)).containsExactly(MAILBOX2);
    }
    
}
