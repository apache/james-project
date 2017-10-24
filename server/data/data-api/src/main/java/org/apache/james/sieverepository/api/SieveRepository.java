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
import java.util.List;

import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.joda.time.DateTime;


/**
 * <code>SieveRepository</code>
 */
public interface SieveRepository extends SieveQuotaRepository {

    String NO_SCRIPT_NAME = "";

    void haveSpace(String user, String name, long size) throws QuotaExceededException, StorageException;
    
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
    void putScript(String user, String name, String content) throws StorageException, QuotaExceededException;
    
    List<ScriptSummary> listScripts(String user) throws StorageException;

    DateTime getActivationDateForActiveScript(String user) throws StorageException, ScriptNotFoundException;

    InputStream getActive(String user) throws ScriptNotFoundException, StorageException;
    
    void setActive(String user, String name) throws ScriptNotFoundException, StorageException;
    
    InputStream getScript(String user, String name) throws ScriptNotFoundException, StorageException;
    
    void deleteScript(String user, String name) throws ScriptNotFoundException, IsActiveException, StorageException;
    
    void renameScript(String user, String oldName, String newName) throws ScriptNotFoundException, DuplicateException, StorageException;

}
