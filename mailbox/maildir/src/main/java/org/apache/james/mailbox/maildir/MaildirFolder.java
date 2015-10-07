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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxPathLocker.LockAwareExecution;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLEntryKey;
import org.apache.james.mailbox.model.MailboxACL.MailboxACLRights;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SimpleMailboxACL;

public class MaildirFolder {

    public static final String VALIDITY_FILE = "james-uidvalidity";
    public static final String UIDLIST_FILE = "james-uidlist";
    public static final String ACL_FILE = "james-acl";
    public static final String CUR = "cur";
    public static final String NEW = "new";
    public static final String TMP = "tmp";
    
    private File rootFolder;
    private File curFolder;
    private File newFolder;
    private File tmpFolder;
    private File uidFile;
    private File aclFile;
    
    private long lastUid = -1;
    private int messageCount = 0;
    private long uidValidity = -1;
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
        this.locker = locker;
        this.path = path;
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
     *
     * @return
     */
    public boolean isMessageNameStrictParse() {
        return messageNameStrictParse;
    }

    /**
     * Specifies whether the names of message files in this folder are parsed in
     * a strict manner ({@code true}), which means a size field and flags are
     * expected.
     *
     * @param messageNameStrictParse
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
     * @return nextUid
     */
    private long getNextUid() {
        return ++lastUid;
    }
    
    /**
     * Returns the last uid used in this mailbox
     * @param session
     * @return lastUid
     * @throws MailboxException
     */
    public long getLastUid(MailboxSession session) throws MailboxException {
        if (lastUid == -1) {
            readLastUid(session);
        }
        return lastUid;
    }
    
    public long getHighestModSeq() throws IOException {
        long newModified = getNewFolder().lastModified();
        long curModified = getCurFolder().lastModified();
        if (newModified  == 0L && curModified == 0L) {
            throw new IOException("Unable to read highest modSeq");
        }
        return Math.max(newModified, curModified);
    }

    /**
     * Read the lastUid of the given mailbox from the file system.
     * 
     * @param session
     * @throws MailboxException if there are problems with the uidList file
     */
    private void readLastUid(MailboxSession session) throws MailboxException {
        locker.executeWithLock(session, path, new LockAwareExecution<Void>() {
            
            @Override
            public Void execute() throws MailboxException {
                File uidList = uidFile;
                FileReader fileReader = null;
                BufferedReader reader = null;
                try {
                    if (!uidList.exists())
                        createUidFile();
                    fileReader = new FileReader(uidList);
                    reader = new BufferedReader(fileReader);
                    String line = reader.readLine();
                    if (line != null)
                        readUidListHeader(line);
                    return null;
                } catch (IOException e) {
                    throw new MailboxException("Unable to read last uid", e);
                } finally {
                    IOUtils.closeQuietly(reader);
                    IOUtils.closeQuietly(fileReader);
                }                
            }
        }, true);
        
        
    }

    /**
     * Returns the uidValidity of this mailbox
     * @return The uidValidity
     * @throws IOException
     */
    public long getUidValidity() throws IOException {
        if (uidValidity == -1)
            uidValidity = readUidValidity();
        return uidValidity;
    }
    
    /**
     * Sets the uidValidity for this mailbox and writes it to the file system
     * @param uidValidity
     * @throws IOException
     */
    public void setUidValidity(long uidValidity) throws IOException {
        saveUidValidity(uidValidity);
        this.uidValidity = uidValidity;
    }

    /**
     * Read the uidValidity of the given mailbox from the file system.
     * If the respective file is not yet there, it gets created and
     * filled with a brand new uidValidity.
     * @return The uidValidity
     * @throws IOException if there are problems with the validity file
     */
    private long readUidValidity() throws IOException {
        File validityFile = new File(rootFolder, VALIDITY_FILE);
        if (!validityFile.exists()) {
            return resetUidValidity();
        }
        FileInputStream fis = null;
        InputStreamReader isr = null;
        try {
            fis = new FileInputStream(validityFile);
            isr = new InputStreamReader(fis);
            char[] uidValidity = new char[20];
            int len = isr.read(uidValidity);
            return Long.parseLong(String.valueOf(uidValidity, 0, len).trim());
        } finally {
            IOUtils.closeQuietly(isr);
            IOUtils.closeQuietly(fis);
        }
    }

