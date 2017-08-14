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
package org.apache.james.mailbox.copier;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.mail.Flags.Flag;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;
import org.apache.james.mailbox.store.streaming.InputStreamContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the {@link MailboxCopier} interface.
 * 
 */
public class MailboxCopierImpl implements MailboxCopier {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxCopierImpl.class.getName());

    private final static FetchGroup GROUP = new FetchGroup() {

        @Override
        public int content() {
            return FULL_CONTENT;
        }

        @Override
        public Set<PartContentDescriptor> getPartContentDescriptors() {
            return new HashSet<>();
        }
        
    };

    /**
     * @see org.apache.james.mailbox.copier.MailboxCopier#copyMailboxes(org.apache.james.mailbox.MailboxManager, org.apache.james.mailbox.MailboxManager)
     */
    public void copyMailboxes(MailboxManager srcMailboxManager, MailboxManager dstMailboxManager) throws MailboxException, IOException {
        
        Calendar start = Calendar.getInstance();

        MailboxSession srcMailboxSession;
        MailboxSession dstMailboxSession;

        List<MailboxPath> mailboxPathList = null;

        srcMailboxSession = srcMailboxManager.createSystemSession("manager");
        srcMailboxManager.startProcessingRequest(srcMailboxSession);
        mailboxPathList = srcMailboxManager.list(srcMailboxSession);
        srcMailboxManager.endProcessingRequest(srcMailboxSession);

        LOGGER.info("Found " + mailboxPathList.size() + " mailboxes in source mailbox manager.");
        for (int i=0; i < mailboxPathList.size(); i++) {
            LOGGER.info("Mailbox#" + i + " path=" + mailboxPathList.get(i));
        }

        MailboxPath mailboxPath = null;
        
        for (int i=0; i < mailboxPathList.size(); i++) {
        
            mailboxPath = mailboxPathList.get(i);
            
            if ((mailboxPath.getName() != null) && (mailboxPath.getName().trim().length() > 0)) {
                
                LOGGER.info("Ready to copy source mailbox path=" + mailboxPath.toString());

                srcMailboxSession = srcMailboxManager.createSystemSession(mailboxPath.getUser());
                dstMailboxSession = dstMailboxManager.createSystemSession(mailboxPath.getUser());

                dstMailboxManager.startProcessingRequest(dstMailboxSession);
                try {
                    dstMailboxManager.createMailbox(mailboxPath, dstMailboxSession);
                    LOGGER.info("Destination mailbox " + i + "/" + mailboxPathList.size()
                            + " created with path=" + mailboxPath.toString()
                            + " after " + (Calendar.getInstance().getTimeInMillis() - start.getTimeInMillis()) + " ms.");
                } catch (MailboxExistsException e) {
                    LOGGER.error("Mailbox " + i + " with path=" + mailboxPath.toString() + " already exists.", e);
                }
                dstMailboxManager.endProcessingRequest(dstMailboxSession);

                srcMailboxManager.startProcessingRequest(srcMailboxSession);
                MessageManager srcMessageManager = srcMailboxManager.getMailbox(mailboxPath, srcMailboxSession);
                srcMailboxManager.endProcessingRequest(srcMailboxSession);

                dstMailboxManager.startProcessingRequest(dstMailboxSession);
                MessageManager dstMessageManager = dstMailboxManager.getMailbox(mailboxPath, dstMailboxSession);

                int j=0;
                Iterator<MessageResult> messageResultIterator = srcMessageManager.getMessages(MessageRange.all(), GROUP, srcMailboxSession);
                
                while (messageResultIterator.hasNext()) {

                    MessageResult messageResult = messageResultIterator.next();
                    InputStreamContent content = (InputStreamContent) messageResult.getFullContent();

                    dstMailboxManager.startProcessingRequest(dstMailboxSession);
                    dstMessageManager.appendMessage(content.getInputStream(), messageResult.getInternalDate(), dstMailboxSession, messageResult.getFlags().contains(Flag.RECENT), messageResult.getFlags());
                    dstMailboxManager.endProcessingRequest(dstMailboxSession);
                    LOGGER.info("MailboxMessage #" + j + " appended in destination mailbox with path=" + mailboxPath.toString());
                    j++;

                }
                dstMailboxManager.endProcessingRequest(dstMailboxSession);

            }
            
            else {
                
                LOGGER.info("Destination mailbox " + i + "/" + mailboxPathList.size()
                        + " with path=" + mailboxPath.toString()
                        + " has a null or empty name");

            }

        }

        LOGGER.info("Mailboxes copied in " + (Calendar.getInstance().getTimeInMillis() - start.getTimeInMillis()) + " ms.");

    }
}
