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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.sieverepository.api.exception.UserNotFoundException;

/**
 * <code>SieveFileRepository</code> manages sieve scripts stored on the file system.
 * <p>The sieve root directory is a sub-directory of the application base directory named "sieve".
 * Scripts are stored in sub-directories of the sieve root directory, each with the name of the
 * associated user.
 */
@Deprecated
public class SieveDefaultRepository implements SieveRepository {
    private FileSystem fileSystem;

    @Inject
    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public void haveSpace(String user, String name, long size) throws UserNotFoundException, QuotaExceededException, StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public void putScript(String user, String name, String content) throws UserNotFoundException, StorageException, QuotaExceededException {
        throw new StorageException("This implementation is deprecated and does not support script put operation. You must directly position your scripts in the .sieve folder. Please consider using a SieveFileRepository.");
    }

    @Override
    public List<ScriptSummary> listScripts(String user) throws UserNotFoundException, StorageException {
        throw new StorageException("This implementation is deprecated and does not support listScripts operation. Please consider using a SieveFileRepository.");
    }

    @Override
    public InputStream getActive(String user) throws UserNotFoundException, ScriptNotFoundException, StorageException {
        try {
            return new FileInputStream(retrieveUserFile(user));
        } catch (FileNotFoundException e) {
            throw new ScriptNotFoundException();
        }
    }

    @Override
    public Date getStorageDateForActiveScript(String user) throws StorageException, UserNotFoundException, ScriptNotFoundException {
        return new Date(retrieveUserFile(user).lastModified());
    }

    public File retrieveUserFile(String user) throws ScriptNotFoundException {
        // RFC 5228 permits extensions: .siv .sieve
        String sieveFilePrefix = FileSystem.FILE_PROTOCOL + "sieve/" + user + ".";
        try {
            return fileSystem.getFile(sieveFilePrefix + "sieve");
        } catch (FileNotFoundException e) {
            try {
                return fileSystem.getFile(sieveFilePrefix + "siv");
            } catch (FileNotFoundException fileNotFoundException) {
                throw new ScriptNotFoundException(fileNotFoundException);
            }
        }
    }

    @Override
    public void setActive(String user, String name) throws UserNotFoundException, ScriptNotFoundException, StorageException {
        throw new StorageException("This implementation is deprecated and does not support script SetActive operation. Your uploaded script is by default the active script. Please consider using a SieveFileRepository.");
    }

    @Override
    public InputStream getScript(String user, String name) throws UserNotFoundException, ScriptNotFoundException, StorageException {
        return getActive(user);
    }

    @Override
    public void deleteScript(String user, String name) throws UserNotFoundException, ScriptNotFoundException, IsActiveException, StorageException {
        throw new StorageException("This implementation is deprecated and does not support delete script operation. Please consider using a SieveFileRepository.");
    }

    @Override
    public void renameScript(String user, String oldName, String newName) throws UserNotFoundException, ScriptNotFoundException, DuplicateException, StorageException {
        throw new StorageException("This implementation is deprecated and does not support rename script operation. Please consider using a SieveFileRepository.");
    }

    @Override
    public boolean hasQuota() throws StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public long getQuota() throws QuotaNotFoundException, StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public void setQuota(long quota) throws StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public void removeQuota() throws QuotaNotFoundException, StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public boolean hasQuota(String user) throws UserNotFoundException, StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public long getQuota(String user) throws UserNotFoundException, QuotaNotFoundException, StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public void setQuota(String user, long quota) throws UserNotFoundException, StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public void removeQuota(String user) throws UserNotFoundException, QuotaNotFoundException, StorageException {
        throw apologizeForQuotas();
    }

    private StorageException apologizeForQuotas() throws StorageException {
        throw new StorageException("Implementation deprecated. Quota not managed by this implementation. Please consider using a SieveFileRepository.");
    }
}
