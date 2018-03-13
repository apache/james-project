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

package org.apache.james.app.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.container.spring.context.JamesServerApplicationContext;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.assertj.core.api.Condition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JamesSpringContextTest {
    private static final Condition<MailboxListener> QUOTA_UPDATER_LISTENER = new Condition<MailboxListener>() {
        @Override
        public boolean matches(MailboxListener mailboxListener) {
            return mailboxListener instanceof ListeningCurrentQuotaUpdater;
        }
    };
    private static final int ONCE = 1;
    private JamesServerApplicationContext context;

    @Before
    public void setup() {
        context = new JamesServerApplicationContext(new String[] { "META-INF/org/apache/james/spring-server.xml" });
        context.registerShutdownHook();
        context.start();
    }

    @After
    public void tearDown() {
        context.stop();
        context.destroy();
    }

    @Test
    public void springShouldLoadAndAddOnlyOneQuotaUpdaterListener() {
        DefaultDelegatingMailboxListener mailboxListener = context.getBean(DefaultDelegatingMailboxListener.class);

        assertThat(mailboxListener.getRegistry().getGlobalListeners())
            .hasSize(2)
            .areExactly(ONCE, QUOTA_UPDATER_LISTENER);
    }

}
