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

package org.apache.james.managesieve.core;

import org.apache.james.managesieve.api.AuthenticationRequiredException;
import org.apache.james.managesieve.api.ManageSieveRuntimeException;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.Session.UserListener;
import org.apache.james.managesieve.api.SieveParser;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.managesieve.api.commands.CoreCommands;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.SieveRepositoryException;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.sieverepository.api.exception.UserNotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <code>CoreProcessor</code>
 */
public class CoreProcessor implements CoreCommands {
    
    public static final String IMPLEMENTATION_DESCRIPTION = "Apache ManageSieve v1.0";
    public static final String MANAGE_SIEVE_VERSION = "1.0";
    
    private SieveRepository _repository = null;
    private Session _session = null;
    private SieveParser _parser = null;

    /**
     * Creates a new instance of CoreProcessor.
     *
     */
    private CoreProcessor() {
        super();
    }
    
    /**
     * Creates a new instance of CoreProcessor.
     *
     */
    public CoreProcessor(Session session, SieveRepository repository, SieveParser parser) {
        this();
        _session = session;
        _repository = repository;
        _parser = parser;

        // Ensure the session user is defined in the repository
        _session.addUserListener(new UserListener() {

            public void notifyChange(String user) {
                ensureUser(user);
            }
        });
    }

    /**
     * @see org.apache.james.managesieve.api.commands.Capability#capability()
     */
    public Map<Capabilities, String> capability() {
        Map<Capabilities, String> capabilities = new HashMap<Capabilities, String>();
        capabilities.put(Capabilities.IMPLEMENTATION, IMPLEMENTATION_DESCRIPTION);
        capabilities.put(Capabilities.VERSION, MANAGE_SIEVE_VERSION);
        StringBuilder builder = new StringBuilder();
        for (String extension : _parser.getExtensions())
        {
            builder.append(extension).append(' ');
        }
        String extensions = builder.toString().trim();
        if (!extensions.isEmpty())
        {
            capabilities.put(Capabilities.SIEVE, extensions);
        }
        if (isAuthenticated())
        {
            capabilities.put(Capabilities.OWNER, getUser());
        }
        capabilities.put(Capabilities.GETACTIVE, null);
        return capabilities;
    }

    /**
     * @see org.apache.james.managesieve.api.commands.CheckScript#checkScript(String)
     */
    public List<String> checkScript(String content) throws AuthenticationRequiredException,
            SyntaxException {
        authenticationCheck();
        return _parser.parse(content);
    }

    /**
     * @see org.apache.james.managesieve.api.commands.DeleteScript#deleteScript(String)
     */
    public void deleteScript(String name) throws AuthenticationRequiredException,
            ScriptNotFoundException, IsActiveException {
        authenticationCheck();
        try {
            _repository.deleteScript(getUser(), name);
        } catch (StorageException ex) {
            throw new ManageSieveRuntimeException(ex);
        } catch (UserNotFoundException ex) {
            // Should not happen as the UserListener should ensure the session user is defined in the repository
            throw new ManageSieveRuntimeException(ex);
        }
    }

    /**
     * @see org.apache.james.managesieve.api.commands.GetScript#getScript(String)
     */
    public String getScript(String name) throws AuthenticationRequiredException,
        ScriptNotFoundException, StorageException {
        authenticationCheck();
        String script = null;
        try {
            script = _repository.getScript(getUser(), name);
        } catch (UserNotFoundException ex) {
            // Should not happen as the UserListener should ensure the session user is defined in the repository
            throw new ManageSieveRuntimeException(ex);
        }
        return script;
    }

    /**
     * @see org.apache.james.managesieve.api.commands.HaveSpace#haveSpace(String, long)
     */
    public void haveSpace(String name, long size) throws AuthenticationRequiredException,
            QuotaExceededException {
        authenticationCheck();
        try {
            _repository.haveSpace(getUser(), name, size);
        } catch (SieveRepositoryException ex) {
            // Should not happen as the UserListener should ensure the session user is defined in the repository
            throw new ManageSieveRuntimeException(ex);
        }
    }

    /**
     * @see org.apache.james.managesieve.api.commands.ListScripts#listScripts()
     */
    public List<ScriptSummary> listScripts() throws AuthenticationRequiredException {
        authenticationCheck();
        List<ScriptSummary> summaries = null;
        try {
            summaries = _repository.listScripts(getUser());
        } catch (SieveRepositoryException ex) {
            // Should not happen as the UserListener should ensure the session user is defined in the repository
            throw new ManageSieveRuntimeException(ex);
        }
        return summaries;
    }

    /**
     * @see org.apache.james.managesieve.api.commands.PutScript#putScript(String, String)
     */
    public List<String> putScript(String name, String content)
            throws AuthenticationRequiredException, SyntaxException, QuotaExceededException {
        authenticationCheck();
        List<String> warnings = _parser.parse(content);
        try {
            _repository.putScript(getUser(), name, content);
        } catch (UserNotFoundException ex) {
            // Should not happen as the UserListener should ensure the session user is defined in the repository
            throw new ManageSieveRuntimeException(ex);
        } catch (StorageException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
        return warnings;
    }

    /**
     * @see org.apache.james.managesieve.api.commands.RenameScript#renameScript(String, String)
     */
    public void renameScript(String oldName, String newName)
            throws AuthenticationRequiredException, ScriptNotFoundException,
            DuplicateException {
        authenticationCheck();
        try {
            _repository.renameScript(getUser(), oldName, newName);
        } catch (UserNotFoundException ex) {
            // Should not happen as the UserListener should ensure the session user is defined in the repository
            throw new ManageSieveRuntimeException(ex);
        } catch (StorageException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    /**
     * @see org.apache.james.managesieve.api.commands.SetActive#setActive(String)
     */
    public void setActive(String name) throws AuthenticationRequiredException,
            ScriptNotFoundException {
        authenticationCheck();
        try {
            _repository.setActive(getUser(), name);
        } catch (UserNotFoundException ex) {
            // Should not happen as the UserListener should ensure the session user is defined in the repository
            throw new ManageSieveRuntimeException(ex);
        } catch (StorageException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }   
    
    protected String getUser()
    {
        return _session.getUser();
    }
    
    protected void ensureUser(String user) {
        try {
            if (!_repository.hasUser(user)) {
                _repository.addUser(user);
            }
        } catch (SieveRepositoryException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }
    
    protected void authenticationCheck() throws AuthenticationRequiredException
    {
        if (!isAuthenticated())
        {
            throw new AuthenticationRequiredException();
        }
    }

    protected boolean isAuthenticated()
    {
        return _session.isAuthenticated();
    }

    /**
     * @see org.apache.james.managesieve.api.commands.GetActive#getActive()
     */
    public String getActive() throws AuthenticationRequiredException, ScriptNotFoundException, StorageException {
        authenticationCheck();
        
        String script = null;
        try {
            script = _repository.getActive(getUser());
        } catch (UserNotFoundException ex) {
            // Should not happen as the UserListener should ensure the session
            // user is defined in the repository
            throw new ManageSieveRuntimeException(ex);
        }
        return script;
    }

}
