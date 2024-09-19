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

package org.apache.james.transport.mailets;

import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.IGNORE;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.apache.mailet.StorageDirective;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

/**
 * SubAddressing positions a storage directive for the folder this email will be delivered in if the following criteria are met:
 * - the sender has requested the storage directive by sending the mail to <strong>`recipient+folder@domain`</strong> instead of just `recipient@domain` ;
 * - the folder <strong>exists</strong> and the recipient has <strong>allowed</strong> the sender to send a mail to that specific folder.
 *
 * These directives are used by <strong>LocalDelivery</strong> mailet when adding the email to the recipients mailboxes.
 *
 * The storage directive is recognized when a specific character or character sequence is present in the local part of the recipient address. <strong>By default, it is "+"</strong>.
 * If the sender is not allowed to send a mail to the specified folder, then the mail is delivered in the recipient's inbox.
 * Likewise, if the storage directive is empty or absent, the mail will simply be delivered in the recipient's inbox.
 * Thus,
 * - a mail sent to `recipient+folder@domain` will be delivered to recipient's folder `folder` if allowed ;
 * - a mail sent to `recipient+my-super-folder@domain` will be delivered to recipient's folder `my-super-folder` if allowed ;
 * - a mail sent to `recipient@domain` or `recipient+@domain` will be delivered to recipient's inbox.
 *
 * Any user can position rights for other users and for its different folders. They may create whitelists or blacklists, for one or several folders.
 * In the case where the sender is unknown, the mail will be delivered in the specified folder only if the recipient has allowed everyone for that folder.
 */
public class SubAddressing extends GenericMailet {
    private static final Logger LOG = LoggerFactory.getLogger(SubAddressing.class);
    private static final Username NO_ASSOCIATED_USER = null;

    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;

    @Inject
    public SubAddressing(UsersRepository usersRepository, @Named("mailboxmanager") MailboxManager mailboxManager) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        mail.getRecipients().forEach(recipient ->
            recipient.getLocalPartDetails(UsersRepository.LOCALPART_DETAIL_DELIMITER)
                .ifPresent(Throwing.consumer(targetFolder -> postIfHasRight(mail, recipient, targetFolder))));
    }

    private void postIfHasRight(Mail mail, MailAddress recipient, String targetFolder) throws UsersRepositoryException, MailboxException {
        if (hasPostRight(mail, recipient, targetFolder)) {
            StorageDirective.builder().targetFolders(ImmutableList.of(targetFolder)).build()
                .encodeAsAttributes(usersRepository.getUsername(recipient))
                .forEach(mail::setAttribute);
        } else {
            LOG.info("{} tried to address {}'s subfolder `{}` but they did not have the right to",
                mail.getMaybeSender().toString(), recipient.stripDetails(UsersRepository.LOCALPART_DETAIL_DELIMITER), targetFolder);
        }
    }

    private Boolean hasPostRight(Mail mail, MailAddress recipient, String targetFolder) throws MailboxException, UsersRepositoryException {
        try {
            return resolvePostRight(retrieveMailboxACL(recipient, targetFolder), mail.getMaybeSender(), recipient);
        } catch (MailboxNotFoundException e) {
            LOG.info("{}'s subfolder `{}` was tried to be addressed but it does not exist", recipient.stripDetails(UsersRepository.LOCALPART_DETAIL_DELIMITER), targetFolder);
            return false;
        }
    }

    private MailboxACL retrieveMailboxACL(MailAddress recipient, String targetFolder) throws MailboxException, UsersRepositoryException {
        Username recipientUsername = usersRepository.getUsername(recipient);
        MailboxSession session = mailboxManager.createSystemSession(recipientUsername);
        return mailboxManager.getMailbox(MailboxPath.forUser(recipientUsername, targetFolder), session)
            .getMetaData(IGNORE, session, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();
    }

    private boolean resolvePostRight(MailboxACL acl, MaybeSender maybeSender, MailAddress recipient) throws UnsupportedRightException, UsersRepositoryException {
        return new UnionMailboxACLResolver().resolveRights(
                maybeSender.asOptional()
                    .map(Throwing.function(usersRepository::getUsername))
                    .orElse(NO_ASSOCIATED_USER), acl, usersRepository.getUsername(recipient))
            .contains(MailboxACL.Right.Post);
    }
}
