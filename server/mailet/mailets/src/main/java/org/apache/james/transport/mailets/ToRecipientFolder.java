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

import static org.apache.james.transport.mailets.LocalDelivery.LOCAL_DELIVERED_MAILS_METRIC_NAME;

import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.MessagingException;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.transport.mailets.delivery.MailDispatcher;
import org.apache.james.transport.mailets.delivery.MailboxAppender;
import org.apache.james.transport.mailets.delivery.SimpleMailStore;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

/**
 * Receives a Mail from the Queue and takes care to deliver the message
 * to a defined folder of the recipient(s).
 * 
 * You have to define the folder name of the recipient(s).
 * The flag 'consume' will tell is the mail will be further
 * processed by the upcoming processor mailets, or not.
 * 
 * <pre>
 * &lt;mailet match="RecipientIsLocal" class="ToRecipientFolder"&gt;
 *    &lt;folder&gt; <i>Junk</i> &lt;/folder&gt;
 *    &lt;consume&gt; <i>false</i> &lt;/consume&gt;
 * &lt;/mailet&gt;
 * </pre>
 * 
 */
public class ToRecipientFolder extends GenericMailet {

    public static final String FOLDER_PARAMETER = "folder";
    public static final String CONSUME_PARAMETER = "consume";

    private final MailboxManager mailboxManager;
    private final UsersRepository usersRepository;
    private final MetricFactory metricFactory;
    private MailDispatcher mailDispatcher;

    @Inject
    public ToRecipientFolder(@Named("mailboxmanager")MailboxManager mailboxManager, UsersRepository usersRepository,
                             MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
        this.mailboxManager = mailboxManager;
        this.usersRepository = usersRepository;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        mailDispatcher.dispatch(mail);
    }

    @Override
    public void init() throws MessagingException {
        mailDispatcher = MailDispatcher.builder()
            .mailStore(SimpleMailStore.builder()
                .mailboxAppender(new MailboxAppender(mailboxManager))
                .usersRepository(usersRepository)
                .folder(getInitParameter(FOLDER_PARAMETER, MailboxConstants.INBOX))
                .metric(metricFactory.generate(LOCAL_DELIVERED_MAILS_METRIC_NAME))
                .build())
            .consume(getInitParameter(CONSUME_PARAMETER, false))
            .mailetContext(getMailetContext())
            .build();
    }

    @Override
    public String getMailetInfo() {
        return ToRecipientFolder.class.getName() + " Mailet";
    }

}
