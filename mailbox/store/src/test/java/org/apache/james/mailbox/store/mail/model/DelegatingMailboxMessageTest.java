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

package org.apache.james.mailbox.store.mail.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.store.MessageBuilder;
import org.junit.Test;

public class DelegatingMailboxMessageTest {
    
    @Test
    public void testShouldReturnPositiveWhenFirstGreaterThanSecond()
            throws Exception {
        MailboxMessage one = buildMessage(100);
        MailboxMessage two = buildMessage(99);
        assertTrue(one.compareTo(two) > 0);
    }

    private MailboxMessage buildMessage(int uid) throws Exception {
        MessageBuilder builder = new MessageBuilder();
        builder.uid = MessageUid.of(uid);
        return builder.build();
    }

    @Test
    public void testShouldReturnNegativeWhenFirstLessThanSecond()
            throws Exception {
        MailboxMessage one = buildMessage(98);
        MailboxMessage two = buildMessage(99);
        assertTrue(one.compareTo(two) < 0);
    }

    @Test
    public void testShouldReturnZeroWhenFirstEqualsSecond() throws Exception {
        MailboxMessage one = buildMessage(90);
        MailboxMessage two = buildMessage(90);
        assertEquals(0, one.compareTo(two));
    }
}
