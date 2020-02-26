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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxPathLocker.LockAwareExecution;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaildirFolder {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaildirFolder.class);

    public static final String VALIDITY_FILE = "james-uidvalidity";
    public static final String UIDLIST_FILE = "james-uidlist";
    public static final String ACL_FILE = "james-acl";
    public static final String MAILBOX_ID_FILE = "james-mailboxId";
    public static final String CUR = "cur";
    public static final String NEW = "new";
    public static final String TMP = "tmp";
    
    private final File rootFolder;
    private final File curFolder;
    private final File newFolder;
    private final File tmpFolder;
    private final File uidFile;
    private final File aclFile;
    private final File mailboxIdFile;

    private Optional<MessageUid> lastUid;
    private int messageCount = 0;
    private Optional<UidValidity> uidValidity = Optional.empty();
    private MailboxACL acl;
    private boolean messageNameStrictParse = false;

    private final MailboxPathLocker locker;

    private final MailboxPath path;
    
    /**
     * Representation of a maildir folder containing the message folders
     * and some special files
     * @param absPath The absolute path of the mailbox folder
     */
    public MaildirFolder(String absPath, MailboxPath path, MailboxPathLocker locker) {
        this.rootFolder = new File(absPath);
        this.curFolder = new File(rootFolder, CUR);
        this.newFolder = new File(rootFolder, NEW);
        this.tmpFolder = new File(rootFolder, TMP);
        this.uidFile = new File(rootFolder, UIDLIST_FILE);
        this.aclFile = new File(rootFolder, ACL_FILE);
        this.mailboxIdFile = new File(rootFolder, MAILBOX_ID_FILE);
        this.locker = locker;
        this.path = path;
        this.lastUid = Optional.empty();
    }

    private MaildirMessageName newMaildirMessageName(MaildirFolder folder, String fullName) {
        MaildirMessageName mdn = new MaildirMessageName(folder, fullName);
        mdn.setMessageNameStrictParse(isMessageNameStrictParse());
        return mdn;
    }

    /**
     * Returns whether the names of message files in this folder are parsed in
     * a strict manner ({@code true}), which means a size field and flags are
     * expected.
     */
    public boolean isMessageNameStrictParse() {
        return messageNameStrictParse;
    }

    /**
     * Specifies whether the names of message files in this folder are parsed in
     * a strict manner ({@code true}), which means a size field and flags are
     * expected.
     *
     */
    public void setMessageNameStrictParse(boolean messageNameStrictParse) {
        this.messageNameStrictParse = messageNameStrictParse;
    }

    /**
     * Returns the {@link File} of this Maildir folder.
     * @return the root folder
     */
    public File getRootFile() {
        return rootFolder;
    }

    /**
     * Tests whether the directory belonging to this {@link MaildirFolder} exists 
     * @return true if the directory belonging to this {@link MaildirFolder} exists ; false otherwise 
     */
    public boolean exists() {
        return rootFolder.isDirectory() && curFolder.isDirectory() && newFolder.isDirectory() && tmpFolder.isDirectory();
    }
    
    /**
     * Checks whether the folder's contents have been changed after
     * the uidfile has been created.
     * @return true if the contents have been changed.
     */
    private boolean isModified() {
        long uidListModified = uidFile.lastModified();
        long curModified = curFolder.lastModified();
        long newModified = newFolder.lastModified();
        // because of bad time resolution of file systems we also check "equals"
        if (curModified >= uidListModified || newModified >= uidListModified) {
            return true;
        }
        return false;
    }
    
    /**
     * Returns the ./cur folder of this Maildir folder.
     * @return the <code>./cur</code> folder
     */
    public File getCurFolder() {
        return curFolder;
    }

    public File getMailboxIdFile() {
        return mailboxIdFile;
    }
    
    /**
     * Returns the ./new folder of this Maildir folder.
     * @return the <code>./new</code> folder
     */
    public File getNewFolder() {
        return newFolder;
    }
    
    /**
     * Returns the ./tmp folder of this Maildir folder.
     * @return the <code>./tmp</code> folder
     */
    public File getTmpFolder() {
        return tmpFolder;
    }
    
    /**
     * Returns the nextUid value and increases it.
     */
    private MessageUid getNextUid() {
        MessageUid nextUid = lastUid.map(MessageUid::next).orElse(MessageUid.MIN_VALUE);
        lastUid = Optional.of(nextUid);
        return nextUid;
    }
    
    /**
     * Returns the last uid used in this mailbox
     */
    public Optional<MessageUid> getLastUid() throws MailboxException {
        if (!lastUid.isPresent()) {
            readLastUid();
        }
        return lastUid;
    }
    
    public ModSeq getHighestModSeq() throws IOException {
        long newModified = getNewFolder().lastModified();
        long curModified = getCurFolder().lastModified();
        if (newModified  == 0L && curModified == 0L) {
            throw new IOException("Unable to read highest modSeq");
        }
        return ModSeq.of(Math.max(newModified, curModified));
    }

    /**
     * Read the lastUid of the given mailbox from the file system.
     *
     * @throws MailboxException if there are problems with the uidList file
     */
    private void readLastUid() throws MailboxException {
        locker.executeWithLock(path,
            (LockAwareExecution<Void>) () -> {
            File uidList = uidFile;

            if (!uidList.exists()) {
                createUidFile();
            }
            try (FileReader fileReader = new FileReader(uidList);
                 BufferedReader reader = new BufferedReader(fileReader)) {

                String line = reader.readLine();
                if (line != null) {
                    readUidListHeader(line);
                }
                return null;
            } catch (IOException e) {
                throw new MailboxException("Unable to read last uid", e);
            }
        }, MailboxPathLocker.LockType.Write);
        
        
    }

    /**
     * Returns the uidValidity of this mailbox
     * @return The uidValidity
     */
    public UidValidity getUidValidity() throws IOException {
        if (!uidValidity.isPresent()) {
            uidValidity = Optional.of(readUidValidity());
        }
        return uidValidity.get();
    }
    
    /**
     * Sets the uidValidity for this mailbox and writes it to the file system
     */
    public void setUidValidity(UidValidity uidValidity) throws IOException {
        saveUidValidity(uidValidity);
        this.uidValidity = Optional.of(uidValidity);
    }

    /**
     * Read the uidValidity of the given mailbox from the file system.
     * If the respective file is not yet there, it gets created and
     * filled with a brand new uidValidity.
     * @return The uidValidity
     * @throws IOException if there are problems with the validity file
     */
    private UidValidity readUidValidity() throws IOException {
        File validityFile = new File(rootFolder, VALIDITY_FILE);
        if (!validityFile.exists()) {
            return resetUidValidity();
        }
        try (FileInputStream fis = new FileInputStream(validityFile);
             InputStreamReader isr = new InputStreamReader(fis)) {
            char[] uidValidity = new char[20];
            int len = isr.read(uidValidity);
            return UidValidity.of(Long.parseLong(String.valueOf(uidValidity, 0, len).trim()));
        }
    }

    /**
     * Save the given uidValidity to the file system
     */
    private void saveUidValidity(UidValidity uidValidity) throws IOException {
        File validityFile = new File(rootFolder, VALIDITY_FILE);
        if (!validityFile.createNewFile()) {
            throw new IOException("Could not create file " + validityFile);
        }
        try (FileOutputStream fos = new FileOutputStream(validityFile)) {
            fos.write(String.valueOf(uidValidity.asLong()).getBytes());
        }
    }

    /**
     * Sets the mailboxId for this mailbox and writes it to the file system
     */
    public void setMailboxId(MaildirId mailboxId) throws IOException {
        saveMailboxId(mailboxId);
    }

    /**
     * Read the mailboxId of the given mailbox from the file system.
     * If the respective file is not yet there, it gets created and
     * filled with a brand new uidValidity.
     * @return The uidValidity
     * @throws IOException if there are problems with the validity file
     */
    public MaildirId readMailboxId() throws IOException {
        if (!mailboxIdFile.exists()) {
            MaildirId maildirId = MaildirId.random();
            saveMailboxId(maildirId);
            return maildirId;
        }
        try (FileInputStream fileInputStream = new FileInputStream(mailboxIdFile)) {
            String serializedMaildirId = IOUtils.toString(fileInputStream, StandardCharsets.UTF_8);
            return MaildirId.fromString(serializedMaildirId);
        }
    }

    /**
     * Save the given MaildirId to the file system
     */
    private void saveMailboxId(MaildirId id) throws IOException {
        if (!mailboxIdFile.createNewFile()) {
            throw new IOException("Could not create file " + mailboxIdFile);
        }
        try (FileOutputStream fos = new FileOutputStream(mailboxIdFile)) {
            fos.write(id.serialize().getBytes(StandardCharsets.UTF_8));
        }
    }
    
    /**
     * Sets and returns a new uidValidity for this folder.
     * @return the new uidValidity
     */
    private UidValidity resetUidValidity() throws IOException {
        UidValidity uidValidity = UidValidity.random();
        setUidValidity(uidValidity);
        return uidValidity;
    }
    
    /**
     * Searches the uid list for a certain uid and returns the according {@link MaildirMessageName}
     * 
     * @param uid The uid to search for
     * @return The {@link MaildirMessageName} that belongs to the uid
     * @throws MailboxException If the uidlist file cannot be found or read
     */
    public MaildirMessageName getMessageNameByUid(final MessageUid uid) throws MailboxException {
       
        return locker.executeWithLock(path, () -> {
            File uidList = uidFile;
            try (FileReader fileReader = new FileReader(uidList);
                 BufferedReader reader = new BufferedReader(fileReader)) {

                String uidString = String.valueOf(uid.asLong());
                String line = reader.readLine(); // the header
                int lineNumber = 1; // already read the first line
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (!line.equals("")) {
                        int gap = line.indexOf(" ");
                        if (gap == -1) {
                            // there must be some issues in the file if no gap can be found
                            LOGGER.info("Corrupted entry in uid-file {} line {}", uidList, lineNumber);
                            continue;
                        }

                        if (line.substring(0, gap).equals(uidString)) {
                            return newMaildirMessageName(MaildirFolder.this, line.substring(gap + 1));
                        }
                    }
                }

                // TODO: Is this right!?
                return null;
            } catch (IOException e) {
                throw new MailboxException("Unable to read messagename for uid " + uid, e);
            }
        }, MailboxPathLocker.LockType.Write);
    }
    
    /**
     * Reads all uids between the two boundaries from the folder and returns them as
     * a sorted map together with their corresponding {@link MaildirMessageName}s.
     *
     * @param from The lower uid limit
     * @param to The upper uid limit. <code>-1</code> disables the upper limit
     * @return a {@link Map} whith all uids in the given range and associated {@link MaildirMessageName}s
     * @throws MailboxException if there is a problem with the uid list file
     */
    public SortedMap<MessageUid, MaildirMessageName> getUidMap(final MessageUid from, final MessageUid to)
    throws MailboxException {
        return locker.executeWithLock(path, () -> {
            final SortedMap<MessageUid, MaildirMessageName> uidMap = new TreeMap<>();

            File uidList = uidFile;

            if (uidList.isFile()) {
                if (isModified()) {
                    try {
                        uidMap.putAll(truncateMap(updateUidFile(), from, to));
                    } catch (MailboxException e) {
                        // weird case if someone deleted the uidlist after
                        // checking its
                        // existence and before trying to update it.
                        uidMap.putAll(truncateMap(createUidFile(), from, to));
                    }
                } else {
                    // the uidList is up to date
                    uidMap.putAll(readUidFile(from, to));
                }
            } else {
                // the uidList does not exist
                uidMap.putAll(truncateMap(createUidFile(), from, to));
            }
            return uidMap;
        }, MailboxPathLocker.LockType.Write);
    }
    
    public SortedMap<MessageUid, MaildirMessageName> getUidMap(MailboxSession session, FilenameFilter filter, MessageUid from, MessageUid to)
    throws MailboxException {
        SortedMap<MessageUid, MaildirMessageName> allUids = getUidMap(from, to);
        SortedMap<MessageUid, MaildirMessageName> filteredUids = new TreeMap<>();
        for (Entry<MessageUid, MaildirMessageName> entry : allUids.entrySet()) {
            if (filter.accept(null, entry.getValue().getFullName())) {
                filteredUids.put(entry.getKey(), entry.getValue());
            }
        }
        return filteredUids;
    }
    
    /**
     * Reads all uids from the uid list file which match the given filter
     * and returns as many of them as a sorted map as the limit specifies.
     *
     * @param filter The file names of all returned items match the filter. 
     * The dir argument to {@link FilenameFilter}.accept(dir, name) will always be null.
     * @param limit The number of items; a limit smaller then 1 disables the limit
     * @return A {@link Map} with all uids and associated {@link MaildirMessageName}s
     * @throws MailboxException if there is a problem with the uid list file
     */
    public SortedMap<MessageUid, MaildirMessageName> getUidMap(FilenameFilter filter, int limit) throws MailboxException {
        MessageUid to = null;
        SortedMap<MessageUid, MaildirMessageName> allUids = getUidMap(MessageUid.MIN_VALUE, to);
        SortedMap<MessageUid, MaildirMessageName> filteredUids = new TreeMap<>();
        int theLimit = limit;
        if (limit < 1) {
            theLimit = allUids.size();
        }
        int counter = 0;
        for (Entry<MessageUid, MaildirMessageName> entry : allUids.entrySet()) {
            if (counter >= theLimit) {
                break;
            }
            if (filter.accept(null, entry.getValue().getFullName())) {
                filteredUids.put(entry.getKey(), entry.getValue());
                counter++;
            }
        }
        return filteredUids;
    }
    
    /**
     * Creates a map of recent messages.
     *
     * @return A {@link Map} with all uids and associated {@link MaildirMessageName}s of recent messages
     * @throws MailboxException If there is a problem with the uid list file
     */
    public SortedMap<MessageUid, MaildirMessageName> getRecentMessages() throws MailboxException {
        final String[] recentFiles = getNewFolder().list();
        final LinkedList<String> lines = new LinkedList<>();
        final int theLimit = recentFiles.length;
        return locker.executeWithLock(path, () -> {
            final SortedMap<MessageUid, MaildirMessageName> recentMessages = new TreeMap<>();

            File uidList = uidFile;

            try {
                if (!uidList.isFile()) {
                    if (!uidList.createNewFile()) {
                        throw new IOException("Could not create file " + uidList);
                    }
                    String[] curFiles = curFolder.list();
                    String[] newFiles = newFolder.list();
                    messageCount = curFiles.length + newFiles.length;
                    String[] allFiles = ArrayUtils.addAll(curFiles, newFiles);
                    for (String file : allFiles) {
                        lines.add(String.valueOf(getNextUid().asLong()) + " " + file);
                    }

                    try (PrintWriter pw = new PrintWriter(uidList)) {
                        pw.println(createUidListHeader());
                        for (String line : lines) {
                            pw.println(line);
                        }
                    }
                } else {
                    try (FileReader fileReader = new FileReader(uidList);
                        BufferedReader reader = new BufferedReader(fileReader)) {

                            reader.readLine();
                            // the first line in the file contains the next uid and message count
                            String line;
                            while ((line = reader.readLine()) != null) {
                                lines.add(line);
                            }
                        }
                }
                int counter = 0;
                String line;
                while (counter < theLimit) {
                    // walk backwards as recent files are supposedly recent
                    try {
                        line = lines.removeLast();
                    } catch (NoSuchElementException e) {
                        break; // the list is empty
                    }
                    if (!line.equals("")) {
                        int gap = line.indexOf(" ");
                        if (gap == -1) {
                            // there must be some issues in the file if no gap can be found
                            // there must be some issues in the file if no gap can be found
                            LOGGER.info("Corrupted entry in uid-file {} line {}", uidList, lines.size());
                            continue;
                        }

                        MessageUid uid = MessageUid.of(Long.parseLong(line.substring(0, gap)));
                        String name = line.substring(gap + 1, line.length());
                        for (String recentFile : recentFiles) {
                            if (recentFile.equals(name)) {
                                recentMessages.put(uid, newMaildirMessageName(MaildirFolder.this, recentFile));
                                counter++;
                                break;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new MailboxException("Unable to read recent messages", e);
            }
            return recentMessages;
        }, MailboxPathLocker.LockType.Write);
    }
    
    
    /**
     * Creates and returns a uid map (uid -> {@link MaildirMessageName}) and writes it to the disk
     * @return The uid map
     */
    private Map<MessageUid, MaildirMessageName> createUidFile() throws MailboxException {
        final Map<MessageUid, MaildirMessageName> uidMap = new TreeMap<>();
        File uidList = uidFile;
        try {
            if (!uidList.createNewFile()) {
                throw new IOException("Could not create file " + uidList);
            }
            lastUid = Optional.empty();
            String[] curFiles = curFolder.list();
            String[] newFiles = newFolder.list();
            messageCount = curFiles.length + newFiles.length;
            String[] allFiles = (String[]) ArrayUtils.addAll(curFiles, newFiles);
            for (String file : allFiles) {
                uidMap.put(getNextUid(), newMaildirMessageName(MaildirFolder.this, file));
            }
            try (PrintWriter pw = new PrintWriter(uidList)) {
                pw.println(createUidListHeader());
                for (Entry<MessageUid, MaildirMessageName> entry : uidMap.entrySet()) {
                    pw.println(String.valueOf(entry.getKey().asLong()) + " " + entry.getValue().getFullName());
                }
            }
        } catch (IOException e) {
            throw new MailboxException("Unable to create uid file", e);
        }

        return uidMap;
    }
    
    private Map<MessageUid, MaildirMessageName> updateUidFile() throws MailboxException {
        final Map<MessageUid, MaildirMessageName> uidMap = new TreeMap<>();
        File uidList = uidFile;
        String[] curFiles = curFolder.list();
        String[] newFiles = newFolder.list();
        messageCount = curFiles.length + newFiles.length;
        HashMap<String, MessageUid> reverseUidMap = new HashMap<>(messageCount);
        try (FileReader fileReader = new FileReader(uidList);
            BufferedReader reader = new BufferedReader(fileReader)) {
            String line = reader.readLine();
            // the first line in the file contains the next uid and message count
            if (line != null) {
                readUidListHeader(line);
            }
            int lineNumber = 1; // already read the first line
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (!line.equals("")) {
                    int gap = line.indexOf(" ");
                    if (gap == -1) {
                        // there must be some issues in the file if no gap can be found
                        throw new MailboxException("Corrupted entry in uid-file " + uidList + " line " + lineNumber);
                    }
                    MessageUid uid = MessageUid.of(Long.parseLong(line.substring(0, gap)));
                    String name = line.substring(gap + 1, line.length());
                    reverseUidMap.put(stripMetaFromName(name), uid);
                }
            }
            String[] allFiles = (String[]) ArrayUtils.addAll(curFiles, newFiles);
            for (String file : allFiles) {
                MaildirMessageName messageName = newMaildirMessageName(MaildirFolder.this, file);
                MessageUid uid = reverseUidMap.get(messageName.getBaseName());
                if (uid == null) {
                    uid = getNextUid();
                }
                uidMap.put(uid, messageName);
            }
            try (PrintWriter pw = new PrintWriter(uidList)) {
                pw.println(createUidListHeader());
                for (Entry<MessageUid, MaildirMessageName> entry : uidMap.entrySet()) {
                    pw.println(String.valueOf(entry.getKey().asLong()) + " " + entry.getValue().getFullName());
                }
            }
        } catch (IOException e) {
            throw new MailboxException("Unable to update uid file", e);
        }
        return uidMap;
    }

    private Map<MessageUid, MaildirMessageName> readUidFile(MessageUid from, MessageUid to) throws MailboxException {
        final Map<MessageUid, MaildirMessageName> uidMap = new HashMap<>();

        File uidList = uidFile;
        try (FileReader fileReader = new FileReader(uidList);
            BufferedReader reader = new BufferedReader(fileReader)) {

            String line = reader.readLine();
            // the first line in the file contains the next uid and message
            // count
            if (line != null) {
                readUidListHeader(line);
            }
            int lineNumber = 1; // already read the first line
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (!line.equals("")) {
                    int gap = line.indexOf(" ");

                    if (gap == -1) {
                        // there must be some issues in the file if no gap can be found
                        LOGGER.info("Corrupted entry in uid-file {} line {}", uidList, lineNumber);
                        continue;
                    }
                    
                    MessageUid uid = MessageUid.of(Long.parseLong(line.substring(0, gap)));
                    if (uid.compareTo(from) >= 0) {
                        if (to != null && uid.compareTo(to) > 0) {
                            break;
                        }
                        String name = line.substring(gap + 1, line.length());
                        uidMap.put(uid, newMaildirMessageName(MaildirFolder.this, name));
                    }
                }
            }
        } catch (IOException e) {
            throw new MailboxException("Unable to read uid file", e);
        }
        messageCount = uidMap.size();

        return uidMap;
    }
    
    /**
     * Sorts the given map and returns a subset which is constricted by a lower and an upper limit.
     * @param map The source map
     * @param from The lower limit
     * @param to The upper limit; <code>-1</code> disables the upper limit.
     * @return The sorted subset
     */
    private SortedMap<MessageUid, MaildirMessageName> truncateMap(Map<MessageUid, MaildirMessageName> map, MessageUid from, MessageUid to) {
        TreeMap<MessageUid, MaildirMessageName> sortedMap;
        if (map instanceof TreeMap<?, ?>) {
            sortedMap = (TreeMap<MessageUid, MaildirMessageName>) map;
        } else {
            sortedMap = new TreeMap<>(map);
        }
        if (to != null) {
            return sortedMap.subMap(from, to.next());
        }
        return sortedMap.tailMap(from);
    }
    
    /**
     * Parses the header line in uid list files.
     * The format is: version lastUid messageCount (e.g. 1 615 273)
     * @param line The raw header line
     */
    private void readUidListHeader(String line) throws IOException {
        if (line == null) {
            throw new IOException("Header entry in uid-file is null");
        }
        int gap1 = line.indexOf(" ");
        if (gap1 == -1) {
            // there must be some issues in the file if no gap can be found
            throw new IOException("Corrupted header entry in uid-file");
            
        }
        int version = Integer.parseInt(line.substring(0, gap1));
        if (version != 1) {
            throw new IOException("Cannot read uidlists with versions other than 1.");
        }
        int gap2 = line.indexOf(" ", gap1 + 1);
        lastUid = Optional.of(MessageUid.of(Long.parseLong(line.substring(gap1 + 1, gap2))));
        messageCount = Integer.parseInt(line.substring(gap2 + 1, line.length()));
    }
    
    /**
     * Creates a line to put as a header in the uid list file.
     * @return the line which ought to be the header
     */
    private String createUidListHeader() {
        Long last = lastUid.map(MessageUid::asLong).orElse(0L);
        return "1 " + String.valueOf(last) + " " + String.valueOf(messageCount);
    }
    
    /**
     * Takes the name of a message file and returns only the base name.
     * @param fileName The name of the message file
     * @return the file name without meta data, the unmodified name if it doesn't have meta data
     */
    public static String stripMetaFromName(String fileName) {
        int end = fileName.indexOf(",S="); // the size
        if (end == -1) {
            end = fileName.indexOf(":2,"); // the flags
        }
        if (end == -1) {
            return fileName; // there is no meta data to strip
        }
        return fileName.substring(0, end);
    }

    /**
     * Appends a message to the uidlist and returns its uid.
     *
     * @param name The name of the message's file
     * @return The uid of the message
     */
    public MessageUid appendMessage(final String name) throws MailboxException {
        return locker.executeWithLock(path, () -> {
            File uidList = uidFile;
            MessageUid uid = null;
            try {
                if (uidList.isFile()) {
                    try (FileReader fileReader = new FileReader(uidList);
                        BufferedReader reader = new BufferedReader(fileReader)) {
                        String line = reader.readLine();
                        // the first line in the file contains the next uid and message count
                        if (line != null) {
                            readUidListHeader(line);
                        }
                        ArrayList<String> lines = new ArrayList<>();
                        while ((line = reader.readLine()) != null) {
                            lines.add(line);
                        }
                        uid = getNextUid();
                        lines.add(String.valueOf(uid.asLong()) + " " + name);
                        messageCount++;
                        try (PrintWriter pw = new PrintWriter(uidList)) {
                            pw.println(createUidListHeader());
                            for (String entry : lines) {
                                pw.println(entry);
                            }
                        }
                    }
                } else {
                    // create the file
                    if (!uidList.createNewFile()) {
                        throw new IOException("Could not create file " + uidList);
                    }
                    String[] curFiles = curFolder.list();
                    String[] newFiles = newFolder.list();
                    messageCount = curFiles.length + newFiles.length;
                    ArrayList<String> lines = new ArrayList<>();
                    String[] allFiles = (String[]) ArrayUtils.addAll(curFiles, newFiles);
                    for (String file : allFiles) {
                        MessageUid theUid = getNextUid();
                        lines.add(String.valueOf(theUid.asLong()) + " " + file);
                        // the listed names already include the message to append
                        if (file.equals(name)) {
                            uid = theUid;
                        }
                    }
                    try (PrintWriter pw = new PrintWriter(uidList)) {
                        pw.println(createUidListHeader());
                        for (String line : lines) {
                            pw.println(line);
                        }
                    }
                }
            } catch (IOException e) {
                throw new MailboxException("Unable to append msg", e);
            }
            if (uid == null) {
                throw new MailboxException("Unable to append msg");
            } else {
               return uid;
            }
        }, MailboxPathLocker.LockType.Write);

    }

    /**
     * Updates an entry in the uid list.
     */
    public void update(final MessageUid uid, final String messageName) throws MailboxException {
        locker.executeWithLock(path, (LockAwareExecution<Void>) () -> {
            File uidList = uidFile;
            try (FileReader fileReader = new FileReader(uidList);
                BufferedReader reader = new BufferedReader(fileReader)) {

                String line = reader.readLine();
                readUidListHeader(line);
                ArrayList<String> lines = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    if (uid.equals(MessageUid.of(Long.parseLong(line.substring(0, line.indexOf(" ")))))) {
                        line = String.valueOf(uid.asLong()) + " " + messageName;
                    }
                    lines.add(line);
                }
                try (PrintWriter writer = new PrintWriter(uidList)) {
                    writer.println(createUidListHeader());
                    for (String entry : lines) {
                        writer.println(entry);
                    }
                }
            } catch (IOException e) {
                throw new MailboxException("Unable to update msg with uid " + uid, e);
            }
            return null;
        }, MailboxPathLocker.LockType.Write);

    }
    
    /**
     * Retrieves the file belonging to the given uid, deletes it and updates
     * the uid list.
     * @param uid The uid of the message to delete
     * @return The {@link MaildirMessageName} of the deleted message
     * @throws MailboxException If the file cannot be deleted of there is a problem with the uid list
     */
    public MaildirMessageName delete(final MessageUid uid) throws MailboxException {
        return locker.executeWithLock(path, () -> {
            File uidList = uidFile;
            MaildirMessageName deletedMessage = null;
            try (FileReader fileReader = new FileReader(uidList);
                BufferedReader reader = new BufferedReader(fileReader)) {

                readUidListHeader(reader.readLine());

                // It may be possible that message count is 0 so we should better not try to calculate the size of the ArrayList
                ArrayList<String> lines = new ArrayList<>();
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    int gap = line.indexOf(" ");
                    if (gap == -1) {
                        // there must be some issues in the file if no gap can be found
                        LOGGER.info("Corrupted entry in uid-file {} line {}", uidList, lineNumber);
                        continue;
                    }

                    if (uid.equals(MessageUid.of(Long.parseLong(line.substring(0, line.indexOf(" ")))))) {
                        deletedMessage = newMaildirMessageName(MaildirFolder.this, line.substring(gap + 1, line.length()));
                        messageCount--;
                    } else {
                        lines.add(line);
                    }
                }
                if (deletedMessage != null) {
                    FileUtils.forceDelete(deletedMessage.getFile());
                    try (PrintWriter writer = new PrintWriter(uidList)) {
                        writer.println(createUidListHeader());
                        for (String entry : lines) {
                            writer.println(entry);
                        }
                    }
                }
                return deletedMessage;

            } catch (IOException e) {
                throw new MailboxException("Unable to delete msg with uid " + uid, e);
            }
        }, MailboxPathLocker.LockType.Write);
        

    }
    
    /** 
     * The absolute path of this folder.
     */
    @Override
    public String toString() {
        return getRootFile().getAbsolutePath();
    }
    
    public MailboxACL getACL() throws MailboxException {
        if (acl == null) {
            acl = readACL();
        }
        return acl;
    }

    /**
     * Read the ACL of the given mailbox from the file system.
     *
     * @throws MailboxException if there are problems with the aclFile file
     */
    private MailboxACL readACL() throws MailboxException {
        // FIXME Do we need this locking?
        return locker.executeWithLock(path, (LockAwareExecution<MailboxACL>) () -> {
            File f = aclFile;
            Properties props = new Properties();
            if (f.exists()) {
                try (FileInputStream in = new FileInputStream(f)) {
                    props.load(in);
                } catch (IOException e) {
                    throw new MailboxException("Unable to read last ACL from " + f.getAbsolutePath(), e);
                }
            }

            return new MailboxACL(props);

        }, MailboxPathLocker.LockType.Write);
        
    }
    
    public void setACL(MailboxACL acl) throws MailboxException {
        MailboxACL old = this.acl;
        if (!Objects.equals(old, acl)) {
            /* change only if different */
            saveACL(acl);
            this.acl = acl;
        }
        
    }

    private void saveACL(final MailboxACL acl) throws MailboxException {
        // FIXME Do we need this locking?
        locker.executeWithLock(path, new LockAwareExecution<Void>() {
            
            @Override
            public Void execute() throws MailboxException {
                File f = aclFile;

                Properties props = new Properties();
                Map<EntryKey, Rfc4314Rights> entries = acl.getEntries();
                if (entries != null) {
                    for (Entry<EntryKey, Rfc4314Rights> en : entries.entrySet()) {
                        props.put(en.getKey().serialize(), en.getValue().serialize());
                    }
                }
                if (f.exists()) {
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        props.store(out, "written by " + getClass().getName());
                    } catch (IOException e) {
                        throw new MailboxException("Unable to read last ACL from " + f.getAbsolutePath(), e);
                    }
                }
                
                return null;

            }
        }, MailboxPathLocker.LockType.Write);
    }

    
}
