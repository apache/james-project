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

package org.apache.james.jmap.memory.projections;

import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.jmap.api.projections.EmailQueryViewContract;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.BeforeEach;

public class MemoryEmailQueryViewTest implements EmailQueryViewContract {
    private MemoryEmailQueryView testee;

    @BeforeEach
    void setUp() {
        testee = new MemoryEmailQueryView();
    }

    @Override
    public EmailQueryView testee() {
        return testee;
    }

    @Override
    public MailboxId mailboxId1() {
        return TestId.of(0);
    }

    @Override
    public MessageId messageId1() {
        return TestMessageId.of(1);
    }

    @Override
    public MessageId messageId2() {
        return TestMessageId.of(2);
    }

    @Override
    public MessageId messageId3() {
        return TestMessageId.of(3);
    }

    @Override
    public MessageId messageId4() {
        return TestMessageId.of(4);
    }
}
