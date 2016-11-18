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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.commons.logging.Log;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.transport.mailets.delivery.MailStore;
import org.apache.james.transport.mailets.jsieve.CommonsLoggingAdapter;
import org.apache.james.transport.mailets.jsieve.ResourceLocator;
import org.apache.james.transport.mailets.jsieve.delivery.SieveExecutor;
import org.apache.james.transport.mailets.jsieve.delivery.SievePoster;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

/**
 * Execute Sieve scripts for incoming emails, and set the result of the execution as attributes of the mail
 */
public class Sieve extends GenericMailet {

    private final UsersRepository usersRepository;
    private final ResourceLocator resourceLocator;
    private SieveExecutor sieveExecutor;

    @Inject
    public Sieve(UsersRepository usersRepository, SieveRepository sieveRepository) throws MessagingException {
        this(usersRepository, ResourceLocatorImpl.instanciate(usersRepository, sieveRepository));
    }

    public Sieve(UsersRepository usersRepository, ResourceLocator resourceLocator) throws MessagingException {
        this.usersRepository = usersRepository;
        this.resourceLocator = resourceLocator;
    }

    @Override
    public String getMailetInfo() {
        return "Sieve Mailet";
    }

    @Override
    public void init() throws MessagingException {
        Log log = CommonsLoggingAdapter.builder()
            .wrappedLogger(getMailetContext().getLogger())
            .quiet(getInitParameter("quiet", false))
            .verbose(getInitParameter("verbose", false))
            .build();
        sieveExecutor = SieveExecutor.builder()
            .resourceLocator(resourceLocator)
            .usersRepository(usersRepository)
            .mailetContext(getMailetContext())
            .log(log)
            .sievePoster(new SievePoster(usersRepository, MailboxConstants.INBOX))
            .build();
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        for(MailAddress recipient: mail.getRecipients()) {
            executeSieveScript(mail, recipient);
        }
        mail.setRecipients(keepNonDiscardedRecipients(mail));
    }

    private void executeSieveScript(Mail mail, MailAddress recipient) {
        try {
            sieveExecutor.execute(recipient, mail);
        } catch (Exception e) {
            getMailetContext().getLogger().warn("Failed to execute Sieve script for user " + recipient.asPrettyString(), e);
        }
    }


    private ImmutableList<MailAddress> keepNonDiscardedRecipients(Mail mail) {
        final List<MailAddress> discardedRecipients = retrieveDiscardedRecipients(mail);
        return FluentIterable.from(mail.getRecipients()).filter(new Predicate<MailAddress>() {
                @Override
                public boolean apply(MailAddress input) {
                    return !discardedRecipients.contains(input);
                }
            }).toList();
    }

    private List<MailAddress> retrieveDiscardedRecipients(Mail mail) {
        final List<MailAddress> discardedRecipients = new ArrayList<MailAddress>();
        for(MailAddress recipient: mail.getRecipients()) {
            if (isDiscarded(mail, recipient)) {
                discardedRecipients.add(recipient);
            }
        }
        return discardedRecipients;
    }

    private boolean isDiscarded(Mail mail, MailAddress recipient) {
        return !(mail.getAttribute(MailStore.DELIVERY_PATH_PREFIX + retrieveUser(recipient)) instanceof String);
    }

    private String retrieveUser(MailAddress recipient) {
        try {
            return usersRepository.getUser(recipient);
        } catch (UsersRepositoryException e) {
            log("Can not retrieve username for mail address " + recipient.asPrettyString(), e);
            return recipient.asString();
        }
    }
}
