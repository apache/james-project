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

package org.apache.james.imap.api;

import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.mailbox.MailboxSession;

public class ImapSessionUtils {

    public static final String MAILBOX_USER_ATTRIBUTE_SESSION_KEY = "org.apache.james.api.imap.MAILBOX_USER_ATTRIBUTE_SESSION_KEY";

    public static final String MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY = "org.apache.james.api.imap.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY";

    public static MailboxSession getMailboxSession(final ImapSession session) {
        final MailboxSession result = (MailboxSession) session.getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY);
        return result;
    }

    public static String getUserName(final ImapSession imapSession) {
        final String result;
        final MailboxSession mailboxSession = getMailboxSession(imapSession);
        if (imapSession == null) {
            result = null;
        } else {
            result = mailboxSession.getUser().getUserName();
        }
        return result;
    }
}
