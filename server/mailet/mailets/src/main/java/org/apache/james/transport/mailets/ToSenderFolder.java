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

import java.util.Date;

import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.MessagingException;

import org.apache.james.core.MimeMessageInputStream;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.transport.util.MailetContextLog;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;

/**
 * Receives a Mail from the Queue and takes care to deliver the message
 * to a defined folder of the sender.
 * 
 * You have to define the folder name of the sender.
 * The flag 'consume' will tell is the mail will be further
 * processed by the upcoming processor mailets, or not.
 * 
 * <pre>
 * &lt;mailet match="RecipientIsLocal" class="ToSenderFolder"&gt;
 *    &lt;folder&gt; <i>Sent Items</i> &lt;/folder&gt;
 *    &lt;consume&gt; <i>false</i> &lt;/consume&gt;
 * &lt;/mailet&gt;
 * </pre>
 * 
 */
public class ToSenderFolder extends GenericMailet {

    @Inject
    private UsersRepository usersRepository;

    @Inject
    @Named("mailboxmanager")
    private MailboxManager mailboxManager;

    private String folder;
    private boolean consume;

    /**
     * Delivers a mail to a local mailbox in a given folder.
     * 
     * @see org.apache.mailet.base.GenericMailet#service(org.apache.mailet.Mail)
     */
    @Override
    public void service(Mail mail) throws MessagingException {
        if (!mail.getState().equals(Mail.GHOST)) {
            doService(mail);
            if (consume) {
                mail.setState(Mail.GHOST);
            }
        }
    }

    private void doService(Mail mail) throws MessagingException {

        final MailAddress sender = mail.getSender();
        String username;
        try {
            if (usersRepository.supportVirtualHosting()) {
                username = sender.toString();
            }
            else {
                username = sender.getLocalPart();
            }
        } catch (UsersRepositoryException e) {
            throw new MessagingException(e.getMessage());
        }

        final MailboxSession session;
        try {
            session = mailboxManager.createSystemSession(username, new MailetContextLog(getMailetContext()));
        } catch (BadCredentialsException e) {
            throw new MessagingException("Unable to authenticate to mailbox", e);
        } catch (MailboxException e) {
            throw new MessagingException("Can not access mailbox", e);
        }

        mailboxManager.startProcessingRequest(session);

        final MailboxPath path = new MailboxPath(MailboxConstants.USER_NAMESPACE, username, this.folder);
        
        try {
        
            if (this.folder.equalsIgnoreCase(folder) && !(mailboxManager.mailboxExists(path, session))) {
                mailboxManager.createMailbox(path, session);
            }
            final MessageManager mailbox = mailboxManager.getMailbox(path, session);
            if (mailbox == null) {
                final String error = "Mailbox for username " + username + " was not found on this server.";
                throw new MessagingException(error);
            }

            mailbox.appendMessage(new MimeMessageInputStream(mail.getMessage()), new Date(), session, true, null);

            log("Local delivery with ToSenderFolder mailet for mail " + mail.getName() + " with sender " + sender.toString() + " in folder " + this.folder);
        
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

    /* (non-Javadoc)
     * @see org.apache.mailet.base.GenericMailet#init()
     */
    @Override
    public void init() throws MessagingException {
        super.init();
        this.folder = getInitParameter("folder", "Sent");
        this.consume = getInitParameter("consume", false);

    }

    /* (non-Javadoc)
     * @see org.apache.mailet.base.GenericMailet#getMailetInfo()
     */
    @Override
    public String getMailetInfo() {
        return ToSenderFolder.class.getName() + " Mailet";
    }

}
