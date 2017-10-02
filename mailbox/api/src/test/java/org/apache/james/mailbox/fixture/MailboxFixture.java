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

import org.apache.james.mailbox.model.MailboxPath;

public class MailboxFixture {
    public static final String USER = "user";
    public static final String OTHER_USER = "otheruser";
    public static final String THIRD_USER = "thirdUser";

    public static final MailboxPath MAILBOX_PATH1 = MailboxPath.forUser(USER, "INBOX");
    public static final MailboxPath MAILBOX_PATH2 = MailboxPath.forUser(USER, "OUTBOX");
    public static final MailboxPath MAILBOX_PATH3 = MailboxPath.forUser(USER, "SENT");
    public static final MailboxPath MAILBOX_PATH4 = MailboxPath.forUser(OTHER_USER, "INBOX");
}
