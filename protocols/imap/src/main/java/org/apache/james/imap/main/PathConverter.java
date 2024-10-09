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

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

public interface PathConverter {
    interface Factory {
        PathConverter.Factory DEFAULT = new PathConverter.Factory.Default();

        class Default implements Factory {
            public PathConverter forSession(ImapSession session) {
                return new PathConverter.Default(session);
            }
        }

        PathConverter forSession(ImapSession session);
    }

    class Default implements PathConverter{
        private static final int NAMESPACE = 0;

        public static PathConverter forSession(ImapSession session) {
            return new PathConverter.Default(session);
        }

        private final ImapSession session;

        private Default(ImapSession session) {
            this.session = session;
        }

        public MailboxPath buildFullPath(String mailboxName) {
            if (Strings.isNullOrEmpty(mailboxName)) {
                return buildRelativePath("");
            }
            if (isAbsolute(mailboxName)) {
                return buildAbsolutePath(mailboxName);
            } else {
                return buildRelativePath(mailboxName);
            }
        }

        private boolean isAbsolute(String mailboxName) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(mailboxName));
            return mailboxName.charAt(0) == MailboxConstants.NAMESPACE_PREFIX_CHAR;
        }

        private MailboxPath buildRelativePath(String mailboxName) {
            return buildMailboxPath(MailboxConstants.USER_NAMESPACE, session.getUserName(), mailboxName);
        }

        private MailboxPath buildAbsolutePath(String absolutePath) {
            char pathDelimiter = session.getMailboxSession().getPathDelimiter();
            List<String> mailboxPathParts = Splitter.on(pathDelimiter).splitToList(absolutePath);
            String namespace = mailboxPathParts.get(NAMESPACE);
            String mailboxName = Joiner.on(pathDelimiter).join(Iterables.skip(mailboxPathParts, 1));
            return buildMailboxPath(namespace, retrieveUserName(namespace), mailboxName);
        }

        private Username retrieveUserName(String namespace) {
            if (namespace.equals(MailboxConstants.USER_NAMESPACE)) {
                return session.getUserName();
            }
            throw new DeniedAccessOnSharedMailboxException();
        }

        private MailboxPath buildMailboxPath(String namespace, Username user, String mailboxName) {
            if (!namespace.equals(MailboxConstants.USER_NAMESPACE)) {
                throw new DeniedAccessOnSharedMailboxException();
            }
            return new MailboxPath(namespace, user, sanitizeMailboxName(mailboxName));
        }

        private String sanitizeMailboxName(String mailboxName) {
            // use uppercase for INBOX
            // See IMAP-349
            if (mailboxName.equalsIgnoreCase(MailboxConstants.INBOX)) {
                return MailboxConstants.INBOX;
            }
            return mailboxName;
        }

        /**
         * Joins the elements of a mailboxPath together and returns them as a string
         */
        private String joinMailboxPath(MailboxPath mailboxPath, char delimiter) {
            StringBuilder sb = new StringBuilder();
            if (mailboxPath.getNamespace() != null && !mailboxPath.getNamespace().equals("")) {
                sb.append(mailboxPath.getNamespace());
            }
            if (mailboxPath.getUser() != null && !mailboxPath.getUser().equals("")) {
                if (sb.length() > 0) {
                    sb.append(delimiter);
                }
                sb.append(mailboxPath.getUser().asString());
            }
            if (mailboxPath.getName() != null && !mailboxPath.getName().equals("")) {
                if (sb.length() > 0) {
                    sb.append(delimiter);
                }
                sb.append(mailboxPath.getName());
            }
            return sb.toString();
        }

        public String mailboxName(boolean relative, MailboxPath path, MailboxSession session) {
            if (relative && path.belongsTo(session)) {
                return path.getName();
            } else {
                return joinMailboxPath(path, session.getPathDelimiter());
            }
        }
    }

    MailboxPath buildFullPath(String mailboxName);

    String mailboxName(boolean relative, MailboxPath path, MailboxSession session);
}
