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

package org.apache.james.transport.matchers;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;

/**
 * <p>
 * Experimental: Abstract matcher checking whether a recipient has exceeded a
 * maximum allowed <I>storage</I> quota for messages standing in his inbox.
 * </p>
 * <p>
 * "Storage quota" at this level is still an abstraction whose specific
 * interpretation will be done by subclasses (e.g. could be specific for each
 * user or common to all of them).
 * </p>
 * <p>
 * This matcher need to calculate the mailbox size every time it is called. This
 * can slow down things if there are many mails in the mailbox. Some users also
 * report big problems with the matcher if a JDBC based mailrepository is used.
 * </p>
 * 
 * @since 2.2.0
 */
@Experimental
abstract public class AbstractStorageQuota extends AbstractQuotaMatcher {

    private MailboxManager manager;

    @Inject
    public void setMailboxManager(@Named("mailboxmanager") MailboxManager manager) {
        this.manager = manager;
    }

    @Inject
    public void setUsersRepository(UsersRepository localUsers) {
        this.localUsers = localUsers;
    }

    /**
     * The user repository for this mail server. Contains all the users with
     * inboxes on this server.
     */
    private UsersRepository localUsers;

    /**
     * Checks the recipient.<br>
     * Does a <code>super.isRecipientChecked</code> and checks that the
     * recipient is a known user in the local server.<br>
     * If a subclass overrides this method it should "and"
     * <code>super.isRecipientChecked</code> to its check.
     * 
     * @param recipient
     *            the recipient to check
     */
    protected boolean isRecipientChecked(MailAddress recipient) throws MessagingException {
        MailetContext mailetContext = getMailetContext();
        return super.isRecipientChecked(recipient) && (mailetContext.isLocalEmail(recipient));
    }

    /**
     * Gets the storage used in the recipient's inbox.
     * 
     * @param recipient
     *            the recipient to check
     */
    @Override
    protected long getUsed(MailAddress recipient, Mail ignored) throws MessagingException {
        long size = 0;
        MailboxSession session;
        try {
            String username;
            try {
                // see if we need use the full email address as username or not.
                // See JAMES-1197
                username = localUsers.getUser(recipient).toLowerCase(Locale.US);
            }
            catch (UsersRepositoryException e) {
                throw new MessagingException("Unable to access UsersRepository", e);
            }
            session = manager.createSystemSession(username);
            manager.startProcessingRequest(session);

            // get all mailboxes for the user to calculate the size
            // TODO: See JAMES-1198
            List<MailboxMetaData> mList = manager.search(
                    MailboxQuery.builder()
                        .base(MailboxPath.inbox(session))
                        .pathDelimiter(session.getPathDelimiter())
                        .build(),
                    session);
            for (MailboxMetaData aMList : mList) {
                MessageManager mailbox = manager.getMailbox(aMList.getPath(), session);
                Iterator<MessageResult> results = mailbox.getMessages(MessageRange.all(), FetchGroupImpl.MINIMAL,
                        session);
                while (results.hasNext()) {
                    size += results.next().getSize();
                }
            }
            manager.endProcessingRequest(session);
            manager.logout(session, true);
        }
        catch (BadCredentialsException e) {
            throw new MessagingException("Unable to authenticate to mailbox", e);
        }
        catch (MailboxException e) {
            throw new MessagingException("Unable to get used space from mailbox", e);
        }

        return size;
    }
}
