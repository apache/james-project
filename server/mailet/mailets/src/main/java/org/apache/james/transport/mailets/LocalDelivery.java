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

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.MessagingException;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.transport.mailets.delivery.MailDispatcher;
import org.apache.james.transport.mailets.delivery.MailboxAppenderImpl;
import org.apache.james.transport.mailets.delivery.SimpleMailStore;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.MailetUtil;

/**
 * Receives a Mail from the Queue and takes care of delivery of the
 * message to local inboxes.
 * 
 * This mailet is a composition of RecipientRewriteTable, SieveMailet 
 * and MailboxManager configured to mimic the old "LocalDelivery"
 * James 2.3 behavior.
 */
public class LocalDelivery extends GenericMailet {

    public static final String LOCAL_DELIVERED_MAILS_METRIC_NAME = "localDeliveredMails";
    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;
    private final MetricFactory metricFactory;
    private MailDispatcher mailDispatcher;

    @Inject
    public LocalDelivery(UsersRepository usersRepository, @Named("mailboxmanager") MailboxManager mailboxManager,
                         MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        mailDispatcher.dispatch(mail);
    }

    @Override
    public String getMailetInfo() {
        return "Local Delivery Mailet";
    }

    @Override
    public void init() throws MessagingException {
        mailDispatcher = MailDispatcher.builder()
            .mailStore(SimpleMailStore.builder()
                .mailboxAppender(new MailboxAppenderImpl(mailboxManager))
                .usersRepository(usersRepository)
                .folder(MailboxConstants.INBOX)
                .metric(metricFactory.generate(LOCAL_DELIVERED_MAILS_METRIC_NAME))
                .build())
            .consume(getInitParameter("consume", true))
            .retries(MailetUtil.getInitParameterAsInteger(getInitParameter("retries"), Optional.of(MailDispatcher.RETRIES)))
            .mailetContext(getMailetContext())
            .build();
    }

}
