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
import org.apache.james.metrics.api.Metric;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.apache.mailet.StorageDirective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

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
    public Mono<Void> storeMail(MailAddress recipient, Mail mail) {
        Username username = computeUsername(recipient);
        StorageDirective storageDirective = StorageDirective.fromMail(computeUsername(recipient), mail)
            .withDefaultFolder(folder);

        try {
            return Mono.from(mailboxAppender.append(mail.getMessage(), username, storageDirective))
                .doOnSuccess(ids -> {
                    metric.increment();
                    LOGGER.info("Local delivered mail {} with messageId {} successfully from {} to {} in folder {} with composedMessageId {}",
                        mail.getName(), getMessageId(mail), mail.getMaybeSender().asString(), recipient.asPrettyString(), storageDirective.getTargetFolder().get(), ids);
                })
                .then();
        } catch (MessagingException e) {
            throw new RuntimeException("Could not retrieve mail message content", e);
        }
    }

    private Username computeUsername(MailAddress recipient) {
        try {
            return usersRepository.getUsername(recipient);
        } catch (UsersRepositoryException e) {
            LOGGER.warn("Unable to retrieve username for {}", recipient.asPrettyString(), e);
            return Username.of(recipient.asString());
        }
    }

    private String getMessageId(Mail mail) {
        try {
            return mail.getMessage().getMessageID();
        } catch (MessagingException e) {
            LOGGER.debug("failed to extract messageId from message {}", mail.getName(), e);
            return null;
        }
    }
}
