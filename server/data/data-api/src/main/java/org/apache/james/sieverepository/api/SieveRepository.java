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

import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.DuplicateUserException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.sieverepository.api.exception.UserNotFoundException;

import java.util.List;



/**
 * <code>SieveRepository</code>
 */
public interface SieveRepository {
    
    void haveSpace(String user, String name, long size) throws UserNotFoundException, QuotaExceededException, StorageException;
    
    /**
     * PutScript.
     *
     * <p><strong>Note:</strong> It is the responsibility of the caller to validate the script to be put.
     *
     * @param user
     * @param name
     * @param content
     * @throws UserNotFoundException
     * @throws StorageException
     * @throws QuotaExceededException
     */
    void putScript(String user, String name, String content) throws UserNotFoundException, StorageException, QuotaExceededException;
    
    List<ScriptSummary> listScripts(String user) throws UserNotFoundException, StorageException;
    
    String getActive(String user) throws UserNotFoundException, ScriptNotFoundException, StorageException;
    
    void setActive(String user, String name) throws UserNotFoundException, ScriptNotFoundException, StorageException;
    
    String getScript(String user, String name) throws UserNotFoundException, ScriptNotFoundException, StorageException;
    
    void deleteScript(String user, String name) throws UserNotFoundException, ScriptNotFoundException, IsActiveException, StorageException;
    
    void renameScript(String user, String oldName, String newName) throws UserNotFoundException, ScriptNotFoundException, DuplicateException, StorageException;
    
    boolean hasUser(String user) throws StorageException;
    
    void addUser(String user) throws DuplicateUserException, StorageException;
    
    void removeUser(String user) throws UserNotFoundException, StorageException;

    boolean hasQuota() throws StorageException;
    
    long getQuota() throws QuotaNotFoundException, StorageException;
    
    void setQuota(long quota) throws StorageException;
    
    void removeQuota() throws QuotaNotFoundException, StorageException;
    
    boolean hasQuota(String user) throws UserNotFoundException, StorageException;
    
    long getQuota(String user) throws UserNotFoundException, QuotaNotFoundException, StorageException;
    
    void setQuota(String user, long quota) throws UserNotFoundException, StorageException;
    
    void removeQuota(String user) throws UserNotFoundException, QuotaNotFoundException, StorageException;

}
