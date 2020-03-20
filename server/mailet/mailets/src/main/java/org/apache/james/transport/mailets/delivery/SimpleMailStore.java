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

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.metrics.api.Metric;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class SimpleMailStore implements MailStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleMailStore.class);

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UsersRepository usersRepos;
        private MailboxAppender mailboxAppender;
        private String folder;
        private Metric metric;

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

        public Builder metric(Metric metric) {
            this.metric = metric;
            return this;
        }

        public SimpleMailStore build() {
            Preconditions.checkNotNull(usersRepos);
            Preconditions.checkNotNull(folder);
            Preconditions.checkNotNull(mailboxAppender);
            Preconditions.checkNotNull(metric);
            return new SimpleMailStore(mailboxAppender, usersRepos, metric, folder);
        }
    }

    private final MailboxAppender mailboxAppender;
    private final UsersRepository usersRepository;
    private final Metric metric;
    private final String folder;

    private SimpleMailStore(MailboxAppender mailboxAppender, UsersRepository usersRepository, Metric metric, String folder) {
        this.mailboxAppender = mailboxAppender;
        this.usersRepository = usersRepository;
        this.metric = metric;
        this.folder = folder;
    }

    @Override
    public void storeMail(MailAddress recipient, Mail mail) throws MessagingException {
        Username username = computeUsername(recipient);

        String locatedFolder = locateFolder(username, mail);
        ComposedMessageId composedMessageId = mailboxAppender.append(mail.getMessage(), username, locatedFolder);

        metric.increment();
        LOGGER.info("Local delivered mail {} successfully from {} to {} in folder {} with composedMessageId {}", mail.getName(),
            mail.getMaybeSender().asString(), recipient.asPrettyString(), locatedFolder, composedMessageId);
    }

    private String locateFolder(Username username, Mail mail) {
        return AttributeUtils
            .getValueAndCastFromMail(mail, AttributeName.of(DELIVERY_PATH_PREFIX + username.asString()), String.class)
            .orElse(folder);
    }

    private Username computeUsername(MailAddress recipient) {
        try {
            return usersRepository.getUsername(recipient);
        } catch (UsersRepositoryException e) {
            LOGGER.warn("Unable to retrieve username for {}", recipient.asPrettyString(), e);
            return Username.of(recipient.asString());
        }
    }
}
