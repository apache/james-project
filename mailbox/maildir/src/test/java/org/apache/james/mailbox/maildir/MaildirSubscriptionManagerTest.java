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
package org.apache.james.mailbox.maildir;

import java.io.File;

import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.SubscriptionManagerContract;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class MaildirSubscriptionManagerTest implements SubscriptionManagerContract {

    @TempDir
    File tmpFolder;

    private SubscriptionManager subscriptionManager;

    @Override
    public SubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }

    @BeforeEach
    void setUp() {
        MaildirStore store = new MaildirStore(tmpFolder + "/Maildir/%domain/%user", new JVMMailboxPathLocker());
        MaildirMailboxSessionMapperFactory factory = new MaildirMailboxSessionMapperFactory(store);

        subscriptionManager = new StoreSubscriptionManager(factory);
    }

    @AfterEach
    void tearDown() throws SubscriptionException {
        subscriptionManager.unsubscribe(SESSION, MAILBOX1);
        subscriptionManager.unsubscribe(SESSION, MAILBOX2);
    }
}
