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

package org.apache.james.managesieve.mock;

import org.apache.commons.io.IOUtils;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.DuplicateUserException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.sieverepository.api.exception.UserNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


/**
 * <code>MockSieveRepository</code>
 */
public class MockSieveRepository implements SieveRepository {
    
    public class SieveScript
    {
        private String _name = null;
        private String _content = null;
        private boolean _isActive = false;
     
        /**
         * Creates a new instance of SieveScript.
         *
         */
        private SieveScript() {
            super();
        }
        
        /**
         * Creates a new instance of SieveScript.
         *
         */
        public SieveScript(String content, boolean isActive) {
            this();
            setContent(content);
            setActive(isActive);
        }
        
        /**
         * @return the name
         */
        public String getName() {
            return _name;
        }
        
        /**
         * @param name the name to set
         */
        public void setName(String name) {
            _name = name;
        }
        
        /**
         * @return the content
         */
        public String getContent() {
            return _content;
        }
        
        /**
         * @param content the content to set
         */
        public void setContent(String content) {
            _content = content;
        }
        
        /**
         * @return the isActive
         */
        public boolean isActive() {
            return _isActive;
        }
        
        /**
         * @param isActive the isActive to set
         */
        public void setActive(boolean isActive) {
            _isActive = isActive;
        }
    }
    
    Map<String,Map<String, SieveScript>> _repository = null;

    /**
     * Creates a new instance of MockSieveRepository.
     *
     */
    public MockSieveRepository() {
        _repository = new HashMap<String,Map<String, SieveScript>>();
    }

    /**
     * @see SieveRepository#addUser(String)
     */
    public void addUser(String user) throws DuplicateUserException, StorageException {
        if (_repository.containsKey(user))
        {
            throw new DuplicateUserException(user);
        }
        _repository.put(user, new HashMap<String, SieveScript>());
    }

    /**
     * @see SieveRepository#deleteScript(String, String)
     */
    public void deleteScript(String user, String name) throws UserNotFoundException,
            ScriptNotFoundException, IsActiveException, StorageException {
        if (!_repository.containsKey(user))
        {
            throw new UserNotFoundException(user);
        }
        SieveScript script = _repository.get(user).get(name);
        if (null == script)
        {
            throw new ScriptNotFoundException(name);
        }
        if (script.isActive())
        {
            throw new IsActiveException(name);
        }
        _repository.get(user).remove(name);
    }

    /**
     * @see SieveRepository#getActive(String)
     */
    public InputStream getActive(String user) throws UserNotFoundException, ScriptNotFoundException, StorageException {
        if (!_repository.containsKey(user))
        {
            throw new UserNotFoundException(user);
        }
        Set<Entry<String, SieveScript>> scripts = _repository.get(user).entrySet();
        String content = null;
        for (final Entry<String, SieveScript> entry : scripts)
        {
            if (entry.getValue().isActive())
            {
                content = entry.getValue().getContent();
                break;
            }
        }
        if (null == content)
        {
            throw new ScriptNotFoundException();
        }
        try {
            return IOUtils.toInputStream(content, "UTF-8");
        } catch (IOException e) {
            throw new StorageException();
        }

    }

