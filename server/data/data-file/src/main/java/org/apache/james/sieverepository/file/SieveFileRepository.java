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
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    private final File root;
    private final Object lock = new Object();

    /**
     * Read a file with the specified encoding into a String
     *
     * @param file
     * @param encoding
     * @return
     * @throws FileNotFoundException
     */
    protected static String toString(File file, String encoding) throws FileNotFoundException {
        String script = null;
        try (Scanner scanner = new Scanner(file, encoding)) {
            scanner.useDelimiter("\\A");
            script = scanner.next();
        }
        return script;
    }

    protected static void toFile(File file, String content) throws StorageException {
        // Create a temporary file
        int bufferSize = Math.min(content.length(), MAX_BUFF_SIZE);
        File tmpFile = null;

        try {
            tmpFile = Files.createTempFile(file.getParentFile().toPath(), "", ".tmp").toFile();
            try (Writer out = new OutputStreamWriter(new BufferedOutputStream(
                    new FileOutputStream(tmpFile), bufferSize), UTF_8)) {
                out.write(content);
            }
        } catch (IOException ex) {
            FileUtils.deleteQuietly(tmpFile);
            throw new StorageException(ex);
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
        this.root = fileSystem.getFile(SIEVE_ROOT);
        FileUtils.forceMkdir(root);
    }

    @Override
    public void deleteScript(Username username, ScriptName name) throws ScriptNotFoundException, IsActiveException, StorageException {
        synchronized (lock) {
            File file = getScriptFile(username, name);
            if (isActiveFile(username, file)) {
                throw new IsActiveException("User: " + username.asString() + "Script: " + name);
            }
            try {
                FileUtils.forceDelete(file);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    @Override
    public InputStream getScript(Username username, ScriptName name) throws ScriptNotFoundException, StorageException {
        InputStream script;
        try {
            script = new FileInputStream(getScriptFile(username, name));
        } catch (FileNotFoundException ex) {
            throw new ScriptNotFoundException(ex);
        }
        return script;
    }

    /**
     * The default quota, if any, is stored in file '.quota' in the sieve root directory. Quotas for
     * specific users are stored in file '.quota' in the user's directory.
     *
     * The '.quota' file contains a single positive integer value representing the quota in octets.
     */
    @Override
    public void haveSpace(Username username, ScriptName name, long size) throws QuotaExceededException, StorageException {
        long usedSpace = Arrays.stream(getUserDirectory(username).listFiles())
            .filter(file -> !(file.getName().equals(name.getValue()) || SYSTEM_FILES.contains(file.getName())))
            .mapToLong(File::length)
            .sum();

        long quota = Long.MAX_VALUE;
        File file = getQuotaFile(username);
        if (!file.exists()) {
            file = getQuotaFile();
        }
        if (file.exists()) {
            try (Scanner scanner = new Scanner(file, UTF_8)) {
                quota = scanner.nextLong();
            } catch (FileNotFoundException | NoSuchElementException ex) {
                // no op
            }
        }
        if ((usedSpace + size) > quota) {
            throw new QuotaExceededException(" Quota: " + quota + " Used: " + usedSpace
                    + " Requested: " + size);
        }
    }

    @Override
    public List<ScriptSummary> listScripts(Username username) throws StorageException {
        File activeFile = null;
        try {
            activeFile = getActiveFile(username);
        } catch (ScriptNotFoundException ex) {
            // no op
        }

        Predicate<File> isActive = isActiveValidator(activeFile);
        return Stream.of(Optional.ofNullable(getUserDirectory(username).listFiles()).orElse(new File[]{}))
            .filter(file -> !SYSTEM_FILES.contains(file.getName()))
            .map(file -> new ScriptSummary(new ScriptName(file.getName()), isActive.test(file), file.length()))
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Flux<ScriptSummary> listScriptsReactive(Username username) {
        return Mono.fromCallable(() -> listScripts(username)).flatMapMany(Flux::fromIterable);
    }

    private Predicate<File> isActiveValidator(File activeFile) {
        if (activeFile != null) {
            return activeFile::equals;
        }
        return file -> false;
    }

    @Override
    public void putScript(Username username, ScriptName name, ScriptContent content) throws StorageException, QuotaExceededException {
        synchronized (lock) {
            File file = new File(getUserDirectory(username), name.getValue());
            enforceRoot(file);
            haveSpace(username, name, content.length());
            toFile(file, content.getValue());
        }
    }

    @Override
    public void renameScript(Username username, ScriptName oldName, ScriptName newName)
            throws ScriptNotFoundException, DuplicateException, StorageException {
        synchronized (lock) {
            File oldFile = getScriptFile(username, oldName);
            File newFile = new File(getUserDirectory(username), newName.getValue());
            enforceRoot(newFile);
            if (newFile.exists()) {
                throw new DuplicateException("User: " + username.asString() + "Script: " + newName);
            }
            try {
                FileUtils.copyFile(oldFile, newFile);
                if (isActiveFile(username, oldFile)) {
                    setActiveFile(newFile, username, true);
                }
                FileUtils.forceDelete(oldFile);
            } catch (IOException ex) {
                throw new StorageException(ex);
            }
        }
    }

    @Override
    public InputStream getActive(Username username) throws ScriptNotFoundException, StorageException {
        InputStream script;
        try {
            script = new FileInputStream(getActiveFile(username));
        } catch (FileNotFoundException ex) {
            throw new ScriptNotFoundException(ex);
        }
        return script;
    }

    @Override
    public ZonedDateTime getActivationDateForActiveScript(Username username) throws StorageException, ScriptNotFoundException {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(getActiveFile(username).lastModified()), ZoneOffset.UTC);
    }

    @Override
    public void setActive(Username username, ScriptName scriptName) throws ScriptNotFoundException, StorageException {
        synchronized (lock) {
            // Turn off currently active script, if any
            File oldActive = null;
            try {
                oldActive = getActiveFile(username);
                setActiveFile(oldActive, username, false);
            } catch (ScriptNotFoundException ex) {
                // This is permissible
            }
            // Turn on the new active script if not an empty name
            String name = scriptName.getValue();
            if ((null != name) && (!name.trim().isEmpty())) {
                try {
                    setActiveFile(getScriptFile(username, new ScriptName(name)), username, true);
                } catch (ScriptNotFoundException ex) {
                    if (null != oldActive) {
                        setActiveFile(oldActive, username, true);
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

    protected File getUserDirectory(Username username) throws StorageException {
        File file = getUserDirectoryFile(username);
        if (!file.exists()) {
            ensureUser(username);
        }
        return file;
    }

    private void enforceRoot(File file) throws StorageException {
        if (!file.toPath().normalize().startsWith(root.toPath().normalize())) {
            throw new StorageException(new IllegalStateException("Path traversal attempted"));
        }
    }

    protected File getUserDirectoryFile(Username username) throws StorageException {
        final File userFile = new File(getSieveRootDirectory(), username.asString() + '/');
        enforceRoot(userFile);
        return userFile;
    }

    protected File getActiveFile(Username username) throws ScriptNotFoundException, StorageException {
        File dir = getUserDirectory(username);
        String content;
        try {
            content = toString(new File(dir, FILE_NAME_ACTIVE), UTF_8);
        } catch (FileNotFoundException ex) {
            throw new ScriptNotFoundException("There is no active script for user " + username.asString());
        }
        File scriptFile = new File(dir, content);
        enforceRoot(scriptFile);
        return scriptFile;
    }

    protected boolean isActiveFile(Username username, File file) throws StorageException {
        try {
            return 0 == getActiveFile(username).compareTo(file);
        } catch (ScriptNotFoundException ex) {
            return false;
        }
    }

    protected void setActiveFile(File scriptToBeActivated, Username userName, boolean isActive) throws StorageException {
        File personalScriptDirectory = scriptToBeActivated.getParentFile();
        File sieveBaseDirectory = personalScriptDirectory.getParentFile();
        File activeScriptPersistenceFile = new File(personalScriptDirectory, FILE_NAME_ACTIVE);
        File activeScriptCopy = new File(sieveBaseDirectory, userName.asString() + SIEVE_EXTENSION);
        enforceRoot(activeScriptPersistenceFile);
        enforceRoot(activeScriptCopy);
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

    protected File getScriptFile(Username username, ScriptName name) throws ScriptNotFoundException, StorageException {
        if (name.getValue().contains("/")) {
            throw new StorageException(new IllegalArgumentException("Script name should not contain '/' as it can allow path traversal"));
        }
        File file = new File(getUserDirectory(username), name.getValue());
        enforceRoot(file);
        if (!file.exists()) {
            throw new ScriptNotFoundException("User: " + username + "Script: " + name);
        }
        return file;
    }

    public void ensureUser(Username username) throws StorageException {
        synchronized (lock) {
            try {
                FileUtils.forceMkdir(getUserDirectoryFile(username));
            } catch (IOException e) {
                throw new StorageException("Error while creating directory for " + username.asString(), e);
            }
        }
    }

    protected File getQuotaFile() throws StorageException {
        return new File(getSieveRootDirectory(), FILE_NAME_QUOTA);
    }

    @Override
    public boolean hasDefaultQuota() throws StorageException {
        return getQuotaFile().exists();
    }

    @Override
    public QuotaSizeLimit getDefaultQuota() throws QuotaNotFoundException, StorageException {
        Long quota = null;
        File file = getQuotaFile();
        if (file.exists()) {
            try (Scanner scanner = new Scanner(file, UTF_8)) {
                quota = scanner.nextLong();
            } catch (FileNotFoundException | NoSuchElementException ex) {
                // no op
            }
        }
        if (null == quota) {
            throw new QuotaNotFoundException("No default quota");
        }
        return QuotaSizeLimit.size(quota);
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
    public synchronized void setDefaultQuota(QuotaSizeLimit quota) throws StorageException {
        File file = getQuotaFile();
        String content = Long.toString(quota.asLong());
        toFile(file, content);
    }

    protected File getQuotaFile(Username username) throws StorageException {
        return new File(getUserDirectory(username), FILE_NAME_QUOTA);
    }

    @Override
    public boolean hasQuota(Username username) throws StorageException {
        return getQuotaFile(username).exists();
    }

    @Override
    public QuotaSizeLimit getQuota(Username username) throws QuotaNotFoundException, StorageException {
        Long quota = null;
        File file = getQuotaFile(username);
        if (file.exists()) {
            try (Scanner scanner = new Scanner(file, UTF_8)) {
                quota = scanner.nextLong();
            } catch (FileNotFoundException | NoSuchElementException ex) {
                // no op
            }
        }
        if (null == quota) {
            throw new QuotaNotFoundException("No quota for user: " + username.asString());
        }
        return QuotaSizeLimit.size(quota);
    }

    @Override
    public void removeQuota(Username username) throws QuotaNotFoundException, StorageException {
        synchronized (lock) {
            File file = getQuotaFile(username);
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
    public void setQuota(Username username, QuotaSizeLimit quota) throws StorageException {
        synchronized (lock) {
            File file = getQuotaFile(username);
            String content = Long.toString(quota.asLong());
            toFile(file, content);
        }
    }

    @Override
    public Mono<Void> resetSpaceUsedReactive(Username username, long spaceUsed) {
        return Mono.error(new UnsupportedOperationException());
    }
}
