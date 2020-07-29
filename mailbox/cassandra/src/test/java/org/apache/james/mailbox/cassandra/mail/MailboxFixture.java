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

package org.apache.james.mailbox.cassandra.mail;

import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;

public interface MailboxFixture {
    Username USER = Username.of("user");
    Username OTHER_USER = Username.of("other");

    CassandraId INBOX_ID = CassandraId.timeBased();
    CassandraId OUTBOX_ID = CassandraId.timeBased();
    CassandraId otherMailboxId = CassandraId.timeBased();
    MailboxPath USER_INBOX_MAILBOXPATH = MailboxPath.forUser(USER, "INBOX");
    MailboxPath USER_OUTBOX_MAILBOXPATH = MailboxPath.forUser(USER, "OUTBOX");
    MailboxPath OTHER_USER_MAILBOXPATH = MailboxPath.forUser(OTHER_USER, "INBOX");
    CassandraIdAndPath INBOX_ID_AND_PATH = new CassandraIdAndPath(INBOX_ID, USER_INBOX_MAILBOXPATH);

    Mailbox MAILBOX_1 = new Mailbox(USER_INBOX_MAILBOXPATH, UidValidity.of(42), INBOX_ID);
    Mailbox MAILBOX_2 = new Mailbox(USER_OUTBOX_MAILBOXPATH, UidValidity.of(43), OUTBOX_ID);
    Mailbox MAILBOX_3 = new Mailbox(OTHER_USER_MAILBOXPATH, UidValidity.of(44), otherMailboxId);
}
