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
package org.apache.james.mailbox.mock;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;

/**
 * A mock mailbox manager.
 *
 */
public class MockMailboxManager {
    
    /**
     * The mock mailbox manager constructed based on a provided mailboxmanager.
     */
    private final MailboxManager mockMailboxManager;
    
    /**
     * Number of Domains to be created in the Mailbox Manager.
     */
    public static final int DOMAIN_COUNT = 3;
    
    /**
     * Number of Users (with INBOX) to be created in the Mailbox Manager.
     */
    public static final int USER_COUNT = 3;
    
    /**
     * Number of Sub Mailboxes (mailbox in INBOX) to be created in the Mailbox Manager.
     */
    public static final int SUB_MAILBOXES_COUNT = 3;
    
    /**
     * Number of Sub Sub Mailboxes (mailbox in a mailbox under INBOX) to be created in the Mailbox Manager.
     */
    public static final int SUB_SUB_MAILBOXES_COUNT = 3;
    
    /**
     * The expected Mailboxes count calculated based on the feeded mails.
     */
    public static final int EXPECTED_MAILBOXES_COUNT = DOMAIN_COUNT * 
                     (USER_COUNT + // INBOX
                      USER_COUNT * SUB_MAILBOXES_COUNT + // INBOX.SUB_FOLDER
                      USER_COUNT * SUB_MAILBOXES_COUNT * SUB_SUB_MAILBOXES_COUNT);  // INBOX.SUB_FOLDER.SUBSUB_FOLDER
    
    /**
     * Number of Messages per Mailbox to be created in the Mailbox Manager.
     */
    public static final int MESSAGE_PER_MAILBOX_COUNT = 3;
    
    /**
     * Construct a mock mailboxManager based on a valid mailboxManager.
     * The mailboxManager will be feeded with mailboxes and mails.
     * 
     * @param mailboxManager
     * @throws UnsupportedEncodingException 
     * @throws MailboxException 
     */
    public MockMailboxManager(MailboxManager mailboxManager) throws MailboxException, UnsupportedEncodingException {
        this.mockMailboxManager = mailboxManager;
        feedMockMailboxManager();
    }
    
    /**
     * @return
     */
    public MailboxManager getMockMailboxManager() {
        return mockMailboxManager;
    }
    
    /**
     * Utility method to feed the Mailbox Manager with a number of 
     * mailboxes and messages per mailbox.
     * 
     * @throws MailboxException
     * @throws UnsupportedEncodingException
     */
    private void feedMockMailboxManager() throws MailboxException, UnsupportedEncodingException {

        MailboxPath mailboxPath = null;
        
        for (int i=0; i < DOMAIN_COUNT; i++) {

            for (int j=0; j < USER_COUNT; j++) {
                
                String user = "user" + j + "@localhost" + i;
                
                String folderName = "INBOX";

                MailboxSession mailboxSession = getMockMailboxManager().createSystemSession(user);
                mailboxPath = new MailboxPath("#private", user, folderName);
                createMailbox(mailboxSession, mailboxPath);
                
                for (int k=0; k < SUB_MAILBOXES_COUNT; k++) {
                    
                    String subFolderName = folderName + ".SUB_FOLDER_" + k;
                    mailboxPath = new MailboxPath("#private", user, subFolderName);
                    createMailbox(mailboxSession, mailboxPath);
                    
                    for (int l=0; l < SUB_SUB_MAILBOXES_COUNT; l++) {

                        String subSubfolderName = subFolderName + ".SUBSUB_FOLDER_" + l;
                        mailboxPath = new MailboxPath("#private", user, subSubfolderName);
                        createMailbox(mailboxSession, mailboxPath);

                    }
                        
                }

                getMockMailboxManager().logout(mailboxSession, true);
        
            }
            
        }
        
    }
    
    /**
     * 
     * @param mailboxPath
     * @throws MailboxException
     * @throws UnsupportedEncodingException 
     */
    private void createMailbox(MailboxSession mailboxSession, MailboxPath mailboxPath) throws MailboxException, UnsupportedEncodingException {
        getMockMailboxManager().startProcessingRequest(mailboxSession);
        getMockMailboxManager().createMailbox(mailboxPath, mailboxSession);
        MessageManager messageManager = getMockMailboxManager().getMailbox(mailboxPath, mailboxSession);
        for (int j=0; j < MESSAGE_PER_MAILBOX_COUNT; j++) {
            messageManager.appendMessage(new ByteArrayInputStream(MockMail.MAIL_TEXT_PLAIN.getBytes("UTF-8")), 
                    Calendar.getInstance().getTime(), 
                    mailboxSession, 
                    true, 
                    new Flags(Flags.Flag.RECENT));
        }
        getMockMailboxManager().endProcessingRequest(mailboxSession);
    }
    
}
