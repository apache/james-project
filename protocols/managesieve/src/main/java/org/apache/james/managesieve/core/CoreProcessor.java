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

import com.google.common.base.Throwables;
import org.apache.james.managesieve.api.AuthenticationRequiredException;
import org.apache.james.managesieve.api.ManageSieveRuntimeException;
import org.apache.james.managesieve.api.Session;
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
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CoreProcessor implements CoreCommands {
    
    public static final String IMPLEMENTATION_DESCRIPTION = "Apache ManageSieve v1.0";
    public static final String MANAGE_SIEVE_VERSION = "1.0";
    
    private final SieveRepository sieveRepository;
    private final UsersRepository usersRepository;
    private final Session session;
    private final SieveParser parser;

    public CoreProcessor(Session session, SieveRepository repository, UsersRepository usersRepository, SieveParser parser) {
        this.session = session;
        this.sieveRepository = repository;
        this.usersRepository = usersRepository;
        this.parser = parser;
        ensureUser();
    }

    public Map<Capabilities, String> capability() {
        Map<Capabilities, String> capabilities = new HashMap<Capabilities, String>();
        capabilities.put(Capabilities.IMPLEMENTATION, IMPLEMENTATION_DESCRIPTION);
        capabilities.put(Capabilities.VERSION, MANAGE_SIEVE_VERSION);
        StringBuilder builder = new StringBuilder();
        for (String extension : parser.getExtensions()) {
            builder.append(extension).append(' ');
        }
        String extensions = builder.toString().trim();
        if (!extensions.isEmpty()) {
            capabilities.put(Capabilities.SIEVE, extensions);
        }
        if (isAuthenticated()) {
            capabilities.put(Capabilities.OWNER, getUser());
        }
        capabilities.put(Capabilities.GETACTIVE, null);
        return capabilities;
    }

    public List<String> checkScript(String content) throws AuthenticationRequiredException, SyntaxException {
        authenticationCheck();
        return parser.parse(content);
    }

    public void deleteScript(String name) throws AuthenticationRequiredException, ScriptNotFoundException, IsActiveException {
        authenticationCheck();
        try {
            sieveRepository.deleteScript(getUser(), name);
        } catch (StorageException ex) {
            throw new ManageSieveRuntimeException(ex);
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    public String getScript(String name) throws AuthenticationRequiredException, ScriptNotFoundException, StorageException {
        authenticationCheck();
        try {
            return sieveRepository.getScript(getUser(), name);
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    public void haveSpace(String name, long size) throws AuthenticationRequiredException, QuotaExceededException {
        authenticationCheck();
        try {
            sieveRepository.haveSpace(getUser(), name, size);
        } catch (SieveRepositoryException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    public List<ScriptSummary> listScripts() throws AuthenticationRequiredException {
        authenticationCheck();
        try {
            return sieveRepository.listScripts(getUser());
        } catch (SieveRepositoryException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    public List<String> putScript(String name, String content) throws AuthenticationRequiredException, SyntaxException, QuotaExceededException {
        authenticationCheck();
        List<String> warnings = parser.parse(content);
        try {
            sieveRepository.putScript(getUser(), name, content);
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
        } catch (StorageException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
        return warnings;
    }

    public void renameScript(String oldName, String newName) throws AuthenticationRequiredException, ScriptNotFoundException, DuplicateException {
        authenticationCheck();
        try {
            sieveRepository.renameScript(getUser(), oldName, newName);
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
        } catch (StorageException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    public void setActive(String name) throws AuthenticationRequiredException, ScriptNotFoundException {
        authenticationCheck();
        try {
            sieveRepository.setActive(getUser(), name);
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
        } catch (StorageException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    public String getActive() throws AuthenticationRequiredException, ScriptNotFoundException, StorageException {
        authenticationCheck();
        try {
            return sieveRepository.getActive(getUser());
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }
    
    protected String getUser() {
        return session.getUser();
    }

    private void ensureUser() {
        try {
            if (usersRepository.contains(session.getUser())) {
                throw new RuntimeException("User " + session.getUser() + " not found");
            }
        } catch (UsersRepositoryException e) {
            Throwables.propagate(e);
        }
    }
    
    protected void authenticationCheck() throws AuthenticationRequiredException {
        if (!isAuthenticated()) {
            throw new AuthenticationRequiredException();
        }
    }

    protected boolean isAuthenticated() {
        return session.isAuthenticated();
    }
}