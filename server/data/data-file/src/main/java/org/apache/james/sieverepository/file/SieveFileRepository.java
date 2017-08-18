/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.sieverepository.file;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.joda.time.DateTime;

/**
 * <code>SieveFileRepository</code> manages sieve scripts stored on the file system.
 * <p>The sieve root directory is a sub-directory of the application base directory named "sieve".
 * Scripts are stored in sub-directories of the sieve root directory, each with the name of the
 * associated user.
 */
public class SieveFileRepository implements SieveRepository {

    private static final String SIEVE_ROOT = FileSystem.FILE_PROTOCOL + "sieve/";
    private static final String UTF_8 = "UTF-8";
    private static final String FILE_NAME_QUOTA = ".quota";
    private static final String FILE_NAME_ACTIVE = ".active";
    private static final List<String> SYSTEM_FILES = Arrays.asList(FILE_NAME_QUOTA, FILE_NAME_ACTIVE);
    private static final int MAX_BUFF_SIZE = 32768;
    public static final String SIEVE_EXTENSION = ".sieve";

    private final FileSystem fileSystem;
    private final Object lock = new Object();

    /**
     * Read a file with the specified encoding into a String
     *
     * @param file
     * @param encoding
     * @return
     * @throws FileNotFoundException
     */
    static protected String toString(File file, String encoding) throws FileNotFoundException {
        String script = null;
        Scanner scanner = null;
        try {
            scanner = new Scanner(file, encoding);
            scanner.useDelimiter("\\A");
            script = scanner.next();
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return script;
    }

    static protected void toFile(File file, String content) throws StorageException {
        // Create a temporary file
        int bufferSize = content.length() > MAX_BUFF_SIZE ? MAX_BUFF_SIZE : content.length();
        File tmpFile = null;
        Writer out = null;
        try {
            tmpFile = File.createTempFile(file.getName(), ".tmp", file.getParentFile());
            out = new OutputStreamWriter(new BufferedOutputStream(
                    new FileOutputStream(tmpFile), bufferSize), UTF_8);
            out.write(content);
        } catch (IOException ex) {
            FileUtils.deleteQuietly(tmpFile);
            throw new StorageException(ex);
        } finally {
            IOUtils.closeQuietly(out);
        }

        // Does the file exist?
        // If so, make a backup
        File backupFile = new File(file.getParentFile(), file.getName() + ".bak");
        if (file.exists()) {
            try {
                FileUtils.copyFile(file, backupFile);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }

        // Copy the temporary file to its final name
        try {
            FileUtils.copyFile(tmpFile, file);
        } catch (IOException ex) {
            throw new StorageException(ex);
        }
        // Tidy up
        if (tmpFile.exists()) {
            FileUtils.deleteQuietly(tmpFile);
        }
        if (backupFile.exists()) {
            FileUtils.deleteQuietly(backupFile);
        }
    }

    @Inject
    public SieveFileRepository(FileSystem fileSystem) throws IOException {
        this.fileSystem = fileSystem;
        File root = fileSystem.getFile(SIEVE_ROOT);
        FileUtils.forceMkdir(root);
    }

    @Override
    public void deleteScript(String user, String name) throws ScriptNotFoundException, IsActiveException, StorageException {
        synchronized (lock) {
            File file = getScriptFile(user, name);
            if (isActiveFile(user, file)) {
                throw new IsActiveException("User: " + user + "Script: " + name);
            }
            try {
                FileUtils.forceDelete(file);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    @Override
    public InputStream getScript(String user, String name) throws ScriptNotFoundException, StorageException {
        InputStream script;
        try {
            script = new FileInputStream(getScriptFile(user, name));
        } catch (FileNotFoundException ex) {
            throw new ScriptNotFoundException(ex);
        }
        return script;
    }

    /**
     * The default quota, if any, is stored in file '.quota' in the sieve root directory. Quotas for
     * specific users are stored in file '.quota' in the user's directory.
     * <p/>
     * <p>The '.quota' file contains a single positive integer value representing the quota in octets.
     *
     * @see SieveRepository#haveSpace(java.lang.String, java.lang.String, long)
     */
    @Override
    public void haveSpace(String user, String name, long size) throws QuotaExceededException, StorageException {
        long usedSpace = Arrays.stream(getUserDirectory(user).listFiles())
            .filter(file -> !(file.getName().equals(name) || SYSTEM_FILES.contains(file.getName())))
            .mapToLong(File::length)
            .sum();

        long quota = Long.MAX_VALUE;
        File file = getQuotaFile(user);
        if (!file.exists()) {
            file = getQuotaFile();
        }
        if (file.exists()) {
            Scanner scanner = null;
            try {
                scanner = new Scanner(file, UTF_8);
                quota = scanner.nextLong();
            } catch (FileNotFoundException | NoSuchElementException ex) {
                // no op
            } finally {
                if (null != scanner) {
                    scanner.close();
                }
            }
        }
        if ((usedSpace + size) > quota) {
            throw new QuotaExceededException(" Quota: " + quota + " Used: " + usedSpace
                    + " Requested: " + size);
        }
    }

    public List<ScriptSummary> listScripts(String user) throws StorageException {
        File[] files = getUserDirectory(user).listFiles();
        List<ScriptSummary> summaries = new ArrayList<>(files.length);
        File activeFile = null;
        try {
            activeFile = getActiveFile(user);
        } catch (ScriptNotFoundException ex) {
            // no op
        }
        for (File file : files) {
            if (!SYSTEM_FILES.contains(file.getName())) {
                summaries.add(new ScriptSummary(file.getName(), isActive(file, activeFile)));
            }
        }
        return summaries;
    }

    private boolean isActive(File file, File activeFile) {
        return null != activeFile
            && activeFile.equals(file);
    }

    @Override
    public void putScript(String user, String name, String content) throws StorageException, QuotaExceededException {
        synchronized (lock) {
            File file = new File(getUserDirectory(user), name);
            haveSpace(user, name, content.length());
            toFile(file, content);
        }
    }

    @Override
    public void renameScript(String user, String oldName, String newName)
            throws ScriptNotFoundException, DuplicateException, StorageException {
        synchronized (lock) {
            File oldFile = getScriptFile(user, oldName);
            File newFile = new File(getUserDirectory(user), newName);
            if (newFile.exists()) {
                throw new DuplicateException("User: " + user + "Script: " + newName);
            }
            try {
                FileUtils.copyFile(oldFile, newFile);
                if (isActiveFile(user, oldFile)) {
                    setActiveFile(newFile, user, true);
                }
                FileUtils.forceDelete(oldFile);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    @Override
    public InputStream getActive(String user) throws ScriptNotFoundException, StorageException {
        InputStream script;
        try {
            script = new FileInputStream(getActiveFile(user));
        } catch (FileNotFoundException ex) {
            throw new ScriptNotFoundException(ex);
        }
        return script;
    }

    @Override
    public DateTime getActivationDateForActiveScript(String user) throws StorageException, ScriptNotFoundException {
        return new DateTime(getActiveFile(user).lastModified());
    }

    public void setActive( String user, String name) throws ScriptNotFoundException, StorageException {
        synchronized (lock) {
            // Turn off currently active script, if any
            File oldActive = null;
            try {
                oldActive = getActiveFile(user);
                setActiveFile(oldActive, user, false);
            } catch (ScriptNotFoundException ex) {
                // This is permissible
            }
            // Turn on the new active script if not an empty name
            if ((null != name) && (!name.trim().isEmpty())) {
                try {
                    setActiveFile(getScriptFile(user, name), user, true);
                } catch (ScriptNotFoundException ex) {
                    if (null != oldActive) {
                        setActiveFile(oldActive, user, true);
                    }
                    throw ex;
                }
            }
        }
    }

    protected File getSieveRootDirectory() throws StorageException {
        try {
            return fileSystem.getFile(SIEVE_ROOT);
        } catch (FileNotFoundException ex1) {
            throw new StorageException(ex1);
        }
    }

    protected File getUserDirectory(String user) throws StorageException {
        File file = getUserDirectoryFile(user);
        if (!file.exists()) {
            ensureUser(user);
        }
        return file;
    }

    protected File getUserDirectoryFile(String user) throws StorageException {
        return new File(getSieveRootDirectory(), user + '/');
    }

    protected File getActiveFile(String user) throws ScriptNotFoundException, StorageException {
        File dir = getUserDirectory(user);
        String content;
        try {
            content = toString(new File(dir, FILE_NAME_ACTIVE), UTF_8);
        } catch (FileNotFoundException ex) {
            throw new ScriptNotFoundException("There is no active script for user " + user);
        }
        return new File(dir, content);
    }

    protected boolean isActiveFile(String user, File file) throws StorageException {
        try {
            return 0 == getActiveFile(user).compareTo(file);
        } catch (ScriptNotFoundException ex) {
            return false;
        }
    }

    protected void setActiveFile(File scriptToBeActivated, String userName, boolean isActive) throws StorageException {
        File personalScriptDirectory = scriptToBeActivated.getParentFile();
        File sieveBaseDirectory = personalScriptDirectory.getParentFile();
        File activeScriptPersistenceFile = new File(personalScriptDirectory, FILE_NAME_ACTIVE);
        File activeScriptCopy = new File(sieveBaseDirectory, userName + SIEVE_EXTENSION);
        if (isActive) {
            toFile(activeScriptPersistenceFile, scriptToBeActivated.getName());
            try {
                FileUtils.copyFile(scriptToBeActivated, activeScriptCopy);
            } catch (IOException exception) {
                throw new StorageException("Can not copy active script to make it accessible for sieve utils", exception);
            }
        } else {
            try {
                FileUtils.forceDelete(activeScriptPersistenceFile);
                FileUtils.forceDelete(activeScriptCopy);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    protected File getScriptFile(String user, String name) throws ScriptNotFoundException, StorageException {
        File file = new File(getUserDirectory(user), name);
        if (!file.exists()) {
            throw new ScriptNotFoundException("User: " + user + "Script: " + name);
        }
        return file;
    }

    public void ensureUser(String user) throws StorageException {
        synchronized (lock) {
            try {
                FileUtils.forceMkdir(getUserDirectoryFile(user));
            } catch (IOException e) {
                throw new StorageException("Error while creating directory for " + user, e);
            }
        }
    }

    protected File getQuotaFile() throws StorageException {
        return new File(getSieveRootDirectory(), FILE_NAME_QUOTA);
    }

    @Override
    public boolean hasQuota() throws StorageException {
        return getQuotaFile().exists();
    }

    @Override
    public long getQuota() throws QuotaNotFoundException, StorageException {
        Long quota = null;
        File file = getQuotaFile();
        if (file.exists()) {
            Scanner scanner = null;
            try {
                scanner = new Scanner(file, UTF_8);
                quota = scanner.nextLong();
            } catch (FileNotFoundException | NoSuchElementException ex) {
                // no op
            } finally {
                if (null != scanner) {
                    scanner.close();
                }
            }
        }
        if (null == quota) {
            throw new QuotaNotFoundException("No default quota");
        }
        return quota;
    }

    @Override
    public synchronized void removeQuota() throws QuotaNotFoundException, StorageException {
        File file = getQuotaFile();
        if (!file.exists()) {
            return;
        }
        try {
            FileUtils.forceDelete(file);
        } catch (IOException ex) {
            throw new StorageException(ex);
        }
    }

    @Override
    public synchronized void setQuota(long quota) throws StorageException {
        File file = getQuotaFile();
        String content = Long.toString(quota);
        toFile(file, content);
    }

    protected File getQuotaFile(String user) throws StorageException {
        return new File(getUserDirectory(user), FILE_NAME_QUOTA);
    }

    @Override
    public boolean hasQuota(String user) throws StorageException {
        return getQuotaFile(user).exists();
    }

    @Override
    public long getQuota(String user) throws QuotaNotFoundException, StorageException {
        Long quota = null;
        File file = getQuotaFile(user);
        if (file.exists()) {
            Scanner scanner = null;
            try {
                scanner = new Scanner(file, UTF_8);
                quota = scanner.nextLong();
            } catch (FileNotFoundException | NoSuchElementException ex) {
                // no op
            } finally {
                if (null != scanner) {
                    scanner.close();
                }
            }
        }
        if (null == quota) {
            throw new QuotaNotFoundException("No quota for user: " + user);
        }
        return quota;
    }

    @Override
    public void removeQuota(String user) throws QuotaNotFoundException, StorageException {
        synchronized (lock) {
            File file = getQuotaFile(user);
            if (!file.exists()) {
                return;
            }
            try {
                FileUtils.forceDelete(file);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    @Override
    public void setQuota(String user, long quota) throws StorageException {
        synchronized (lock) {
            File file = getQuotaFile(user);
            String content = Long.toString(quota);
            toFile(file, content);
        }
    }

}
