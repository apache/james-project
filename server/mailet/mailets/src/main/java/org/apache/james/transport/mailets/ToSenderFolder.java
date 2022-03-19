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

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import jakarta.mail.Flags;
import jakarta.mail.MessagingException;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.transport.mailets.delivery.MailboxAppenderImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.StorageDirective;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives a Mail from the Queue and takes care to deliver the message
 * to a defined folder of the sender.
 * 
 * You have to define the folder name of the sender.
 * The flag 'consume' will tell is the mail will be further
 * processed by the upcoming processor mailets, or not.
 * 
 * <pre>
 * &lt;mailet match="SenderIsLocal" class="ToSenderFolder"&gt;
 *    &lt;folder&gt; <i>Sent Items</i> &lt;/folder&gt;
 *    &lt;consume&gt; <i>false</i> &lt;/consume&gt;
 * &lt;/mailet&gt;
 * </pre>
 * 
 */
@Experimental
public class ToSenderFolder extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToSenderFolder.class);
    private static final Optional<Flags> NO_FLAGS = Optional.empty();

    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;
    private String folder;
    private boolean consume;
    private MailboxAppenderImpl mailboxAppender;

    @Inject
    public ToSenderFolder(UsersRepository usersRepository, @Named("mailboxmanager") MailboxManager mailboxManager) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
    }

    /**
     * Delivers a mail to a local mailbox in a given folder.
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
        if (mail.hasSender()) {
            MailAddress sender = mail.getMaybeSender().get();
            Username username = retrieveUser(sender);

            mailboxAppender.append(mail.getMessage(), username, StorageDirective.builder().targetFolder(folder).build()).block();

            LOGGER.error("Local delivery with ToSenderFolder mailet for mail {} with sender {} in folder {}", mail.getName(), sender, folder);
        }
    }

    private Username retrieveUser(MailAddress sender) throws MessagingException {
        try {
            return usersRepository.getUsername(sender);
        } catch (UsersRepositoryException e) {
            throw new MessagingException(e.getMessage());
        }
    }

    @Override
    public void init() throws MessagingException {
        folder = getInitParameter("folder", "Sent");
        consume = getInitParameter("consume", false);
        mailboxAppender = new MailboxAppenderImpl(mailboxManager);
    }

    @Override
    public String getMailetInfo() {
        return ToSenderFolder.class.getName() + " Mailet";
    }

}
