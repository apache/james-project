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
import java.util.List;

import javax.inject.Inject;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaSize;
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
import org.joda.time.DateTime;

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
    public void haveSpace(User user, ScriptName name, long size) throws QuotaExceededException, StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public void putScript(User user, ScriptName name, ScriptContent content) throws StorageException, QuotaExceededException {
        throw new StorageException("This implementation is deprecated and does not support script put operation. You must directly position your scripts in the .sieve folder. Please consider using a SieveFileRepository.");
    }

    @Override
    public List<ScriptSummary> listScripts(User user) throws StorageException {
        throw new StorageException("This implementation is deprecated and does not support listScripts operation. Please consider using a SieveFileRepository.");
    }

    @Override
    public InputStream getActive(User user) throws ScriptNotFoundException, StorageException {
        try {
            return new FileInputStream(retrieveUserFile(user));
        } catch (FileNotFoundException e) {
            throw new ScriptNotFoundException();
        }
    }

    @Override
    public DateTime getActivationDateForActiveScript(User user) throws StorageException, ScriptNotFoundException {
        return new DateTime(retrieveUserFile(user).lastModified());
    }

    public File retrieveUserFile(User user) throws ScriptNotFoundException {
        // RFC 5228 permits extensions: .siv .sieve
        String sieveFilePrefix = FileSystem.FILE_PROTOCOL + "sieve/" + user.asString() + ".";
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
    public void setActive(User user, ScriptName name) throws ScriptNotFoundException, StorageException {
        throw new StorageException("This implementation is deprecated and does not support script SetActive operation. Your uploaded script is by default the active script. Please consider using a SieveFileRepository.");
    }

    @Override
    public InputStream getScript(User user, ScriptName name) throws ScriptNotFoundException, StorageException {
        return getActive(user);
    }

    @Override
    public void deleteScript(User user, ScriptName name) throws ScriptNotFoundException, IsActiveException, StorageException {
        throw new StorageException("This implementation is deprecated and does not support delete script operation. Please consider using a SieveFileRepository.");
    }

    @Override
    public void renameScript(User user, ScriptName oldName, ScriptName newName) throws ScriptNotFoundException, DuplicateException, StorageException {
        throw new StorageException("This implementation is deprecated and does not support rename script operation. Please consider using a SieveFileRepository.");
    }

    @Override
    public boolean hasDefaultQuota() throws StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public QuotaSize getDefaultQuota() throws QuotaNotFoundException, StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public void setDefaultQuota(QuotaSize quota) throws StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public void removeQuota() throws QuotaNotFoundException, StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public boolean hasQuota(User user) throws StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public QuotaSize getQuota(User user) throws QuotaNotFoundException, StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public void setQuota(User user, QuotaSize quota) throws StorageException {
        throw apologizeForQuotas();
    }

    @Override
    public void removeQuota(User user) throws QuotaNotFoundException, StorageException {
        throw apologizeForQuotas();
    }

    private StorageException apologizeForQuotas() throws StorageException {
        throw new StorageException("Implementation deprecated. Quota not managed by this implementation. Please consider using a SieveFileRepository.");
    }
}
