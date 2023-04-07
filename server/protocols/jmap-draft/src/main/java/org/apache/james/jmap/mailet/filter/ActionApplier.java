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

package org.apache.james.jmap.mailet.filter;

import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.mailet.Mail;
import org.apache.mailet.StorageDirective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class ActionApplier {
    static final String DELIVERY_PATH_PREFIX = "DeliveryPath_";
    public static final Logger LOGGER = LoggerFactory.getLogger(ActionApplier.class);

    @VisibleForTesting
    static class Factory {
        private final MailboxManager mailboxManager;
        private final MailboxId.Factory mailboxIdFactory;

        @Inject
        Factory(MailboxManager mailboxManager, MailboxId.Factory mailboxIdFactory) {
            this.mailboxManager = mailboxManager;
            this.mailboxIdFactory = mailboxIdFactory;
        }

        public RequireUser forMail(Mail mail) {
            return new RequireUser(mail);
        }

        public class RequireUser {
            private final Mail mail;

            RequireUser(Mail mail) {
                this.mail = mail;
            }

            public ActionApplier forUser(Username username) {
                return new ActionApplier(mailboxManager, mailboxIdFactory, mail, username);
            }
        }
    }

    private final MailboxManager mailboxManager;
    private final MailboxId.Factory mailboxIdFactory;
    private final Mail mail;
    private final Username username;

    @VisibleForTesting
    public static Factory factory(MailboxManager mailboxManager, MailboxId.Factory mailboxIdFactory) {
        return new Factory(mailboxManager, mailboxIdFactory);
    }

    private ActionApplier(MailboxManager mailboxManager, MailboxId.Factory mailboxIdFactory, Mail mail, Username username) {
        this.mailboxManager = mailboxManager;
        this.mailboxIdFactory = mailboxIdFactory;
        this.mail = mail;
        this.username = username;
    }

    public void apply(Stream<Rule.Action> actions) {
        actions.flatMap(action -> action.getAppendInMailboxes().getMailboxIds().stream())
            .map(mailboxIdFactory::fromString)
            .forEach(this::addStorageDirective);
    }

    private void addStorageDirective(MailboxId mailboxId) {
        try {
            MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
            MessageManager messageManager = mailboxManager.getMailbox(mailboxId, mailboxSession);

            String mailboxName = messageManager.getMailboxPath().getName();

            StorageDirective.builder()
                .targetFolder(mailboxName)
                .build()
                .encodeAsAttributes(username)
                .forEach(mail::setAttribute);
        } catch (MailboxNotFoundException e) {
            LOGGER.info("Mailbox {} does not exist, but it was mentioned in a JMAP filtering rule", mailboxId, e);
        } catch (Exception e) {
            LOGGER.error("Unexpected failure while resolving mailbox name for {}", mailboxId, e);
        }
    }
}
