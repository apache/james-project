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
package org.apache.james.mailbox.maildir;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;

public class MaildirStore implements UidProvider, ModSeqProvider {

    public static final String PATH_USER = "%user";
    public static final String PATH_DOMAIN = "%domain";
    public static final String PATH_FULLUSER = "%fulluser";
    public static final String WILDCARD = "%";
    
    public static final String maildirDelimiter = ".";
    
    private final String maildirLocation;
    
    private File maildirRootFile;
    private final MailboxPathLocker locker;

    private boolean messageNameStrictParse = false;

    /**
     * Construct a MaildirStore with a location. The location String
     * currently may contain the
     * %user,
     * %domain,
     * %fulluser
     * variables.
     * @param maildirLocation A String with variables
     */
    public MaildirStore(String maildirLocation, MailboxPathLocker locker) {
        this.maildirLocation = maildirLocation;
        this.locker = locker;
    }
    
    public MaildirStore(String maildirLocation) {
        this(maildirLocation, new JVMMailboxPathLocker());
    }
    
    
    public String getMaildirLocation() {
        return maildirLocation;
    }
    
    /**
     * Create a {@link MaildirFolder} for a mailbox
     *
     * @return The MaildirFolder
     */
    public MaildirFolder createMaildirFolder(Mailbox mailbox) {
        MaildirFolder mf = new MaildirFolder(getFolderName(mailbox), mailbox.generateAssociatedPath(), locker);
        mf.setMessageNameStrictParse(isMessageNameStrictParse());
        return mf;
    }

    /**
     * Creates a Mailbox object with data loaded from the file system
     * @param root The main maildir folder containing the mailbox to load
     * @param namespace The namespace to use
     * @param user The owner of this mailbox
     * @param folderName The name of the mailbox folder
     * @return The Mailbox object populated with data from the file system
     * @throws MailboxException If the mailbox folder doesn't exist or can't be read
     */
    public Mailbox loadMailbox(MailboxSession session, File root, String namespace, Username user, String folderName) throws MailboxException {
        String mailboxName = getMailboxNameFromFolderName(folderName);
        return loadMailbox(session, new File(root, folderName), new MailboxPath(namespace, user, mailboxName));
    }

    /**
     * Creates a Mailbox object with data loaded from the file system
     * @param mailboxPath The path of the mailbox
     * @return The Mailbox object populated with data from the file system
     * @throws MailboxNotFoundException If the mailbox folder doesn't exist
     * @throws MailboxException If the mailbox folder can't be read
     */
    public Mailbox loadMailbox(MailboxSession session, MailboxPath mailboxPath)
    throws MailboxNotFoundException, MailboxException {
        MaildirFolder folder = new MaildirFolder(getFolderName(mailboxPath), mailboxPath, locker);
        folder.setMessageNameStrictParse(isMessageNameStrictParse());
        if (!folder.exists()) {
            throw new MailboxNotFoundException(mailboxPath);
        }
        return loadMailbox(session, folder.getRootFile(), mailboxPath);
    }

    /**
     * Creates a Mailbox object with data loaded from the file system
     * @param mailboxFile File object referencing the folder for the mailbox
     * @param mailboxPath The path of the mailbox
     * @return The Mailbox object populated with data from the file system
     * @throws MailboxException If the mailbox folder doesn't exist or can't be read
     */
    private Mailbox loadMailbox(MailboxSession session, File mailboxFile, MailboxPath mailboxPath) throws MailboxException {
        MaildirFolder folder = new MaildirFolder(mailboxFile.getAbsolutePath(), mailboxPath, locker);
        folder.setMessageNameStrictParse(isMessageNameStrictParse());
        try {
            Mailbox loadedMailbox = new Mailbox(mailboxPath, folder.getUidValidity(), folder.readMailboxId());
            loadedMailbox.setACL(folder.getACL());
            return loadedMailbox;
        } catch (IOException e) {
            throw new MailboxException("Unable to load Mailbox " + mailboxPath, e);
        }
    }
    
    /**
     * Inserts the user name parts in the general maildir location String
     * @param user The user to get the root for.
     * @return The name of the folder which contains the specified user's mailbox
     */
    public String userRoot(Username user) {
        String userName = user.asString();
        String path = maildirLocation.replace(PATH_FULLUSER, userName);
        String[] userParts = userName.split("@");
        if (userParts.length == 2) {
            userName = userParts[0];
            // At least the domain part should not handled in a case-sensitive manner
            // See MAILBOX-58
            path = path.replace(PATH_DOMAIN, userParts[1].toLowerCase(Locale.US));
        }
        path = path.replace(PATH_USER, userName);
        return path;
    }
    
