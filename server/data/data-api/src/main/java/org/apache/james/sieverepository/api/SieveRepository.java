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

package org.apache.james.sieverepository.api;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;

import org.apache.james.core.User;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;


/**
 * <code>SieveRepository</code>
 */
public interface SieveRepository extends SieveQuotaRepository {

    ScriptName NO_SCRIPT_NAME = new ScriptName("");

    void haveSpace(User user, ScriptName name, long size) throws QuotaExceededException, StorageException;
    
    /**
     * PutScript.
     *
     * <p><strong>Note:</strong> It is the responsibility of the caller to validate the script to be put.
     *
     * @param user
     * @param name
     * @param content
     * @throws StorageException
     * @throws QuotaExceededException
     */
    void putScript(User user, ScriptName name, ScriptContent content) throws StorageException, QuotaExceededException;
    
    List<ScriptSummary> listScripts(User user) throws StorageException;

    ZonedDateTime getActivationDateForActiveScript(User user) throws StorageException, ScriptNotFoundException;

    InputStream getActive(User user) throws ScriptNotFoundException, StorageException;
    
    void setActive(User user, ScriptName name) throws ScriptNotFoundException, StorageException;
    
    InputStream getScript(User user, ScriptName name) throws ScriptNotFoundException, StorageException;
    
    void deleteScript(User user, ScriptName name) throws ScriptNotFoundException, IsActiveException, StorageException;
    
    void renameScript(User user, ScriptName oldName, ScriptName newName) throws ScriptNotFoundException, DuplicateException, StorageException;

}
