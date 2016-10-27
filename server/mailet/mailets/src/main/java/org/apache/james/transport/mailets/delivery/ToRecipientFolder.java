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

import com.google.common.collect.Iterators;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.transport.mailets.ResourceLocatorImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.GenericMailet;

import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.MessagingException;
import java.util.Arrays;
import java.util.Iterator;

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

    private MailboxManager mailboxManager;
    private SieveRepository sieveRepository;
    private UsersRepository usersRepository;

    @Inject
    public void setMailboxManager(@Named("mailboxmanager")MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    @Inject
    public void setSieveRepository(SieveRepository sieveRepository) {
        this.sieveRepository = sieveRepository;
    }

    @Inject
    public void setUsersRepository(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    private SieveMailet sieveMailet;  // Mailet that actually stores the message

    /**
     * Delivers a mail to a local mailbox in a given folder.
     * 
     * @see org.apache.mailet.base.GenericMailet#service(org.apache.mailet.Mail)
     */
    @Override
    public void service(Mail mail) throws MessagingException {
        if (!mail.getState().equals(Mail.GHOST)) {
            sieveMailet.service(mail);
        }
    }

    @Override
    public void init(MailetConfig mailetConfig) throws MessagingException {
        super.init(mailetConfig);
        sieveMailet = SieveMailet.builder()
            .mailboxManager(mailboxManager)
            .userRepository(usersRepository)
            .resourceLocator(ResourceLocatorImpl.instanciate(usersRepository, sieveRepository))
            .deliveryHeader("Delivered-To")
            .folder(getInitParameter(FOLDER_PARAMETER, "INBOX"))
            .consume(getInitParameter(CONSUME_PARAMETER, false))
            .verbose(getInitParameter("verbose", false))
            .quiet(getInitParameter("quiet", true))
            .build();
        sieveMailet.init(mailetConfig);
    }

    @Override
    public String getMailetInfo() {
        return ToRecipientFolder.class.getName() + " Mailet";
    }

}
