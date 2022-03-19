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

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Class <code>FolderProcessor</code> opens a Folder and iterates over all of
 * the Messages, delegating their processing to <code>MessageProcessor</code>.
 * </p>
 * 
 * <p>
 * If isRecurse(), all subfolders are fetched recursively.
 * </p>
 */
public class FolderProcessor extends ProcessorAbstract {
    private static final Logger LOGGER = LoggerFactory.getLogger(FolderProcessor.class);

    /**
     * The fetched folder
     */
    private Folder fieldFolder;

    private Boolean fieldMarkSeenPermanent;

    /**
     * Constructor for FolderProcessor.
     * 
     * @param folder
     *            The folder to be fetched
     * @param account
     *            The account being processed
     */
    protected FolderProcessor(Folder folder, Account account) {
        super(account);
        setFolder(folder);
    }

    /**
     * Method process opens a Folder, fetches the Envelopes for all of its
     * Messages, creates a <code>MessageProcessor</code> and runs it to process
     * each message.
     */
    @Override
    public void process() throws MessagingException {
        int messagesProcessed = 0;
        int messageCount = 0;
        try {
            // open the folder
            try {
                open();
            } catch (MessagingException ex) {
                LOGGER.error("{} Failed to open folder!", getFetchTaskName());
                throw ex;
            }

            // Lock the folder while processing each message
            synchronized (getFolder()) {
                messageCount = getFolder().getMessageCount();
                for (int i = 1; i <= messageCount; i++) {
                    MimeMessage message = (MimeMessage) getFolder().getMessage(i);
                    if (isFetchAll() || !isSeen(message)) {
                        try {
                            new MessageProcessor(message, getAccount()).process();
                            messagesProcessed++;
                        } catch (Exception ex) {
                            // Catch and report an exception but don't rethrow it,
                            // allowing subsequent messages to be processed.
                            LOGGER.error("Exception processing message ID: {}", message.getMessageID(), ex);
                        }
                    }
                }
            }
        } catch (MessagingException mex) {
            LOGGER.error("A MessagingException has terminated fetching messages for this folder", mex);
        } finally {
            // Close the folder
            try {
                close();
            } catch (MessagingException ex) {
                // No-op
            }
            LOGGER.info("Processed {} messages of {} in folder '{}'", messagesProcessed, messageCount, getFolder().getName());
        }

        // Recurse through sub-folders if required
        try {
            if (isRecurse()) {
                recurse();
            }
        } catch (MessagingException mex) {
            LOGGER.error("A MessagingException has terminated recursing through sub-folders", mex);
        }
    }

    /**
     * Method close.
     * 
     * @throws MessagingException
     */
    protected void close() throws MessagingException {
        if (null != getFolder() && getFolder().isOpen()) {
            getFolder().close(true);
        }
    }

    /**
     * Method recurse.
     * 
     * @throws MessagingException
     */
    protected void recurse() throws MessagingException {
        if ((getFolder().getType() & Folder.HOLDS_FOLDERS) == Folder.HOLDS_FOLDERS) {
            // folder contains subfolders...
            Folder[] folders = getFolder().list();

            for (Folder folder : folders) {
                new FolderProcessor(folder, getAccount()).process();
            }

        }
    }

    /**
     * Method open.
     * 
     * @throws MessagingException
     */
    protected void open() throws MessagingException {
        int openFlag = Folder.READ_WRITE;

        if (isOpenReadOnly()) {
            openFlag = Folder.READ_ONLY;
        }

        getFolder().open(openFlag);
    }

    /**
     * Returns the folder.
     * 
     * @return Folder
     */
    protected Folder getFolder() {
        return fieldFolder;
    }

    /**
     * Answer if <code>aMessage</code> has been SEEN.
     * 
     * @param aMessage
     * @return boolean
     * @throws MessagingException
     */
    protected boolean isSeen(MimeMessage aMessage) throws MessagingException {
        boolean isSeen;
        if (isMarkSeenPermanent()) {
            isSeen = aMessage.isSet(Flags.Flag.SEEN);
        } else {
            isSeen = handleMarkSeenNotPermanent(aMessage);
        }
        return isSeen;
    }

    /**
     * Answer the result of computing markSeenPermanent.
     * 
     * @return Boolean
     */
    protected Boolean computeMarkSeenPermanent() {
        return getFolder().getPermanentFlags().contains(Flags.Flag.SEEN);
    }

    /**
     * <p>
     * Handler for when the folder does not support the SEEN flag. The default
     * behaviour implemented here is to answer the value of the SEEN flag
     * anyway.
     * </p>
     * 
     * <p>
     * Subclasses may choose to override this method and implement their own
     * solutions.
     * </p>
     * 
     * @param aMessage
     * @return boolean
     * @throws MessagingException
     */
    protected boolean handleMarkSeenNotPermanent(MimeMessage aMessage) throws MessagingException {
        return aMessage.isSet(Flags.Flag.SEEN);
    }

    /**
     * Sets the folder.
     * 
     * @param folder
     *            The folder to set
     */
    protected void setFolder(Folder folder) {
        fieldFolder = folder;
    }

    /**
     * Returns the isMarkSeenPermanent.
     * 
     * @return Boolean
     */
    protected Boolean isMarkSeenPermanent() {
        Boolean markSeenPermanent;
        if (null == (markSeenPermanent = isMarkSeenPermanentBasic())) {
            updateMarkSeenPermanent();
            return isMarkSeenPermanent();
        }
        return markSeenPermanent;
    }

    /**
     * Returns the markSeenPermanent.
     * 
     * @return Boolean
     */
    private Boolean isMarkSeenPermanentBasic() {
        return fieldMarkSeenPermanent;
    }

    /**
     * Sets the markSeenPermanent.
     * 
     * @param markSeenPermanent
     *            The isMarkSeenPermanent to set
     */
    protected void setMarkSeenPermanent(Boolean markSeenPermanent) {
        fieldMarkSeenPermanent = markSeenPermanent;
    }

    /**
     * Updates the markSeenPermanent.
     */
    protected void updateMarkSeenPermanent() {
        setMarkSeenPermanent(computeMarkSeenPermanent());
    }

}