    /**
     * Save the given uidValidity to the file system
     * @param uidValidity
     * @throws IOException
     */
    private void saveUidValidity(long uidValidity) throws IOException {
        File validityFile = new File(rootFolder, VALIDITY_FILE);
        if (!validityFile.createNewFile())
            throw new IOException("Could not create file " + validityFile);
        FileOutputStream fos = new FileOutputStream(validityFile);
        try {
            fos.write(String.valueOf(uidValidity).getBytes());
        } finally {
            IOUtils.closeQuietly(fos);
        }
    }
    
    /**
     * Sets and returns a new uidValidity for this folder.
     * @return the new uidValidity
     * @throws IOException
     */
    private long resetUidValidity() throws IOException {
        // using the timestamp as uidValidity
        long timestamp = System.currentTimeMillis();
        setUidValidity(timestamp);
        return timestamp;
    }
    
    /**
     * Searches the uid list for a certain uid and returns the according {@link MaildirMessageName}
     * 
     * @param session
     * @param uid The uid to search for
     * @return The {@link MaildirMessageName} that belongs to the uid
     * @throws IOException If the uidlist file cannot be found or read
     */
    public MaildirMessageName getMessageNameByUid(final MailboxSession session, final Long uid) throws MailboxException {
       
        return locker.executeWithLock(session, path, new LockAwareExecution<MaildirMessageName>() {
            
            @Override
            public MaildirMessageName execute() throws MailboxException {
                FileReader fileReader = null;
                BufferedReader reader = null;
                File uidList = uidFile;
                try {
                    fileReader = new FileReader(uidList);
                    reader = new BufferedReader(fileReader);
                    String uidString = String.valueOf(uid);
                    String line = reader.readLine(); // the header
                    int lineNumber = 1;
                    while ((line = reader.readLine()) != null) {
                        if (!line.equals("")) {
                            int gap = line.indexOf(" ");
                            if (gap == -1) {
                                // there must be some issues in the file if no gap can be found
                            	session.getLog().info("Corrupted entry in uid-file " + uidList + " line " + lineNumber++);
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
                } finally {
                    IOUtils.closeQuietly(reader);
                    IOUtils.closeQuietly(fileReader);
                }                
            }
        }, true);
    }
    
    /**
     * Reads all uids between the two boundaries from the folder and returns them as
     * a sorted map together with their corresponding {@link MaildirMessageName}s.
     *
     * @param session
     * @param from The lower uid limit
     * @param to The upper uid limit. <code>-1</code> disables the upper limit
     * @return a {@link Map} whith all uids in the given range and associated {@link MaildirMessageName}s
     * @throws MailboxException if there is a problem with the uid list file
     */
    public SortedMap<Long, MaildirMessageName> getUidMap(final MailboxSession session, final long from, final long to)
    throws MailboxException {
        return locker.executeWithLock(session, path, new LockAwareExecution<SortedMap<Long, MaildirMessageName>>() {
            
            @Override
            public SortedMap<Long, MaildirMessageName> execute() throws MailboxException {
                final SortedMap<Long, MaildirMessageName> uidMap = new TreeMap<Long, MaildirMessageName>();

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
                        uidMap.putAll(readUidFile(session, from, to));
                    }
                } else {
                    // the uidList does not exist
                    uidMap.putAll(truncateMap(createUidFile(), from, to));
                }
                return uidMap;
            }
        }, true);
    }
    
    public SortedMap<Long, MaildirMessageName> getUidMap(MailboxSession session, FilenameFilter filter, long from, long to)
    throws MailboxException {
        SortedMap<Long, MaildirMessageName> allUids = getUidMap(session, from, to);
        SortedMap<Long, MaildirMessageName> filteredUids = new TreeMap<Long, MaildirMessageName>();
        for (Entry<Long, MaildirMessageName> entry : allUids.entrySet()) {
            if (filter.accept(null, entry.getValue().getFullName()))
                filteredUids.put(entry.getKey(), entry.getValue());
        }
        return filteredUids;
    }
    
    /**
     * Reads all uids from the uid list file which match the given filter
     * and returns as many of them as a sorted map as the limit specifies.
     * 
     * 
     * @param session
     * @param filter The file names of all returned items match the filter. 
     * The dir argument to {@link FilenameFilter}.accept(dir, name) will always be null.
     * @param limit The number of items; a limit smaller then 1 disables the limit
     * @return A {@link Map} with all uids and associated {@link MaildirMessageName}s
     * @throws MailboxException if there is a problem with the uid list file
     */
    public SortedMap<Long, MaildirMessageName> getUidMap(MailboxSession session, FilenameFilter filter, int limit) throws MailboxException {
        SortedMap<Long, MaildirMessageName> allUids = getUidMap(session, 0, -1);
        SortedMap<Long, MaildirMessageName> filteredUids = new TreeMap<Long, MaildirMessageName>();
        int theLimit = limit;
        if (limit < 1)
            theLimit = allUids.size();
        int counter = 0;
        for (Entry<Long, MaildirMessageName> entry : allUids.entrySet()) {
            if (counter >= theLimit)
                break;
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
     * @param session
     * @return A {@link Map} with all uids and associated {@link MaildirMessageName}s of recent messages
     * @throws MailboxException If there is a problem with the uid list file
     */
    public SortedMap<Long, MaildirMessageName> getRecentMessages(final MailboxSession session) throws MailboxException {
        final String[] recentFiles = getNewFolder().list();
        final LinkedList<String> lines = new LinkedList<String>();
        final int theLimit = recentFiles.length;
        return locker.executeWithLock(session, path, new LockAwareExecution<SortedMap<Long, MaildirMessageName>>() {
            
            @Override
            public SortedMap<Long, MaildirMessageName> execute() throws MailboxException {
                final SortedMap<Long, MaildirMessageName> recentMessages = new TreeMap<Long, MaildirMessageName>();

                File uidList = uidFile;

                try {
                    if (!uidList.isFile()) {
                        if (!uidList.createNewFile())
                            throw new IOException("Could not create file " + uidList);
                        String[] curFiles = curFolder.list();
                        String[] newFiles = newFolder.list();
                        messageCount = curFiles.length + newFiles.length;
                        String[] allFiles = (String[]) ArrayUtils.addAll(curFiles, newFiles);
                        for (String file : allFiles)
                            lines.add(String.valueOf(getNextUid()) + " " + file);
                        PrintWriter pw = new PrintWriter(uidList);
                        try {
                            pw.println(createUidListHeader());
                            for (String line : lines)
                            pw.println(line);
                        } finally {
                            IOUtils.closeQuietly(pw);
                        }
                    }
                    else {
                        FileReader fileReader = null;
                        BufferedReader reader = null;
                    try {
                            fileReader = new FileReader(uidList);
                            reader = new BufferedReader(fileReader);
                            String line = reader.readLine();
                            // the first line in the file contains the next uid and message count
                            while ((line = reader.readLine()) != null)
                                lines.add(line);
                        } finally {
                            IOUtils.closeQuietly(reader);
                            IOUtils.closeQuietly(fileReader);
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
                            	session.getLog().info("Corrupted entry in uid-file " + uidList + " line " + lines.size());
                                continue;
                            }
                            
                            Long uid = Long.valueOf(line.substring(0, gap));
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
            }
        }, true);
    }
    
    
    /**
     * Creates and returns a uid map (uid -> {@link MaildirMessageName}) and writes it to the disk
     * @return The uid map
     * @throws MailboxException
     */
    private Map<Long, MaildirMessageName> createUidFile() throws MailboxException {
        final Map<Long, MaildirMessageName> uidMap = new TreeMap<Long, MaildirMessageName>();
        File uidList = uidFile;
        PrintWriter pw = null;
        try {
            if (!uidList.createNewFile())
                throw new IOException("Could not create file " + uidList);
            lastUid = 0;
            String[] curFiles = curFolder.list();
            String[] newFiles = newFolder.list();
            messageCount = curFiles.length + newFiles.length;
            String[] allFiles = (String[]) ArrayUtils.addAll(curFiles, newFiles);
            for (String file : allFiles)
                uidMap.put(getNextUid(), newMaildirMessageName(MaildirFolder.this, file));
            //uidMap = new TreeMap<Long, MaildirMessageName>(uidMap);
            pw = new PrintWriter(uidList);
            pw.println(createUidListHeader());
            for (Entry<Long, MaildirMessageName> entry : uidMap.entrySet())
                pw.println(String.valueOf(entry.getKey()) + " " + entry.getValue().getFullName());
        } catch (IOException e) {
            throw new MailboxException("Unable to create uid file", e);
        } finally {
            IOUtils.closeQuietly(pw);
        }     

        return uidMap;
    }
    
    private Map<Long, MaildirMessageName> updateUidFile() throws MailboxException {
        final Map<Long, MaildirMessageName> uidMap = new TreeMap<Long, MaildirMessageName>();
        File uidList = uidFile;
        String[] curFiles = curFolder.list();
        String[] newFiles = newFolder.list();
        messageCount = curFiles.length + newFiles.length;
        HashMap<String, Long> reverseUidMap = new HashMap<String, Long>(messageCount);
        FileReader fileReader = null;
        BufferedReader reader = null;
        PrintWriter pw = null;
        try {
            fileReader = new FileReader(uidList);
            reader = new BufferedReader(fileReader);
            String line = reader.readLine();
            // the first line in the file contains the next uid and message count
            if (line != null)
                readUidListHeader(line);
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                if (!line.equals("")) {
                    int gap = line.indexOf(" ");
                    if (gap == -1) {
                        // there must be some issues in the file if no gap can be found
                        throw new MailboxException("Corrupted entry in uid-file " + uidList + " line " + lineNumber++);
                    }
                    Long uid = Long.valueOf(line.substring(0, gap));
                    String name = line.substring(gap + 1, line.length());
                    reverseUidMap.put(stripMetaFromName(name), uid);
                }
            }
            String[] allFiles = (String[]) ArrayUtils.addAll(curFiles, newFiles);
            for (String file : allFiles) {
                MaildirMessageName messageName = newMaildirMessageName(MaildirFolder.this, file);
                Long uid = reverseUidMap.get(messageName.getBaseName());
                if (uid == null)
                    uid = getNextUid();
                uidMap.put(uid, messageName);
            }
            pw = new PrintWriter(uidList);
            pw.println(createUidListHeader());
            for (Entry<Long, MaildirMessageName> entry : uidMap.entrySet())
                pw.println(String.valueOf(entry.getKey()) + " " + entry.getValue().getFullName());
        } catch (IOException e) {
            throw new MailboxException("Unable to update uid file", e);
        } finally {
            IOUtils.closeQuietly(pw);
            IOUtils.closeQuietly(fileReader);
            IOUtils.closeQuietly(reader);
        }               
        return uidMap;
    }

    private Map<Long, MaildirMessageName> readUidFile(MailboxSession session, final long from, final long to) throws MailboxException {
        final Map<Long, MaildirMessageName> uidMap = new HashMap<Long, MaildirMessageName>();

        File uidList = uidFile;
        FileReader fileReader = null;
        BufferedReader reader = null;
        try {
            fileReader = new FileReader(uidList);
            reader = new BufferedReader(fileReader);
            String line = reader.readLine();
            // the first line in the file contains the next uid and message
            // count
            if (line != null)
                readUidListHeader(line);
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                if (!line.equals("")) {
                    int gap = line.indexOf(" ");

                    if (gap == -1) {
                        // there must be some issues in the file if no gap can be found
                    	session.getLog().info("Corrupted entry in uid-file " + uidList + " line " + lineNumber++);
                        continue;
                    }

                    Long uid = Long.valueOf(line.substring(0, gap));
                    if (uid >= from) {
                        if (to != -1 && uid > to)
                            break;
                        String name = line.substring(gap + 1, line.length());
                        uidMap.put(uid, newMaildirMessageName(MaildirFolder.this, name));
                    }
                }
            }
        } catch (IOException e) {
            throw new MailboxException("Unable to read uid file", e);
        } finally {
            IOUtils.closeQuietly(reader);
            IOUtils.closeQuietly(fileReader);
        }
        messageCount = uidMap.size();

        return uidMap;
    }
    
    /**
     * Sorts the given map and returns a subset which is constricted by a lower and an upper limit.
     * @param source The source map
     * @param from The lower limit
     * @param to The upper limit; <code>-1</code> disables the upper limit.
     * @return The sorted subset
     */
    private SortedMap<Long, MaildirMessageName> truncateMap(Map<Long, MaildirMessageName> source, long from, long to) {
        TreeMap<Long, MaildirMessageName> sortedMap;
        if (source instanceof TreeMap<?, ?>) sortedMap = (TreeMap<Long, MaildirMessageName>) source;
        else sortedMap = new TreeMap<Long, MaildirMessageName>(source);
        if (to != -1)
            return sortedMap.subMap(from, to + 1);
        return sortedMap.tailMap(from);
    }
    
    /**
     * Parses the header line in uid list files.
     * The format is: version lastUid messageCount (e.g. 1 615 273)
     * @param line The raw header line
     * @throws IOException
     */
    private void readUidListHeader(String line) throws IOException {
        if (line == null)
            throw new IOException("Header entry in uid-file is null");
        int gap1 = line.indexOf(" ");
        if (gap1 == -1) {
            // there must be some issues in the file if no gap can be found
            throw new IOException("Corrupted header entry in uid-file");
            
        }
        int version = Integer.valueOf(line.substring(0, gap1));
        if (version != 1)
            throw new IOException("Cannot read uidlists with versions other than 1.");
        int gap2 = line.indexOf(" ", gap1 + 1);
        lastUid = Long.valueOf(line.substring(gap1 + 1, gap2));
        messageCount = Integer.valueOf(line.substring(gap2 + 1, line.length()));
    }
    
    /**
     * Creates a line to put as a header in the uid list file.
     * @return the line which ought to be the header
     */
    private String createUidListHeader() {
        return "1 " + String.valueOf(lastUid) + " " + String.valueOf(messageCount);
    }
    
    /**
     * Takes the name of a message file and returns only the base name.
     * @param fileName The name of the message file
     * @return the file name without meta data, the unmodified name if it doesn't have meta data
     */
    public static String stripMetaFromName(String fileName) {
        int end = fileName.indexOf(",S="); // the size
        if (end == -1)
            end = fileName.indexOf(":2,"); // the flags
        if (end == -1)
            return fileName; // there is no meta data to strip
        return fileName.substring(0, end);
    }

    /**
     * Appends a message to the uidlist and returns its uid.
     * @param session
     * @param name The name of the message's file
     * @return The uid of the message
     * @throws IOException
     */
    public long appendMessage(final MailboxSession session, final String name) throws MailboxException {
        return locker.executeWithLock(session, path, new LockAwareExecution<Long>() {
            
            @Override
            public Long execute() throws MailboxException {
                File uidList = uidFile;
                long uid = -1;
                FileReader fileReader = null;
                BufferedReader reader = null;
                PrintWriter pw = null;
                try {
                    if (uidList.isFile()) {
                        fileReader = new FileReader(uidList);
                        reader = new BufferedReader(fileReader);
                        String line = reader.readLine();
                        // the first line in the file contains the next uid and message count
                        if (line != null)
                            readUidListHeader(line);
                        ArrayList<String> lines = new ArrayList<String>();
                        while ((line = reader.readLine()) != null)
                            lines.add(line);
                        uid = getNextUid();
                        lines.add(String.valueOf(uid) + " " + name);
                        messageCount++;
                        pw = new PrintWriter(uidList);
                        pw.println(createUidListHeader());
                        for (String entry : lines)
                            pw.println(entry);
                    }
                    else {
                        // create the file
                        if (!uidList.createNewFile())
                            throw new IOException("Could not create file " + uidList);
                        String[] curFiles = curFolder.list();
                        String[] newFiles = newFolder.list();
                        messageCount = curFiles.length + newFiles.length;
                        ArrayList<String> lines = new ArrayList<String>();
                        String[] allFiles = (String[]) ArrayUtils.addAll(curFiles, newFiles);
                        for (String file : allFiles) {
                            long theUid = getNextUid();
                            lines.add(String.valueOf(theUid) + " " + file);
                            // the listed names already include the message to append
                            if (file.equals(name))
                                uid = theUid;
                        }
                        pw = new PrintWriter(uidList);
                        pw.println(createUidListHeader());
                        for (String line : lines)
                            pw.println(line);
                    }
                } catch (IOException e) {
                    throw new MailboxException("Unable to append msg", e);
                } finally {
                    IOUtils.closeQuietly(pw);
                    IOUtils.closeQuietly(reader);
                    IOUtils.closeQuietly(fileReader);
                }
                if (uid == -1) {
                    throw new MailboxException("Unable to append msg");
                } else {
                   return uid;
                }
            }
        }, true);

    }

    /**
     * Updates an entry in the uid list.
     * @param session
     * @param uid
     * @param messageName
     * @throws MailboxException
     */
    public void update(final MailboxSession session, final long uid, final String messageName) throws MailboxException {
        locker.executeWithLock(session, path, new LockAwareExecution<Void>() {
            
            @Override
            public Void execute() throws MailboxException {
                File uidList = uidFile;
                FileReader fileReader = null;
                BufferedReader reader = null;
                PrintWriter writer = null;
                try {
                    fileReader = new FileReader(uidList);
                    reader = new BufferedReader(fileReader);
                    String line = reader.readLine();
                    readUidListHeader(line);
                    ArrayList<String> lines = new ArrayList<String>();
                    while ((line = reader.readLine()) != null) {
                        if (uid == Long.valueOf(line.substring(0, line.indexOf(" "))))
                            line = String.valueOf(uid) + " " + messageName;
                        lines.add(line);
                    }
                    writer = new PrintWriter(uidList);
                    writer.println(createUidListHeader());
                    for (String entry : lines)
                        writer.println(entry);
                } catch (IOException e) {
                    throw new MailboxException("Unable to update msg with uid " + uid, e);
                } finally {
                    IOUtils.closeQuietly(writer);
                    IOUtils.closeQuietly(reader);
                    IOUtils.closeQuietly(fileReader);
                }    
                return null;
            }
        }, true);

    }
    
    /**
     * Retrieves the file belonging to the given uid, deletes it and updates
     * the uid list.
     * @param uid The uid of the message to delete
     * @return The {@link MaildirMessageName} of the deleted message
     * @throws MailboxException If the file cannot be deleted of there is a problem with the uid list
     */
    public MaildirMessageName delete(final MailboxSession session, final long uid) throws MailboxException {        
        return locker.executeWithLock(session, path, new LockAwareExecution<MaildirMessageName>() {
            
            @Override
            public MaildirMessageName execute() throws MailboxException {
                File uidList = uidFile;
                FileReader fileReader = null;
                BufferedReader reader = null;
                PrintWriter writer = null;
                MaildirMessageName deletedMessage = null;
                try {
                    fileReader = new FileReader(uidList);
                    reader = new BufferedReader(fileReader);
                    readUidListHeader(reader.readLine());
                    
                    // It may be possible that message count is 0 so we should better not try to calculate the size of the ArrayList
                    ArrayList<String> lines = new ArrayList<String>();
                    String line;
                    int lineNumber = 1;
                    while ((line = reader.readLine()) != null) {
                        int gap = line.indexOf(" ");
                        if (gap == -1) {
                            // there must be some issues in the file if no gap can be found
                        	session.getLog().info("Corrupted entry in uid-file " + uidList + " line " + lineNumber++);
                            continue;
                        }
                        
                        if (uid == Long.valueOf(line.substring(0, line.indexOf(" ")))) {
                            deletedMessage = newMaildirMessageName(MaildirFolder.this, line.substring(gap + 1, line.length()));
                            messageCount--;
                        }
                        else {
                            lines.add(line);
                        }
                    }
                    if (deletedMessage != null) {
                        FileUtils.forceDelete(deletedMessage.getFile());
                        writer = new PrintWriter(uidList);
                        writer.println(createUidListHeader());
                        for (String entry : lines)
                            writer.println(entry);
                    }
                    return deletedMessage;

                } catch (IOException e) {
                    throw new MailboxException("Unable to delete msg with uid " + uid, e);
                } finally {
                    IOUtils.closeQuietly(writer);
                    IOUtils.closeQuietly(reader);
                    IOUtils.closeQuietly(fileReader);
                }   
            }
        }, true);
        

    }
    
    /** 
     * The absolute path of this folder.
     */
    @Override
    public String toString() {
        return getRootFile().getAbsolutePath();
    }
    
    public MailboxACL getACL(final MailboxSession session) throws MailboxException {
        if (acl == null) {
            acl = readACL(session);
        }
        return acl;
    }

    /**
     * Read the ACL of the given mailbox from the file system.
     * 
     * @param session
     * @throws MailboxException if there are problems with the aclFile file
     */
    private MailboxACL readACL(MailboxSession session) throws MailboxException {
        // FIXME Do we need this locking?
        return locker.executeWithLock(session, path, new LockAwareExecution<MailboxACL>() {
            
            @Override
            public MailboxACL execute() throws MailboxException {
                File f = aclFile;
                InputStream in = null;
                Properties props = new Properties();
                if (f.exists()) {
                    try {
                        in = new FileInputStream(f);
                        props.load(in);
                    } catch (FileNotFoundException e) {
                        throw new MailboxException("Unable to read last ACL from "+ f.getAbsolutePath(), e);
                    } catch (IOException e) {
                        throw new MailboxException("Unable to read last ACL from "+ f.getAbsolutePath(), e);
                    }
                    finally {
                        IOUtils.closeQuietly(in);
                    }
                }
                
                return new SimpleMailboxACL(props);

            }
        }, true);
        
    }
    
    public void setACL(final MailboxSession session, final MailboxACL acl) throws MailboxException {
        MailboxACL old = this.acl;
        if (old != acl && (old == null || !old.equals(acl))) {
            /* change only if different */
            saveACL(acl, session);
            this.acl = acl;
        }
        
    }

    private void saveACL(final MailboxACL acl, final MailboxSession session) throws MailboxException {
        // FIXME Do we need this locking?
        locker.executeWithLock(session, path, new LockAwareExecution<Void>() {
            
            @Override
            public Void execute() throws MailboxException {
                File f = aclFile;
                OutputStream out = null;
                Properties props = new Properties();
                Map<MailboxACLEntryKey, MailboxACLRights> entries = acl.getEntries();
                if (entries != null) {
                    for (Entry<MailboxACLEntryKey, MailboxACLRights> en : entries.entrySet()) {
                        props.put(en.getKey().serialize(), en.getValue().serialize());
                    }
                }
                if (f.exists()) {
                    try {
                        out = new FileOutputStream(f);
                        props.store(out, "written by "+ getClass().getName());
                    } catch (FileNotFoundException e) {
                        throw new MailboxException("Unable to read last ACL from "+ f.getAbsolutePath(), e);
                    } catch (IOException e) {
                        throw new MailboxException("Unable to read last ACL from "+ f.getAbsolutePath(), e);
                    }
                    finally {
                        IOUtils.closeQuietly(out);
                    }
                }
                
                return null;

            }
        }, true);
    }

    
}
