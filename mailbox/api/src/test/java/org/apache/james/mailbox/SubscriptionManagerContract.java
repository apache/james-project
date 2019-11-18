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
import org.junit.jupiter.api.Test;

public interface SubscriptionManagerContract {

    Username USER1 = Username.of("test");
    String MAILBOX1 = "test1";
    String MAILBOX2 = "test2";
    MailboxSession SESSION = MailboxSessionUtil.create(USER1);

    SubscriptionManager getSubscriptionManager();
    
    @Test
    default void user1ShouldNotHaveAnySubscriptionByDefault() throws SubscriptionException {
        assertThat(getSubscriptionManager().subscriptions(SESSION)).isEmpty();
    }
    
    @Test
    default void user1ShouldBeAbleToSubscribeOneMailbox() throws SubscriptionException {
        getSubscriptionManager().subscribe(SESSION, MAILBOX1);

        assertThat(getSubscriptionManager().subscriptions(SESSION)).containsExactly(MAILBOX1);
    }

    @Test
    default void subscribeShouldBeIdempotent() throws SubscriptionException {
        getSubscriptionManager().subscribe(SESSION, MAILBOX1);
        getSubscriptionManager().subscribe(SESSION, MAILBOX1);
        
        assertThat(getSubscriptionManager().subscriptions(SESSION)).containsExactly(MAILBOX1);
    }
    
    @Test
    default void user1ShouldBeAbleToSubscribeTwoMailbox() throws SubscriptionException {
        getSubscriptionManager().subscribe(SESSION, MAILBOX1);
        getSubscriptionManager().subscribe(SESSION, MAILBOX2);
        
        assertThat(getSubscriptionManager().subscriptions(SESSION)).containsOnly(MAILBOX1, MAILBOX2);
    }
    
    @Test
    default void user1ShouldBeAbleToUnsubscribeOneMailbox() throws SubscriptionException {
        getSubscriptionManager().subscribe(SESSION, MAILBOX1);
        getSubscriptionManager().subscribe(SESSION, MAILBOX2);

        getSubscriptionManager().unsubscribe(SESSION, MAILBOX1);
        
        assertThat(getSubscriptionManager().subscriptions(SESSION)).containsExactly(MAILBOX2);
    }
    
    @Test
    default void unsubscribeShouldBeIdempotent() throws SubscriptionException {
        getSubscriptionManager().subscribe(SESSION, MAILBOX1);
        getSubscriptionManager().subscribe(SESSION, MAILBOX2);
        getSubscriptionManager().unsubscribe(SESSION, MAILBOX1);
        getSubscriptionManager().unsubscribe(SESSION, MAILBOX1);
        
        assertThat(getSubscriptionManager().subscriptions(SESSION)).containsExactly(MAILBOX2);
    }
    
}
