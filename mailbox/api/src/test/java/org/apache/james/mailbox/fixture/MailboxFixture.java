/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.fixture;

import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxPath;

public interface MailboxFixture {
    Username ALICE = Username.of("alice");
    Username BOB = Username.of("bob");
    Username CEDRIC = Username.of("cedric");

    MailboxPath INBOX_ALICE = MailboxPath.inbox(ALICE);
    MailboxPath OUTBOX_ALICE = MailboxPath.forUser(ALICE, "OUTBOX");
    MailboxPath SENT_ALICE = MailboxPath.forUser(ALICE, "SENT");
    MailboxPath INBOX_BOB = MailboxPath.inbox(BOB);
    MailboxPath BOB_2 = MailboxPath.forUser(BOB, "box2");
}
