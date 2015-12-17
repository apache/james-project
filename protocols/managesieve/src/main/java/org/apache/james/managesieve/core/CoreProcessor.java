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

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
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
    private final Map<Capabilities, String> capabilitiesBase;

    public CoreProcessor(SieveRepository repository, UsersRepository usersRepository, SieveParser parser) {
        this.sieveRepository = repository;
        this.usersRepository = usersRepository;
        this.parser = parser;
        this.capabilitiesBase = precomputeCapabilitiesBase(parser);
    }

    @Override
    public Map<Capabilities, String> capability(Session session) {
        Map<Capabilities, String> capabilities = Maps.newHashMap(capabilitiesBase);
        if (session.isAuthenticated()) {
            capabilities.put(Capabilities.OWNER, session.getUser());
        }
        return capabilities;
    }

    @Override
    public List<String> checkScript(Session session, String content) throws AuthenticationRequiredException, SyntaxException {
        authenticationCheck(session);
        return parser.parse(content);
    }

    @Override
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

    @Override
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

    @Override
    public void haveSpace(Session session, String name, long size) throws AuthenticationRequiredException, QuotaExceededException {
        authenticationCheck(session);
        try {
            sieveRepository.haveSpace(session.getUser(), name, size);
        } catch (SieveRepositoryException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    @Override
    public List<ScriptSummary> listScripts(Session session) throws AuthenticationRequiredException {
        authenticationCheck(session);
        try {
            return sieveRepository.listScripts(session.getUser());
        } catch (SieveRepositoryException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
    public String noop(String tag) {
        if(Strings.isNullOrEmpty(tag)) {
            return "OK \"NOOP completed\"";
        }
        return "OK " + taggify(tag) + " \"DONE\"";
    }

    @Override
    public String unauthenticate(Session session) {
        if (session.isAuthenticated()) {
            session.setAuthentication(false);
            return "OK";
        } else {
            return "NO UNAUTHENTICATE command must be issued in authenticated state";
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


    private String buildExtensions(SieveParser parser) {
        StringBuilder builder = new StringBuilder();
        if (parser.getExtensions() != null) {
            for (String extension : parser.getExtensions()) {
                builder.append(extension).append(' ');
            }
        }
        return builder.toString().trim();
    }

    private String taggify(String tag) {
        String sanitizedTag = unquotaIfNeeded(tag.trim());
        return "(TAG {" + sanitizedTag.length() + "}\r\n" + sanitizedTag + ")";
    }

    private String unquotaIfNeeded(String tag) {
        int startIndex = 0;
        int stopIndex = tag.length();
        if (tag.endsWith("\r\n")) {
            stopIndex -= 2;
        }
        if (tag.charAt(0) == '\"') {
            startIndex = 1;
        }
        if (tag.charAt(tag.length() - 1) == '\"') {
            stopIndex--;
        }
        return tag.substring(startIndex, stopIndex);
    }

    private Map<Capabilities, String> precomputeCapabilitiesBase(SieveParser parser) {
        String extensions = buildExtensions(parser);
        Map<Capabilities, String> capabilitiesBase = new HashMap<Capabilities, String>();
        capabilitiesBase.put(Capabilities.IMPLEMENTATION, IMPLEMENTATION_DESCRIPTION);
        capabilitiesBase.put(Capabilities.VERSION, MANAGE_SIEVE_VERSION);
        if (!extensions.isEmpty()) {
            capabilitiesBase.put(Capabilities.SIEVE, extensions);
        }
        capabilitiesBase.put(Capabilities.GETACTIVE, null);
        return capabilitiesBase;
    }
}