    /**
     * @see SieveRepository#getQuota()
     */
    public long getQuota() throws QuotaNotFoundException {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * @see SieveRepository#getQuota(String)
     */
    public long getQuota(String user) throws UserNotFoundException, QuotaNotFoundException {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * @see SieveRepository#getScript(String, String)
     */
    public InputStream getScript(String user, String name) throws UserNotFoundException,
            ScriptNotFoundException, StorageException {
        if (!_repository.containsKey(user))
        {
            throw new UserNotFoundException(user);
        }
        SieveScript script = _repository.get(user).get(name);
        if (null == script)
        {
            throw new ScriptNotFoundException(name);
        }
        try {
            return IOUtils.toInputStream(script.getContent(), "UTF-8");
        } catch (IOException e) {
            throw new StorageException();
        }
    }

    /**
     * @see SieveRepository#hasQuota()
     */
    public boolean hasQuota() {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * @see SieveRepository#hasQuota(String)
     */
    public boolean hasQuota(String user) throws UserNotFoundException {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * @see SieveRepository#hasUser(String)
     */
    public boolean hasUser(String user) {
        return _repository.containsKey(user);
    }

    /**
     * @see SieveRepository#haveSpace(String, String, long)
     */
    public void haveSpace(String user, String name, long size) throws UserNotFoundException,
            QuotaExceededException {
        if (!_repository.containsKey(user))
        {
            throw new UserNotFoundException(user);
        }
    }

    /**
     * @see SieveRepository#listScripts(String)
     */
    public List<ScriptSummary> listScripts(String user) throws UserNotFoundException {
        if (!_repository.containsKey(user))
        {
            throw new UserNotFoundException(user);
        }
        Set<Entry<String, SieveScript>> scripts = _repository.get(user).entrySet();
        List<ScriptSummary> summaries = new ArrayList<ScriptSummary>(scripts.size());
        for (final Entry<String, SieveScript> entry : scripts) {
            summaries.add(new ScriptSummary(entry.getKey(), entry.getValue().isActive()));
        }
        return summaries;
    }

    /**
     * @see SieveRepository#putScript(String, String, String)
     */
    public void putScript(String user, String name, String content) throws UserNotFoundException,
            StorageException, QuotaExceededException {
        if (!_repository.containsKey(user))
        {
            throw new UserNotFoundException(user);
        }
        Map<String,SieveScript> scripts = _repository.get(user);
        scripts.put(name, new SieveScript(content, false));
    }

    /**
     * @see SieveRepository#removeQuota()
     */
    public void removeQuota() throws QuotaNotFoundException, StorageException {
        // TODO Auto-generated method stub

    }

    /**
     * @see SieveRepository#removeQuota(String)
     */
    public void removeQuota(String user) throws UserNotFoundException, QuotaNotFoundException,
            StorageException {
        // TODO Auto-generated method stub

    }

    /**
     * @see SieveRepository#removeUser(String)
     */
    public void removeUser(String user) throws UserNotFoundException, StorageException {
        // TODO Auto-generated method stub

    }

    /**
     * @see SieveRepository#renameScript(String, String, String)
     */
    public void renameScript(String user, String oldName, String newName)
            throws UserNotFoundException, ScriptNotFoundException,
            DuplicateException, StorageException {
        // TODO Auto-generated method stub

    }

    /**
     * @see SieveRepository#setActive(String, String)
     */
    public void setActive(String user, String name) throws UserNotFoundException,
            ScriptNotFoundException, StorageException {

        // Turn off currently active script, if any
        Entry<String, SieveScript> oldActive;
        oldActive = getActiveEntry(user);
        if (null != oldActive) {
            oldActive.getValue().setActive(false);
        }

        // Turn on the new active script if not an empty name
        if ((null != name) && (!name.trim().isEmpty())) {
            if (_repository.get(user).containsKey(name)) {
                _repository.get(user).get(name).setActive(true);
            } else {
                if (null != oldActive) {
                    oldActive.getValue().setActive(true);
                }
                throw new ScriptNotFoundException();
            }
        }
    }

    protected Entry<String, SieveScript> getActiveEntry(String user)
    {
        Set<Entry<String, SieveScript>> scripts = _repository.get(user).entrySet();
        Entry<String, SieveScript> activeEntry = null;
        for (final Entry<String, SieveScript> entry : scripts)
        {
            if (entry.getValue().isActive())
            {
                activeEntry = entry;
                break;
            }
        }
        return activeEntry;
    }

    /**
     * @see SieveRepository#setQuota(long)
     */
    public void setQuota(long quota) throws StorageException {
        // TODO Auto-generated method stub

    }

    /**
     * @see SieveRepository#setQuota(String, long)
     */
    public void setQuota(String user, long quota) throws UserNotFoundException, StorageException {
        // TODO Auto-generated method stub

    }

}
