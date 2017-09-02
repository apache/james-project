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

package org.apache.james.utils;

import java.util.Arrays;

import javax.inject.Inject;

import org.apache.james.jmap.JMAPServer;
import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationPatch;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;

public class JmapGuiceProbe implements GuiceProbe {

    private final VacationRepository vacationRepository;
    private final JMAPServer jmapServer;
    private final MessageIdManager messageIdManager;
    private final MailboxManager mailboxManager;

    @Inject
    private JmapGuiceProbe(VacationRepository vacationRepository, JMAPServer jmapServer, MessageIdManager messageIdManager, MailboxManager mailboxManager) {
        this.vacationRepository = vacationRepository;
        this.jmapServer = jmapServer;
        this.messageIdManager = messageIdManager;
        this.mailboxManager = mailboxManager;
    }

    public int getJmapPort() {
        return jmapServer.getPort();
    }

    public void addMailboxListener(MailboxListener listener) throws MailboxException {
        mailboxManager.addGlobalListener(listener, mailboxManager.createSystemSession("jmap"));
    }

    public void modifyVacation(AccountId accountId, VacationPatch vacationPatch) {
        vacationRepository.modifyVacation(accountId, vacationPatch).join();
    }

    public Vacation retrieveVacation(AccountId accountId) {
        return vacationRepository.retrieveVacation(accountId).join();
    }

    public void setInMailboxes(MessageId messageId, String username, MailboxId... mailboxIds) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        messageIdManager.setInMailboxes(messageId, Arrays.asList(mailboxIds), mailboxSession);
    }
}
