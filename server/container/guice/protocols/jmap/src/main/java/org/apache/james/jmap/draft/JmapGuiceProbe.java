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

package org.apache.james.jmap.draft;

import java.util.Arrays;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.JMAPServer;
import org.apache.james.jmap.api.change.EmailChange;
import org.apache.james.jmap.api.change.EmailChangeRepository;
import org.apache.james.jmap.api.change.MailboxChangeRepository;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationPatch;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.util.Port;
import org.apache.james.utils.GuiceProbe;

import reactor.core.publisher.Mono;

public class JmapGuiceProbe implements GuiceProbe {

    private final VacationRepository vacationRepository;
    private final MailboxChangeRepository mailboxChangeRepository;
    private final EmailChangeRepository emailChangeRepository;
    private final JMAPServer jmapServer;
    private final MessageIdManager messageIdManager;
    private final MailboxManager mailboxManager;
    private final EventBus eventBus;
    private final MessageFastViewProjection messageFastViewProjection;

    @Inject
    private JmapGuiceProbe(VacationRepository vacationRepository, MailboxChangeRepository mailboxChangeRepository, EmailChangeRepository emailChangeRepository, JMAPServer jmapServer, MessageIdManager messageIdManager, MailboxManager mailboxManager, EventBus eventBus, MessageFastViewProjection messageFastViewProjection) {
        this.vacationRepository = vacationRepository;
        this.mailboxChangeRepository = mailboxChangeRepository;
        this.emailChangeRepository = emailChangeRepository;
        this.jmapServer = jmapServer;
        this.messageIdManager = messageIdManager;
        this.mailboxManager = mailboxManager;
        this.eventBus = eventBus;
        this.messageFastViewProjection = messageFastViewProjection;
    }

    public Port getJmapPort() {
        return jmapServer.getPort();
    }

    public void addMailboxListener(MailboxListener.GroupMailboxListener listener) {
        eventBus.register(listener);
    }

    public void modifyVacation(AccountId accountId, VacationPatch vacationPatch) {
        vacationRepository.modifyVacation(accountId, vacationPatch).block();
    }

    public Vacation retrieveVacation(AccountId accountId) {
        return vacationRepository.retrieveVacation(accountId).block();
    }

    public void setInMailboxes(MessageId messageId, Username username, MailboxId... mailboxIds) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        messageIdManager.setInMailboxes(messageId, Arrays.asList(mailboxIds), mailboxSession);
    }

    public void clearMessageFastViewProjection() {
        Mono.from(messageFastViewProjection.clear()).block();
    }

    public void saveEmailChange(EmailChange change) {
        emailChangeRepository.save(change).block();
    }

    public State getLatestMailboxState(AccountId accountId) {
        return mailboxChangeRepository.getLatestState(accountId).block();
    }

    public State getLatestMailboxStateWithDelegation(AccountId accountId) {
        return mailboxChangeRepository.getLatestStateWithDelegation(accountId).block();
    }

    public State getLatestEmailState(AccountId accountId) {
        return emailChangeRepository.getLatestState(accountId).block();
    }

    public State getLatestEmailStateWithDelegation(AccountId accountId) {
        return emailChangeRepository.getLatestStateWithDelegation(accountId).block();
    }
}
