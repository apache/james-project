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
package org.apache.james.container.spring.tool;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;

import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.Flags;
import javax.mail.MessagingException;

import org.apache.james.core.MimeMessageInputStream;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.lifecycle.api.LogEnabled;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryStore.MailRepositoryStoreException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.slf4j.Logger;

/**
 * Tool to import James 2.3 users and mails into James 3.0.
 */
public class James23Importer implements LogEnabled {

    private Logger log;

    /**
     * James 3.0 users repository.
     */
    @Inject
    private UsersRepository james30UsersRepository;

    /**
     * James 3.0 users repository.
     */
    @Inject
    private MailRepositoryStore mailRepositoryStore;

    /**
     * James 3.0 domain list.
     */
    @Inject
    private DomainList domainList;

    /**
     * The mailbox manager needed to copy the mails to.
     */
    @Inject
    @Named("mailboxmanager")
    private MailboxManager mailboxManager;

    /**
     * James 2.3 user repository defined by configuration.
     */
    @Inject
    @Named("usersrepository23")
    private UsersRepository james23UsersRepository;

    /**
     * Import 2.3 users to 3.0 users (taking virtualDomains into account)<br>
     * Import 2.3 mails to 3.0 mails.
     * 
     * @throws MailRepositoryStoreException
     * @throws MessagingException
     * @throws UsersRepositoryException
     * @throws DomainListException
     * @throws IOException
     * @throws MailboxException
     */
    public void importUsersAndMailsFromJames23(String james23MailRepositoryPath, String defaultPassword) throws MailRepositoryStoreException, MessagingException, UsersRepositoryException, DomainListException, MailboxException, IOException {
        importUsersFromJames23(defaultPassword);
        importMailsFromJames23(james23MailRepositoryPath);
    }

    /**
     * Import 2.3 users to 3.0 users (taking virtualDomains into account)
     * 
     * @param defaultPassword
     * @throws MessagingException
     * @throws UsersRepositoryException
     * @throws DomainListException
     */
    public void importUsersFromJames23(String defaultPassword) throws MessagingException, UsersRepositoryException, DomainListException {
        Iterator<String> j23uIt = james23UsersRepository.list();
        while (j23uIt.hasNext()) {
            String userName23 = j23uIt.next();
            String userName30 = convert23UserTo30(userName23);
            james30UsersRepository.addUser(userName30, defaultPassword);
            log.info("New user is copied from 2.3 to 3.0 with username=" + userName30);
        }
    }

    /**
     * Import 2.3 mails to 3.0 mails.
     * 
     * @param james23MailRepositoryPath
     *            the 2.3 mail repository path to import from e.g.
     *            file://var/mail/inboxes
     * @throws MessagingException
     * @throws MailRepositoryStoreException
     * @throws UsersRepositoryException
     * @throws IOException
     * @throws MailboxException
     * @throws DomainListException
     */
    public void importMailsFromJames23(String james23MailRepositoryPath) throws MessagingException, MailRepositoryStoreException, UsersRepositoryException, MailboxException, DomainListException {

        Flags flags = new Flags();
        boolean isRecent = false;

        Iterator<String> james23userRepositoryIterator = james23UsersRepository.list();

        while (james23userRepositoryIterator.hasNext()) {

            String userName23 = james23userRepositoryIterator.next();
            MailRepository mailRepository = mailRepositoryStore.select(james23MailRepositoryPath + "/" + userName23);
            Iterator<String> mailRepositoryIterator = mailRepository.list();

            String userName30 = convert23UserTo30(userName23);


            MailboxSession mailboxSession = mailboxManager.createSystemSession(userName30);
            MailboxPath mailboxPath = MailboxPath.inbox(mailboxSession);

            mailboxManager.startProcessingRequest(mailboxSession);
            try {
                mailboxManager.createMailbox(mailboxPath, mailboxSession);
            } catch (MailboxExistsException e) {
                // Do nothing, the mailbox already exists.
            }
            mailboxManager.endProcessingRequest(mailboxSession);

            MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);

            while (mailRepositoryIterator.hasNext()) {
                Mail mail = mailRepository.retrieve(mailRepositoryIterator.next());
                mailboxManager.startProcessingRequest(mailboxSession);
                messageManager.appendMessage(new MimeMessageInputStream(mail.getMessage()), new Date(), mailboxSession, isRecent, flags);
                mailboxManager.endProcessingRequest(mailboxSession);
            }

        }

    }

    @Override
    public void setLog(Logger log) {
        this.log = log;
    }

    /**
     * Utility method to convert a James 2.3 username to a James 3.0 username.
     * To achieve this, we need to add the default James 3.0 domain because 2.3
     * users have no domains.
     * 
     * @param userName23
     * @return
     * @throws DomainListException
     */
    private String convert23UserTo30(String userName23) throws DomainListException {
        return userName23 + "@" + domainList.getDefaultDomain();
    }

}
