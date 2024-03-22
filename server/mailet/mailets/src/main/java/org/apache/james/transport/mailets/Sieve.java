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

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.transport.mailets.jsieve.ResourceLocator;
import org.apache.james.transport.mailets.jsieve.delivery.SieveExecutor;
import org.apache.james.transport.mailets.jsieve.delivery.SievePoster;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

/**
 * Execute Sieve scripts for incoming emails, and set the result of the execution as attributes of the mail
 */
public class Sieve extends GenericMailet {

    private final UsersRepository usersRepository;
    private final ResourceLocator resourceLocator;
    private SieveExecutor sieveExecutor;

    @Inject
    public Sieve(UsersRepository usersRepository, SieveRepository sieveRepository) {
        this(usersRepository, new ResourceLocator(sieveRepository, usersRepository));
    }

    public Sieve(UsersRepository usersRepository, ResourceLocator resourceLocator) {
        this.usersRepository = usersRepository;
        this.resourceLocator = resourceLocator;
    }

    @Override
    public String getMailetInfo() {
        return "Sieve Mailet";
    }

    @Override
    public void init() throws MessagingException {
        sieveExecutor = SieveExecutor.builder()
            .resourceLocator(resourceLocator)
            .mailetContext(getMailetContext())
            .sievePoster(new SievePoster(usersRepository, MailboxConstants.INBOX))
            .build();
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        for (MailAddress recipient: mail.getRecipients()) {
            sieveExecutor.execute(recipient, mail);
        }
    }
}
