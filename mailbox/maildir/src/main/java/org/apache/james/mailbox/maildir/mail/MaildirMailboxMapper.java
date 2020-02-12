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
package org.apache.james.mailbox.maildir.mail;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.maildir.MaildirFolder;
import org.apache.james.mailbox.maildir.MaildirId;
import org.apache.james.mailbox.maildir.MaildirMessageName;
import org.apache.james.mailbox.maildir.MaildirStore;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedWildcard;
import org.apache.james.mailbox.store.MailboxExpressionBackwardCompatibility;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class MaildirMailboxMapper extends NonTransactionalMapper implements MailboxMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaildirMailboxMapper.class);
    /**
     * The {@link MaildirStore} the mailboxes reside in
     */
    private final MaildirStore maildirStore;

    private final MailboxSession session;
    
    public MaildirMailboxMapper(MaildirStore maildirStore, MailboxSession session) {
        this.maildirStore = maildirStore;
        this.session = session;
    }

    @Override
    public void delete(Mailbox mailbox) throws MailboxException {
        
        String folderName = maildirStore.getFolderName(mailbox);
        File folder = new File(folderName);
        if (folder.isDirectory()) {
            // Shouldn't fail on file deletion, else the mailbox will never be deleted
            if (mailbox.getName().equals(MailboxConstants.INBOX)) {
                // We must only delete cur, new, tmp and metadata for top INBOX mailbox.
                delete(new File(folder, MaildirFolder.CUR), 
                        new File(folder, MaildirFolder.NEW),
                        new File(folder, MaildirFolder.TMP),
                        new File(folder, MaildirFolder.UIDLIST_FILE),
                        new File(folder, MaildirFolder.VALIDITY_FILE));
            } else {
                // We simply delete all the folder for non INBOX mailboxes.
                delete(folder);
            }
        } else {
            throw new MailboxNotFoundException(mailbox.generateAssociatedPath());
        }
    }

    private void delete(File...files) {
        for (File file : files) {
            try {
                if (file.isDirectory()) {
                        FileUtils.deleteDirectory(file);
                } else {
                    FileUtils.forceDelete(file);
                }
            } catch (IOException e) {
                LOGGER.error("Error while deleting file {}", file, e);
            }
        }
    }
   
    @Override
    public Mailbox findMailboxByPath(MailboxPath mailboxPath)
            throws MailboxException, MailboxNotFoundException {      
        return maildirStore.loadMailbox(session, mailboxPath);
    }
    
    @Override
    public Mailbox findMailboxById(MailboxId id) throws MailboxException, MailboxNotFoundException {
        if (id == null) {
            throw new MailboxNotFoundException("null");
        }
        return list().stream()
            .filter(mailbox -> mailbox.getMailboxId().equals(id))
            .findAny()
            .orElseThrow(() -> new MailboxNotFoundException(id));
    }
    
    @Override
    public List<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query) throws MailboxException {
        String pathLike = MailboxExpressionBackwardCompatibility.getPathLike(query);
        final Pattern searchPattern = Pattern.compile("[" + MaildirStore.maildirDelimiter + "]"
                + pathLike.replace(".", "\\.").replace(MaildirStore.WILDCARD, ".*"));
        FilenameFilter filter = MaildirMessageName.createRegexFilter(searchPattern);
        File root = maildirStore.getMailboxRootForUser(query.getFixedUser());
        File[] folders = root.listFiles(filter);
        ArrayList<Mailbox> mailboxList = new ArrayList<>();
        for (File folder : folders) {
            if (folder.isDirectory()) {
                Mailbox mailbox = maildirStore.loadMailbox(session, root, query.getFixedNamespace(), query.getFixedUser(), folder.getName());
                mailboxList.add(mailbox);
            }
        }
        // INBOX is in the root of the folder
        if (Pattern.matches(pathLike.replace(MaildirStore.WILDCARD, ".*"), MailboxConstants.INBOX)) {
            Mailbox mailbox = maildirStore.loadMailbox(session, root, query.getFixedNamespace(), query.getFixedUser(), "");
            mailboxList.add(0, mailbox);
        }
        return mailboxList.stream()
            .filter(query::matches)
            .collect(Guavate.toImmutableList());
    }

    @Override
    public boolean hasChildren(Mailbox mailbox, char delimiter) throws MailboxException, MailboxNotFoundException {
        List<Mailbox> mailboxes = findMailboxWithPathLike(
            MailboxQuery.builder()
            .userAndNamespaceFrom(mailbox.generateAssociatedPath())
            .expression(new PrefixedWildcard(mailbox.getName() + delimiter))
            .build()
            .asUserBound());
        return mailboxes.size() > 0;
    }

    @Override
    public MailboxId create(Mailbox mailbox) throws MailboxException {
        Preconditions.checkArgument(mailbox.getMailboxId() == null, "A mailbox we want to create should not have a mailboxId set already");

        MaildirId maildirId = MaildirId.random();
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);

        if (!folder.exists()) {
            boolean folderExist = folder.getRootFile().exists();
            if (!folderExist && !folder.getRootFile().mkdirs()) {
                throw new MailboxException("Failed to save Mailbox " + mailbox);
            }

            boolean isCreated = folder.getCurFolder().mkdir()
                && folder.getNewFolder().mkdir()
                && folder.getTmpFolder().mkdir();
            if (!isCreated) {
                throw new MailboxException("Failed to save Mailbox " + mailbox, new IOException("Needed folder structure can not be created"));
            }
        }

        try {
            folder.setUidValidity(mailbox.getUidValidity());
            folder.setMailboxId(maildirId);
            mailbox.setMailboxId(maildirId);
        } catch (IOException ioe) {
            throw new MailboxException("Failed to save Mailbox " + mailbox, ioe);

        }
        folder.setACL(mailbox.getACL());

        return maildirId;
    }

    @Override
    public MailboxId rename(Mailbox mailbox) throws MailboxException {
        MaildirId maildirId = Optional.ofNullable(mailbox.getMailboxId())
            .map(mailboxId -> (MaildirId) mailboxId)
            .orElseGet(MaildirId::random);
        try {
            Mailbox originalMailbox = findMailboxById(mailbox.getMailboxId());
            MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
            // equals with null check
            if (originalMailbox.getName() == null ? mailbox.getName() != null : !originalMailbox.getName().equals(mailbox.getName())) {
                if (folder.exists()) {
                    throw new MailboxExistsException(mailbox.getName());
                }
                
                MaildirFolder originalFolder = maildirStore.createMaildirFolder(originalMailbox);
                // renaming the INBOX means to move its contents to the new folder 
                if (originalMailbox.getName().equals(MailboxConstants.INBOX)) {
                    try {
                        File inboxFolder = originalFolder.getRootFile();
                        File newFolder = folder.getRootFile();
                        FileUtils.forceMkdir(newFolder);
                        if (!originalFolder.getCurFolder().renameTo(folder.getCurFolder())) {
                            throw new IOException("Could not rename folder " + originalFolder.getCurFolder() + " to " + folder.getCurFolder());
                        }
                        if (!originalFolder.getMailboxIdFile().renameTo(folder.getMailboxIdFile())) {
                            throw new IOException("Could not rename folder " + originalFolder.getCurFolder() + " to " + folder.getCurFolder());
                        }
                        if (!originalFolder.getNewFolder().renameTo(folder.getNewFolder())) {
                            throw new IOException("Could not rename folder " + originalFolder.getNewFolder() + " to " + folder.getNewFolder());
                        }
                        if (!originalFolder.getTmpFolder().renameTo(folder.getTmpFolder())) {
                            throw new IOException("Could not rename folder " + originalFolder.getTmpFolder() + " to " + folder.getTmpFolder());
                        }
                        File oldUidListFile = new File(inboxFolder, MaildirFolder.UIDLIST_FILE);
                        File newUidListFile = new File(newFolder, MaildirFolder.UIDLIST_FILE);
                        if (!oldUidListFile.renameTo(newUidListFile)) {
                            throw new IOException("Could not rename file " + oldUidListFile + " to " + newUidListFile);
                        }
                        File oldValidityFile = new File(inboxFolder, MaildirFolder.VALIDITY_FILE);
                        File newValidityFile = new File(newFolder, MaildirFolder.VALIDITY_FILE);
                        if (!oldValidityFile.renameTo(newValidityFile)) {
                            throw new IOException("Could not rename file " + oldValidityFile + " to " + newValidityFile);
                        }
                        // recreate the INBOX folders, uidvalidity and uidlist will
                        // automatically be recreated later
                        FileUtils.forceMkdir(originalFolder.getCurFolder());
                        FileUtils.forceMkdir(originalFolder.getNewFolder());
                        FileUtils.forceMkdir(originalFolder.getTmpFolder());
                        originalFolder.setMailboxId(MaildirId.random());
                    } catch (IOException e) {
                        throw new MailboxException("Failed to save Mailbox " + mailbox, e);
                    }
                } else {
                    if (!originalFolder.getRootFile().renameTo(folder.getRootFile())) {
                        throw new MailboxException("Failed to save Mailbox " + mailbox,
                            new IOException("Could not rename folder " + originalFolder));
                    }
                }
            }
            folder.setACL(mailbox.getACL());
        } catch (MailboxNotFoundException e) {
            // it cannot be found and is thus new
            MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
            if (!folder.exists()) {
                boolean success = folder.getRootFile().exists();
                if (!success) {
                    success = folder.getRootFile().mkdirs();
                }
                if (!success) {
                    throw new MailboxException("Failed to save Mailbox " + mailbox);
                }
                success = folder.getCurFolder().mkdir();
                success = success && folder.getNewFolder().mkdir();
                success = success && folder.getTmpFolder().mkdir();
                if (!success) {
                    throw new MailboxException("Failed to save Mailbox " + mailbox, new IOException("Needed folder structure can not be created"));
                }

            }
            try {
                folder.setUidValidity(mailbox.getUidValidity());
                folder.setMailboxId(maildirId);
                mailbox.setMailboxId(maildirId);
            } catch (IOException ioe) {
                throw new MailboxException("Failed to save Mailbox " + mailbox, ioe);

            }
            folder.setACL(mailbox.getACL());
        }
        return maildirId;
    }

    @Override
    public List<Mailbox> list() throws MailboxException {
        
       File maildirRoot = maildirStore.getMaildirRoot();
       List<Mailbox> mailboxList = new ArrayList<>();
        


       if (maildirStore.getMaildirLocation().endsWith("/" + MaildirStore.PATH_DOMAIN + "/" + MaildirStore.PATH_USER)) {
           File[] domains = maildirRoot.listFiles();
           for (File domain : domains) {
               File[] users = domain.listFiles();
               visitUsersForMailboxList(domain, users, mailboxList);
           }
           return mailboxList;
       }

        File[] users = maildirRoot.listFiles();
        visitUsersForMailboxList(null, users, mailboxList);
        return mailboxList;
        
    }

    @Override
    public void endRequest() {

    }

    private void visitUsersForMailboxList(File domain, File[] users, List<Mailbox> mailboxList) throws MailboxException {
        
        String userName = null;
        
        for (File user: users) {
            
            
            if (domain == null) {
                userName = user.getName();
            } else {
                userName = user.getName() + "@" + domain.getName();
            }
            
            // Special case for INBOX: Let's use the user's folder.
            MailboxPath inboxMailboxPath = MailboxPath.forUser(Username.of(userName), MailboxConstants.INBOX);
            mailboxList.add(maildirStore.loadMailbox(session, inboxMailboxPath));
            
            // List all INBOX sub folders.
            
            File[] mailboxes = user.listFiles(pathname -> pathname.getName().startsWith("."));
            
            for (File mailbox: mailboxes) {
               
                
                MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, 
                        Username.of(userName),
                        mailbox.getName().substring(1));
                mailboxList.add(maildirStore.loadMailbox(session, mailboxPath));

            }

        }
        
    }

    @Override
    public ACLDiff updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        MailboxACL oldACL = mailbox.getACL();
        MailboxACL newACL = mailbox.getACL().apply(mailboxACLCommand);
        folder.setACL(newACL);
        mailbox.setACL(newACL);
        return ACLDiff.computeDiff(oldACL, newACL);
    }

    @Override
    public ACLDiff setACL(Mailbox mailbox, MailboxACL mailboxACL) throws MailboxException {
        MailboxACL oldAcl = mailbox.getACL();
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        folder.setACL(mailboxACL);
        mailbox.setACL(mailboxACL);
        return ACLDiff.computeDiff(oldAcl, mailboxACL);
    }

    @Override
    public List<Mailbox> findNonPersonalMailboxes(Username userName, Right right) throws MailboxException {
        return ImmutableList.of();
    }
}
