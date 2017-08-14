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

package org.apache.james.fetchmail;

import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class <code>StoreProcessor</code> connects to a message store, gets the
 * target Folder and delegates its processing to <code>FolderProcessor</code>.
 */
public class StoreProcessor extends ProcessorAbstract {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoreProcessor.class);

    /**
     * Constructor for StoreProcessor.
     * 
     * @param account
     */
    protected StoreProcessor(Account account) {
        super(account);
    }

    /**
     * Method process connects to a Folder in a Message Store, creates a
     * <code>FolderProcessor</code> and runs it to process the messages in the
     * Folder.
     * 
     * @see org.apache.james.fetchmail.ProcessorAbstract#process()
     */
    public void process() throws MessagingException {
        Store store = null;
        Folder folder;

        StringBuilder logMessageBuffer = new StringBuilder("Starting fetching mail from server '");
        logMessageBuffer.append(getHost());
        logMessageBuffer.append("' for user '");
        logMessageBuffer.append(getUser());
        logMessageBuffer.append("' in folder '");
        logMessageBuffer.append(getJavaMailFolderName());
        logMessageBuffer.append("'");
        LOGGER.info(logMessageBuffer.toString());

        try {
            // Get a Store object
            store = getSession().getStore(getJavaMailProviderName());

            // Connect
            if (getHost() != null || getUser() != null || getPassword() != null)
                store.connect(getHost(), getUser(), getPassword());
            else
                store.connect();

            // Get the Folder
            folder = store.getFolder(getJavaMailFolderName());
            if (folder == null)
                LOGGER.error(getFetchTaskName() + " No default folder");

            // Process the Folder
            new FolderProcessor(folder, getAccount()).process();

        } catch (MessagingException ex) {
            LOGGER.error("A MessagingException has terminated processing of this Folder", ex);
        } finally {
            try {
                if (null != store && store.isConnected())
                    store.close();
            } catch (MessagingException ex) {
                LOGGER.error("A MessagingException occured while closing the Store", ex);
            }
            logMessageBuffer = new StringBuilder("Finished fetching mail from server '");
            logMessageBuffer.append(getHost());
            logMessageBuffer.append("' for user '");
            logMessageBuffer.append(getUser());
            logMessageBuffer.append("' in folder '");
            logMessageBuffer.append(getJavaMailFolderName());
            logMessageBuffer.append("'");
            LOGGER.info(logMessageBuffer.toString());
        }
    }

}