    /**
     * The main maildir folder containing all mailboxes for one user
     * @param user The user name of a mailbox
     * @return A File object referencing the main maildir folder
     * @throws MailboxException If the folder does not exist or is no directory
     */
    public File getMailboxRootForUser(Username user) throws MailboxException {
        String path = userRoot(user);
        File root = new File(path);
        if (!root.isDirectory()) {
            throw new MailboxException("Unable to load Mailbox for user " + user);
        }
        return root;
    }
    
    /**
     * Return a File which is the root of all Maidirs.
     * The returned maidirRootFile is lazilly constructured.
     * 
     * @return maidirRootFile
     */
    public File getMaildirRoot() {
        if (maildirRootFile == null) {
            String maildirRootLocation = maildirLocation.replaceAll(PATH_FULLUSER, "");
            maildirRootLocation = maildirRootLocation.replaceAll(PATH_DOMAIN, "");
            maildirRootLocation = maildirRootLocation.replaceAll(PATH_USER, "");
            maildirRootFile = new File(maildirRootLocation);
        }        
        return maildirRootFile;
    }

    /**
     * Transforms a folder name into a mailbox name
     * @param folderName The name of the mailbox folder
     * @return The complete (namespace) name of a mailbox
     */
    public String getMailboxNameFromFolderName(String folderName) {
        String mName;
        if (folderName.equals("")) {
            mName = MailboxConstants.INBOX;
        } else {
            // remove leading dot
            mName = folderName.substring(1);
        }
        // they are equal, anyways, this might change someday...
        //if (maildirDelimiter != MailboxConstants.DEFAULT_DELIMITER_STRING)
        //    mName = mName.replace(maildirDelimiter, MailboxConstants.DEFAULT_DELIMITER_STRING);
        return mName;
    }
    
    /**
     * Get the absolute name of the folder for a specific mailbox
     * @param namespace The namespace of the mailbox
     * @param user The user of the mailbox
     * @param name The name of the mailbox
     * @return absolute name
     */
    public String getFolderName(String namespace, Username user, String name) {
        String root = userRoot(user);
        // if INBOX => location == maildirLocation
        if (name.equals(MailboxConstants.INBOX)) {
            return root;
        }
        StringBuilder folder = new StringBuilder(root);
        if (!root.endsWith(File.pathSeparator)) {
            folder.append(File.separator);
        }
        folder.append(".");
        folder.append(name);
        return folder.toString();
    }
    
    /**
     * Get the absolute name of the folder for a specific mailbox
     * @param mailbox The mailbox
     * @return The absolute path to the folder containing the mailbox
     */
    public String getFolderName(Mailbox mailbox) {
        return getFolderName(mailbox.generateAssociatedPath());
    }
    
    /**
     * Get the absolute name of the folder for a specific mailbox
     * @param mailboxPath The MailboxPath
     * @return The absolute path to the folder containing the mailbox
     */
    public String getFolderName(MailboxPath mailboxPath) {
        return getFolderName(mailboxPath.getNamespace(), mailboxPath.getUser(), mailboxPath.getName());
    }

    @Override
    public MessageUid nextUid(Mailbox mailbox) throws MailboxException {
        try {
            return createMaildirFolder(mailbox).getLastUid()
                .map(MessageUid::next)
                .orElse(MessageUid.MIN_VALUE);
        } catch (MailboxException e) {
            throw new MailboxException("Unable to generate next uid", e);
        }
    }

    @Override
    public ModSeq nextModSeq(Mailbox mailbox) {
        return ModSeq.of(System.currentTimeMillis());
    }

    @Override
    public ModSeq highestModSeq(Mailbox mailbox) throws MailboxException {
        try {
            return createMaildirFolder(mailbox).getHighestModSeq();
        } catch (IOException e) {
            throw new MailboxException("Unable to get highest mod-sequence for mailbox", e);
        }
    }

    @Override
    public Optional<MessageUid> lastUid(Mailbox mailbox) throws MailboxException {
       return createMaildirFolder(mailbox).getLastUid();
    }

    /**
     * Returns whether the names of message files in this store are parsed in
     * a strict manner ({@code true}), which means a size field and flags are
     * expected.
     */
    public boolean isMessageNameStrictParse() {
        return messageNameStrictParse;
    }

    /**
     * Specifies whether the names of message files in this store are parsed in
     * a strict manner ({@code true}), which means a size field and flags are
     * expected.
     *
     * Default is {@code false}.
     */
    public void setMessageNameStrictParse(boolean messageNameStrictParse) {
        this.messageNameStrictParse = messageNameStrictParse;
    }

    @Override
    public ModSeq nextModSeq(MailboxId mailboxId) {
        return ModSeq.of(System.currentTimeMillis());
    }

    @Override
    public MessageUid nextUid(MailboxId mailboxId) throws MailboxException {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public ModSeq highestModSeq(MailboxId mailboxId) {
        throw new NotImplementedException("Not implemented");
    }
}
