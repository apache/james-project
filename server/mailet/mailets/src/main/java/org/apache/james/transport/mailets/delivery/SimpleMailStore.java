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

import javax.mail.MessagingException;

import org.apache.commons.logging.Log;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import com.google.common.base.Preconditions;

public class SimpleMailStore implements MailStore {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UsersRepository usersRepos;
        private MailboxAppender mailboxAppender;
        private String folder;
        private Log log;

        public Builder folder(String folder) {
            this.folder = folder;
            return this;
        }

        public Builder usersRepository(UsersRepository usersRepository) {
            this.usersRepos = usersRepository;
            return this;
        }

        public Builder mailboxAppender(MailboxAppender mailboxAppender) {
            this.mailboxAppender = mailboxAppender;
            return this;
        }

        public Builder log(Log log) {
            this.log = log;
            return this;
        }

        public SimpleMailStore build() throws MessagingException {
            Preconditions.checkNotNull(usersRepos);
            Preconditions.checkNotNull(folder);
            Preconditions.checkNotNull(log);
            Preconditions.checkNotNull(mailboxAppender);
            return new SimpleMailStore(mailboxAppender, usersRepos, log, folder);
        }
    }

    private final MailboxAppender mailboxAppender;
    private final UsersRepository usersRepository;
    private final Log log;
    private final String folder;

    private SimpleMailStore(MailboxAppender mailboxAppender, UsersRepository usersRepository, Log log, String folder) {
        this.mailboxAppender = mailboxAppender;
        this.usersRepository = usersRepository;
        this.log = log;
        this.folder = folder;
    }

    @Override
    public void storeMail(MailAddress recipient, Mail mail) throws MessagingException {
        String username = computeUsername(recipient);

        mailboxAppender.append(mail.getMessage(), username, folder);

        log.info("Local delivered mail " + mail.getName() + " successfully from " + DeliveryUtils.prettyPrint(mail.getSender())
            + " to " + DeliveryUtils.prettyPrint(recipient) + " in folder " + this.folder);
    }

    private String computeUsername(MailAddress recipient) throws MessagingException {
        try {
            return usersRepository.getUser(recipient);
        } catch (UsersRepositoryException e) {
            log.warn("Unable to retrieve username for " + recipient.asPrettyString(), e);
            return recipient.toString();
        }
    }
}
