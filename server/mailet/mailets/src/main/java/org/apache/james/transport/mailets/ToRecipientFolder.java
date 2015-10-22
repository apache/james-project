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
import java.util.Collection;
import java.util.Iterator;

import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.MessagingException;

import org.apache.commons.collections.iterators.IteratorChain;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetContext;
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

    private MailboxManager mailboxManager;

    private UsersRepository usersRepository;

    private FileSystem fileSystem;

    @Inject
    public void setMailboxManager(@Named("mailboxmanager")MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    @Inject
    public void setUsersRepository(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Inject
    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
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

    /* (non-Javadoc)
     * @see org.apache.mailet.base.GenericMailet#init()
     */
    @Override
    public void init() throws MessagingException {
        super.init();
        sieveMailet = new SieveMailet();
        sieveMailet.setUsersRepository(usersRepository);
        sieveMailet.setMailboxManager(mailboxManager);
        sieveMailet.setFileSystem(fileSystem);
        sieveMailet.init(new MailetConfig() {
            /*
             * @see org.apache.mailet.MailetConfig#getInitParameter(java.lang.String)
             */
            public String getInitParameter(String name) {
                if ("addDeliveryHeader".equals(name)) {
                    return "Delivered-To";
                } else if ("resetReturnPath".equals(name)) {
                    return "true";
                } else {
                    return getMailetConfig().getInitParameter(name);
                }
            }
            /*
             * @see org.apache.mailet.MailetConfig#getInitParameterNames()
             */
            public Iterator<String> getInitParameterNames() {
                IteratorChain c = new IteratorChain();
                Collection<String> h = new ArrayList<String>();
                h.add("addDeliveryHeader");
                h.add("resetReturnPath");
                c.addIterator(getMailetConfig().getInitParameterNames());
                c.addIterator(h.iterator());
                return c;
            }
            /*
             * @see org.apache.mailet.MailetConfig#getMailetContext()
             */
            public MailetContext getMailetContext() {
                return getMailetConfig().getMailetContext();
            }
            /*
             * @see org.apache.mailet.MailetConfig#getMailetName()
             */
            public String getMailetName() {
                return getMailetConfig().getMailetName();
            }

        });
        // Override the default value of "quiet"
        sieveMailet.setQuiet(getInitParameter("quiet", true));
        sieveMailet.setFolder(getInitParameter("folder", "INBOX"));
    }

    /* (non-Javadoc)
     * @see org.apache.mailet.base.GenericMailet#getMailetInfo()
     */
    @Override
    public String getMailetInfo() {
        return ToRecipientFolder.class.getName() + " Mailet";
    }

}
