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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.DuplicateUserException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.sieverepository.api.exception.UserNotFoundException;

import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

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

    private FileSystem _fileSystem = null;
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
            scanner = new Scanner(file, encoding).useDelimiter("\\A");
            script = scanner.next();
        } finally {
            if (null != scanner) {
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

    /**
     * Creates a new instance of SieveFileRepository.
     */
    public SieveFileRepository() {
        super();
    }

    /**
     * Creates a new instance of SieveFileRepository.
     *
     * @param fileSystem
     */
    public SieveFileRepository(FileSystem fileSystem) {
        this();
        setFileSystem(fileSystem);
    }

    @Inject
    public void setFileSystem(FileSystem fileSystem) {
        _fileSystem = fileSystem;
    }

    @Override
    public void deleteScript(final String user, final String name) throws UserNotFoundException,
            ScriptNotFoundException, IsActiveException, StorageException {
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
    public String getScript(final String user, final String name) throws UserNotFoundException,
            ScriptNotFoundException, StorageException {
        String script;
        try {
            script = toString(getScriptFile(user, name), UTF_8);
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
    public void haveSpace(final String user, final String name, final long size) throws UserNotFoundException,
            QuotaExceededException, StorageException {
        long usedSpace = 0;
        for (File file : getUserDirectory(user).listFiles()) {
            if (!(file.getName().equals(name) || SYSTEM_FILES.contains(file.getName()))) {
                usedSpace = usedSpace + file.length();
            }
        }

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
            } catch (FileNotFoundException ex) {
                // no op
            } catch (NoSuchElementException ex) {
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

    @Override
    public List<ScriptSummary> listScripts(final String user) throws UserNotFoundException, StorageException {
        File[] files = getUserDirectory(user).listFiles();
        List<ScriptSummary> summaries = new ArrayList<ScriptSummary>(files.length);
        File activeFile = null;
        try {
            activeFile = getActiveFile(user);
        } catch (ScriptNotFoundException ex) {
            // no op
        }
        for (final File file : files) {
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
    public void putScript(final String user, final String name, final String content)
            throws UserNotFoundException, StorageException, QuotaExceededException {
        synchronized (lock) {
            File file = new File(getUserDirectory(user), name);
            haveSpace(user, name, content.length());
            toFile(file, content);
        }
    }

    @Override
    public void renameScript(final String user, final String oldName, final String newName)
            throws UserNotFoundException, ScriptNotFoundException,
            DuplicateException, StorageException {
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
    public String getActive(final String user) throws UserNotFoundException,
            ScriptNotFoundException, StorageException {
        String script;
        try {
            script = toString(getActiveFile(user), UTF_8);
        } catch (FileNotFoundException ex) {
            throw new ScriptNotFoundException(ex);
        }
        return script;
    }

    @Override
    public void setActive(final String user, final String name) throws UserNotFoundException,
            ScriptNotFoundException, StorageException {
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
            return _fileSystem.getFile(SIEVE_ROOT);
        } catch (FileNotFoundException ex1) {
            throw new StorageException(ex1);
        }
    }

    protected File getUserDirectory(String user) throws UserNotFoundException, StorageException {
        File file = getUserDirectoryFile(user);
        if (!file.exists()) {
            throw new UserNotFoundException("User: " + user);
        }
        return file;
    }

    protected File getUserDirectoryFile(String user) throws StorageException {
        return new File(getSieveRootDirectory(), user + '/');
    }

    protected File getActiveFile(String user) throws UserNotFoundException,
            ScriptNotFoundException, StorageException {
        File dir = getUserDirectory(user);
        String content;
        try {
            content = toString(new File(dir, FILE_NAME_ACTIVE), UTF_8);
        } catch (FileNotFoundException ex) {
            throw new ScriptNotFoundException("There is no active script.");
        }
        return new File(dir, content);
    }

    protected boolean isActiveFile(String user, File file) throws UserNotFoundException, StorageException {
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

    protected File getScriptFile(String user, String name) throws UserNotFoundException,
            ScriptNotFoundException, StorageException {
        File file = new File(getUserDirectory(user), name);
        if (!file.exists()) {
            throw new ScriptNotFoundException("User: " + user + "Script: " + name);
        }
        return file;
    }


    @Override
    public boolean hasUser(final String user) throws StorageException {
        boolean userExists = true;
        try {
            getUserDirectory(user);
        } catch (UserNotFoundException ex) {
            userExists = false;
        }
        return userExists;
    }

    @Override
    public void addUser(final String user) throws DuplicateUserException, StorageException {
        synchronized (lock) {
            boolean userExists = true;
            try {
                getUserDirectory(user);
            } catch (UserNotFoundException ex) {
                userExists = false;
            }
            if (userExists) {
                throw new DuplicateUserException("User: " + user);
            }
            File dir = getUserDirectoryFile(user);
            try {
                FileUtils.forceMkdir(dir);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    @Override
    public void removeUser(final String user) throws UserNotFoundException, StorageException {
        synchronized (lock) {
            File dir = getUserDirectory(user);
            try {
                FileUtils.forceDelete(dir);
            } catch (IOException ex) {
                throw new StorageException(ex);
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
            } catch (FileNotFoundException ex) {
                // no op
            } catch (NoSuchElementException ex) {
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
            throw new QuotaNotFoundException("No default quota");
        }
        try {
            FileUtils.forceDelete(file);
        } catch (IOException ex) {
            throw new StorageException(ex);
        }
    }

    @Override
    public synchronized void setQuota(final long quota) throws StorageException {
        File file = getQuotaFile();
        String content = Long.toString(quota);
        toFile(file, content);
    }

    protected File getQuotaFile(String user) throws UserNotFoundException, StorageException {
        return new File(getUserDirectory(user), FILE_NAME_QUOTA);
    }

    @Override
    public boolean hasQuota(final String user) throws UserNotFoundException, StorageException {
        return getQuotaFile(user).exists();
    }

    @Override
    public long getQuota(final String user) throws UserNotFoundException, QuotaNotFoundException, StorageException {
        Long quota = null;
        File file = getQuotaFile(user);
        if (file.exists()) {
            Scanner scanner = null;
            try {
                scanner = new Scanner(file, UTF_8);
                quota = scanner.nextLong();
            } catch (FileNotFoundException ex) {
                // no op
            } catch (NoSuchElementException ex) {
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
    public void removeQuota(final String user) throws UserNotFoundException,
            QuotaNotFoundException, StorageException {
        synchronized (lock) {
            File file = getQuotaFile(user);
            if (!file.exists()) {
                throw new QuotaNotFoundException("No quota for user: " + user);
            }
            try {
                FileUtils.forceDelete(file);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    @Override
    public void setQuota(final String user, final long quota) throws UserNotFoundException,
            StorageException {
        synchronized (lock) {
            File file = getQuotaFile(user);
            String content = Long.toString(quota);
            toFile(file, content);
        }
    }

}
