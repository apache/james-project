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

package org.apache.james.imap.main;

import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;

public class PathConverter {

    public static PathConverter forSession(ImapSession session) {
        return new PathConverter(session);
    }

    private final ImapSession session;

    public PathConverter(ImapSession session) {
        this.session = session;
    }

    public MailboxPath buildFullPath(String mailboxName) {
        String namespace = null;
        String name = null;
        final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);

        if (mailboxName == null || mailboxName.length() == 0) {
            return new MailboxPath("", "", "");
        }
        if (mailboxName.charAt(0) == MailboxConstants.NAMESPACE_PREFIX_CHAR) {
            int namespaceLength = mailboxName.indexOf(mailboxSession.getPathDelimiter());
            if (namespaceLength > -1) {
                namespace = mailboxName.substring(0, namespaceLength);
                if (mailboxName.length() > namespaceLength)
                    name = mailboxName.substring(++namespaceLength);
            } else {
                namespace = mailboxName;
            }
        } else {
            namespace = MailboxConstants.USER_NAMESPACE;
            name = mailboxName;
        }
        String user = null;
        // we only use the user as part of the MailboxPath if its a private user
        // namespace
        if (namespace.equals(MailboxConstants.USER_NAMESPACE)) {
            user = ImapSessionUtils.getUserName(session);
        }

        // use uppercase for INBOX
        //
        // See IMAP-349
        if (name.equalsIgnoreCase(MailboxConstants.INBOX)) {
            name = MailboxConstants.INBOX;
        }

        return new MailboxPath(namespace, user, name);
    }

}
