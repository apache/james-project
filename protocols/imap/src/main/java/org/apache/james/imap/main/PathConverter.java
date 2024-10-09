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

import org.apache.james.core.Domain;
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
        private static final int USER = 1;

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
            return new MailboxPath(MailboxConstants.USER_NAMESPACE, session.getUserName(), sanitizeMailboxName(mailboxName));
        }

        private MailboxPath buildAbsolutePath(String absolutePath) {
            MailboxSession mailboxSession = session.getMailboxSession();
            return asMailboxPath(Splitter.on(mailboxSession.getPathDelimiter()).splitToList(absolutePath), mailboxSession);
        }

        private MailboxPath asMailboxPath(List<String> mailboxPathParts, MailboxSession session) {
            String namespace = mailboxPathParts.get(NAMESPACE);
            if (namespace.equalsIgnoreCase("#private")) {
                String mailboxName = Joiner.on(session.getPathDelimiter()).join(Iterables.skip(mailboxPathParts, 1));
                return new MailboxPath(MailboxConstants.USER_NAMESPACE, session.getUser(), sanitizeMailboxName(mailboxName));
            } else if (namespace.equalsIgnoreCase("#user")) {
                Preconditions.checkArgument(mailboxPathParts.size() > 2, "Expecting at least 2 parts");
                String username = mailboxPathParts.get(USER);
                Username user = Username.from(username, session.getUser().getDomainPart().map(Domain::asString));
                String mailboxName = Joiner.on(session.getPathDelimiter()).join(Iterables.skip(mailboxPathParts, 2));
                return new MailboxPath(MailboxConstants.USER_NAMESPACE, user, sanitizeMailboxName(mailboxName));

            } else {
                throw new DeniedAccessOnSharedMailboxException();
            }
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
        private String joinMailboxPath(MailboxPath mailboxPath, MailboxSession session) {
            StringBuilder sb = new StringBuilder();
            if (mailboxPath.getNamespace() != null && !mailboxPath.getNamespace().equals("")) {
                if (mailboxPath.getNamespace().equalsIgnoreCase(MailboxConstants.USER_NAMESPACE)
                    && !mailboxPath.belongsTo(session)) {
                    sb.append("#user");
                } else {
                    sb.append(mailboxPath.getNamespace());
                }
            }
            if (mailboxPath.getUser() != null && !mailboxPath.getUser().equals("")) {
                if (!mailboxPath.belongsTo(session)) {
                    if (sb.length() > 0) {
                        sb.append(session.getPathDelimiter());
                    }
                    sb.append(mailboxPath.getUser().asString());
                }
            }
            if (mailboxPath.getName() != null && !mailboxPath.getName().equals("")) {
                if (sb.length() > 0) {
                    sb.append(session.getPathDelimiter());
                }
                sb.append(mailboxPath.getName());
            }
            return sb.toString();
        }

        public String mailboxName(boolean relative, MailboxPath path, MailboxSession session) {
            if (relative && path.belongsTo(session)) {
                return path.getName();
            } else {
                return joinMailboxPath(path, session);
            }
        }
    }

    MailboxPath buildFullPath(String mailboxName);

    String mailboxName(boolean relative, MailboxPath path, MailboxSession session);
}
