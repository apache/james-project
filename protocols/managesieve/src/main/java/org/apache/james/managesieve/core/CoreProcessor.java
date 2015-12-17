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
import org.apache.commons.io.IOUtils;
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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CoreProcessor implements CoreCommands {
    
    public static final String IMPLEMENTATION_DESCRIPTION = "Apache ManageSieve v1.0";
    public static final String MANAGE_SIEVE_VERSION = "1.0";
    
    private final SieveRepository sieveRepository;
    private final UsersRepository usersRepository;
    private final SieveParser parser;

    public CoreProcessor(SieveRepository repository, UsersRepository usersRepository, SieveParser parser) {
        this.sieveRepository = repository;
        this.usersRepository = usersRepository;
        this.parser = parser;
    }

    public Map<Capabilities, String> capability(Session session) {
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
        if (session.isAuthenticated()) {
            capabilities.put(Capabilities.OWNER, session.getUser());
        }
        capabilities.put(Capabilities.GETACTIVE, null);
        return capabilities;
    }

    public List<String> checkScript(Session session, String content) throws AuthenticationRequiredException, SyntaxException {
        authenticationCheck(session);
        return parser.parse(content);
    }

    public void deleteScript(Session session, String name) throws AuthenticationRequiredException, ScriptNotFoundException, IsActiveException {
        authenticationCheck(session);
        try {
            sieveRepository.deleteScript(session.getUser(), name);
        } catch (StorageException ex) {
            throw new ManageSieveRuntimeException(ex);
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    public String getScript(Session session, String name) throws AuthenticationRequiredException, ScriptNotFoundException, StorageException {
        authenticationCheck(session);
        try {
            return IOUtils.toString(sieveRepository.getScript(session.getUser(), name));
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
        } catch (IOException ex) {
            // Unable to read script InputStream
            throw new ManageSieveRuntimeException(ex);
        }
    }

    public void haveSpace(Session session, String name, long size) throws AuthenticationRequiredException, QuotaExceededException {
        authenticationCheck(session);
        try {
            sieveRepository.haveSpace(session.getUser(), name, size);
        } catch (SieveRepositoryException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    public List<ScriptSummary> listScripts(Session session) throws AuthenticationRequiredException {
        authenticationCheck(session);
        try {
            return sieveRepository.listScripts(session.getUser());
        } catch (SieveRepositoryException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    public List<String> putScript(Session session, String name, String content) throws AuthenticationRequiredException, SyntaxException, QuotaExceededException {
        authenticationCheck(session);
        List<String> warnings = parser.parse(content);
        try {
            sieveRepository.putScript(session.getUser(), name, content);
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
        } catch (StorageException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
        return warnings;
    }

    public void renameScript(Session session, String oldName, String newName) throws AuthenticationRequiredException, ScriptNotFoundException, DuplicateException {
        authenticationCheck(session);
        try {
            sieveRepository.renameScript(session.getUser(), oldName, newName);
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
        } catch (StorageException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    public void setActive(Session session, String name) throws AuthenticationRequiredException, ScriptNotFoundException {
        authenticationCheck(session);
        try {
            sieveRepository.setActive(session.getUser(), name);
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
        } catch (StorageException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    public String getActive(Session session) throws AuthenticationRequiredException, ScriptNotFoundException, StorageException {
        authenticationCheck(session);
        try {
            return IOUtils.toString(sieveRepository.getActive(session.getUser()));
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
        } catch (IOException e) {
            throw new ManageSieveRuntimeException(e);
        }
    }
    
    protected void authenticationCheck(Session session) throws AuthenticationRequiredException {
        ensureUser(session);
        if (!session.isAuthenticated()) {
            throw new AuthenticationRequiredException();
        }
    }

    private void ensureUser(Session session) {
        try {
            if (session.getUser() == null || !usersRepository.contains(session.getUser())) {
                throw new RuntimeException("User " + session.getUser() + " not found");
            }
        } catch (UsersRepositoryException e) {
            Throwables.propagate(e);
        }
    }

}