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

package org.apache.james.transport.mailets.delivery;

import java.util.Date;

import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MimeMessageInputStream;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.transport.mailets.jsieve.Poster;
import org.apache.james.transport.util.MailetContextLog;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.MailetContext;

import com.google.common.base.Strings;

public class SievePoster implements Poster {

    private static final boolean IS_RECENT = true;
    private static final Flags FLAGS = null;

    private final MailboxManager mailboxManager;
    private final String folder;
    private final UsersRepository usersRepos;
    private final MailetContext mailetContext;

    public SievePoster(MailboxManager mailboxManager, String folder, UsersRepository usersRepos, MailetContext mailetContext) {
        this.mailboxManager = mailboxManager;
        this.folder = folder;
        this.usersRepos = usersRepos;
        this.mailetContext = mailetContext;
    }

    @Override
    public void post(String url, MimeMessage mail) throws MessagingException {
        final int endOfScheme = url.indexOf(':');
        if (endOfScheme < 0) {
            throw new MessagingException("Malformed URI");
        } else {
            final String scheme = url.substring(0, endOfScheme);
            if (scheme.equals("mailbox")) {
                handleMailboxProtocol(url, mail, endOfScheme);
            } else {
                throw new MessagingException("Unsupported protocol");
            }
        }
    }

    private void handleMailboxProtocol(String url, MimeMessage mail, int endOfScheme) throws MessagingException {
        int startOfUser = endOfScheme + 3;
        int endOfUser = url.indexOf('@', startOfUser);
        int startOfHost = endOfUser + 1;
        int endOfHost = url.indexOf('/', startOfHost);
        if (endOfUser < 0) {
            // TODO: When user missing, append to a default location
            throw new MessagingException("Shared mailbox is not supported");
        } else {
            String host = url.substring(startOfHost, endOfHost);
            String user = parseUser(url, startOfUser, endOfUser, host);
            String urlPath = parseUrlPath(url, endOfHost);

            MailboxSession session = createMailboxSession(user);
            appendMessageToMailboxWithSession(mail, user, session, parseDestinationMailboxPath(user, urlPath, session));
        }
    }

    private String parseUrlPath(String url, int endOfHost) {
        String urlPath;
        int length = url.length();
        if (endOfHost + 1 == length) {
            urlPath = this.folder;
        } else {
            urlPath = url.substring(endOfHost, length);
        }
        return urlPath;
    }

    private String parseUser(String url, int startOfUser, int endOfUser, String host) throws MessagingException {
        // lowerCase the user - see
        // https://issues.apache.org/jira/browse/JAMES-1369
        String user = url.substring(startOfUser, endOfUser).toLowerCase();
        // Check if we should use the full email address as username
        try {
            if (usersRepos.supportVirtualHosting()) {
                return user + "@" + host;
            }
            return user;
        } catch (UsersRepositoryException e) {
            throw new MessagingException("Unable to accessUsersRepository", e);
        }
    }

    private void appendMessageToMailboxWithSession(MimeMessage mail, String user, MailboxSession session, MailboxPath path) throws MessagingException {
        mailboxManager.startProcessingRequest(session);
        try {
            appendMessageToMailbox(mail, user, session, path);
        } catch (MailboxException e) {
            throw new MessagingException("Unable to access mailbox.", e);
        } finally {
            session.close();
            try {
                mailboxManager.logout(session, true);
            } catch (MailboxException e) {
                throw new MessagingException("Can logout from mailbox", e);
            }
            mailboxManager.endProcessingRequest(session);
        }
    }

    private void appendMessageToMailbox(MimeMessage mail, String user, MailboxSession session, MailboxPath path) throws MailboxException, MessagingException {
        if (this.folder.equalsIgnoreCase(path.getName()) && !(mailboxManager.mailboxExists(path, session))) {
            mailboxManager.createMailbox(path, session);
        }
        final MessageManager mailbox = mailboxManager.getMailbox(path, session);
        if (mailbox == null) {
            throw new MessagingException("Mailbox for user " + user + " was not found on this server.");
        }
        mailbox.appendMessage(new MimeMessageInputStream(mail), new Date(), session, IS_RECENT, FLAGS);
    }

    private MailboxPath parseDestinationMailboxPath(String user, String urlPath, MailboxSession session) {
        // This allows Sieve scripts to use a standard delimiter
        // regardless of mailbox implementation
        String destination = urlPath.replace('/', session.getPathDelimiter());
        if (Strings.isNullOrEmpty(destination)) {
            destination = this.folder;
        }
        if (destination.charAt(0) == session.getPathDelimiter()) {
            destination = destination.substring(1);
        }
        // Use the MailboxSession to construct the MailboxPath - See JAMES-1326
        return new MailboxPath(MailboxConstants.USER_NAMESPACE, user, destination);
    }

    private MailboxSession createMailboxSession(String user) throws MessagingException {
        try {
            return mailboxManager.createSystemSession(user, new MailetContextLog(mailetContext));
        } catch (BadCredentialsException e) {
            throw new MessagingException("Unable to authenticate to mailbox", e);
        } catch (MailboxException e) {
            throw new MessagingException("Can not access mailbox", e);
        }
    }
}
