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

import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.MessagingException;

import org.apache.commons.logging.Log;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.MailboxManager;

import org.apache.james.transport.mailets.delivery.MailDispatcher;
import org.apache.james.transport.mailets.delivery.MailboxAppender;
import org.apache.james.transport.mailets.delivery.SimpleMailStorer;
import org.apache.james.transport.mailets.jsieve.CommonsLoggingAdapter;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

/**
 * Receives a Mail from the Queue and takes care of delivery of the
 * message to local inboxes.
 * 
 * This mailet is a composition of RecipientRewriteTable, SieveMailet 
 * and MailboxManager configured to mimic the old "LocalDelivery"
 * James 2.3 behavior.
 */
public class LocalDelivery extends GenericMailet {
    
    private org.apache.james.rrt.api.RecipientRewriteTable rrt;
    private UsersRepository usersRepository;
    private MailboxManager mailboxManager;
    private DomainList domainList;

    @Inject
    public void setRrt(org.apache.james.rrt.api.RecipientRewriteTable rrt) {
        this.rrt = rrt;
    }

    @Inject
    public void setUsersRepository(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }
    
    @Inject
    public void setMailboxManager(@Named("mailboxmanager") MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }
    
    @Inject
    public void setDomainList(DomainList domainList) {
        this.domainList = domainList;
    }

    private MailDispatcher mailDispatcher;  // Mailet that actually stores the message
    private RecipientRewriteTable recipientRewriteTable;  // Mailet that applies RecipientRewriteTable

    public void service(Mail mail) throws MessagingException {
        recipientRewriteTable.service(mail);
        if (!mail.getState().equals(Mail.GHOST)) {
            mailDispatcher.dispatch(mail);
        }
    }

    public String getMailetInfo() {
        return "Local Delivery Mailet";
    }

    public void init() throws MessagingException {
        recipientRewriteTable = new RecipientRewriteTable();
        recipientRewriteTable.setDomainList(domainList);
        recipientRewriteTable.setRecipientRewriteTable(rrt);
        recipientRewriteTable.init(getMailetConfig());
        Log log = CommonsLoggingAdapter.builder()
            .mailet(this)
            .quiet(getInitParameter("quiet", false))
            .verbose(getInitParameter("verbose", false))
            .build();
        mailDispatcher = MailDispatcher.builder()
            .mailStorer(SimpleMailStorer.builder()
                .mailboxAppender(new MailboxAppender(mailboxManager, getMailetContext()))
                .usersRepository(usersRepository)
                .folder("INBOX")
                .log(log)
                .build())
            .consume(getInitParameter("consume", true))
            .mailetContext(getMailetContext())
            .log(log)
            .build();
    }

}
