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

import javax.inject.Inject;

import org.apache.james.core.User;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.mailet.Mail;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;

public class ActionApplier {
    static final String DELIVERY_PATH_PREFIX = "DeliveryPath_";

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

            public ActionApplier forUser(User user) {
                return new ActionApplier(mailboxManager, mailboxIdFactory, mail, user);
            }
        }
    }

    private final MailboxManager mailboxManager;
    private final MailboxId.Factory mailboxIdFactory;
    private final Mail mail;
    private final User user;

    @VisibleForTesting
    public static Factory factory(MailboxManager mailboxManager, MailboxId.Factory mailboxIdFactory) {
        return new Factory(mailboxManager, mailboxIdFactory);
    }

    private ActionApplier(MailboxManager mailboxManager, MailboxId.Factory mailboxIdFactory, Mail mail, User user) {
        this.mailboxManager = mailboxManager;
        this.mailboxIdFactory = mailboxIdFactory;
        this.mail = mail;
        this.user = user;
    }

    public void apply(Rule.Action action) {
        action.getAppendInMailboxes()
                .getMailboxIds()
                .stream()
                .findFirst()
                .map(mailboxIdFactory::fromString)
                .ifPresent(Throwing.consumer(this::addStorageDirective));
    }

    private void addStorageDirective(MailboxId mailboxId) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(user.asString());
        MessageManager messageManager = mailboxManager.getMailbox(mailboxId, mailboxSession);

        String mailboxName = messageManager.getMailboxPath().getName();
        String attributeNameForUser = DELIVERY_PATH_PREFIX + user.asString();
        mail.setAttribute(attributeNameForUser, mailboxName);
    }
}
