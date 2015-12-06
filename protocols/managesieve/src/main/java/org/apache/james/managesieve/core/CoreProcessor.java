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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.james.managesieve.api.AuthenticationException;
import org.apache.james.managesieve.api.AuthenticationProcessor;
import org.apache.james.managesieve.api.AuthenticationRequiredException;
import org.apache.james.managesieve.api.ManageSieveRuntimeException;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SessionTerminatedException;
import org.apache.james.managesieve.api.SieveParser;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.managesieve.api.UnknownSaslMechanism;
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

import java.util.Arrays;
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
    private final Map<SupportedMechanism, AuthenticationProcessor> authenticationProcessorMap;

    public CoreProcessor(SieveRepository repository, UsersRepository usersRepository, SieveParser parser) {
        this.sieveRepository = repository;
        this.usersRepository = usersRepository;
        this.parser = parser;
        this.capabilitiesBase = precomputedCapabilitiesBase(parser);
        this.authenticationProcessorMap = new HashMap<SupportedMechanism, AuthenticationProcessor>();
        this.authenticationProcessorMap.put(SupportedMechanism.PLAIN, new PlainAuthenticationProcessor(usersRepository));
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
            String scriptContent = sieveRepository.getScript(session.getUser(), name);
            return "{" + scriptContent.length() + "}" + "\r\n" + scriptContent;
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
        }
    }

    @Override
    public void haveSpace(Session session, String name, long size) throws AuthenticationRequiredException, QuotaExceededException, UserNotFoundException, StorageException {
        authenticationCheck(session);
        sieveRepository.haveSpace(session.getUser(), name, size);
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
    public void setActive(Session session, String name) throws AuthenticationRequiredException, ScriptNotFoundException, UserNotFoundException, StorageException {
        authenticationCheck(session);
        sieveRepository.setActive(session.getUser(), name);
    }

    @Override
    public String getActive(Session session) throws AuthenticationRequiredException, ScriptNotFoundException, StorageException {
        authenticationCheck(session);
        try {
            return sieveRepository.getActive(session.getUser());
        } catch (UserNotFoundException ex) {
            throw new ManageSieveRuntimeException(ex);
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
    public String chooseMechanism(Session session, String mechanism) throws AuthenticationException, UnknownSaslMechanism, SyntaxException {
        if (Strings.isNullOrEmpty(mechanism)) {
            throw new SyntaxException("You must specify a SASL mechanism as an argument of AUTHENTICATE command");
        }
        String unquotedMechanism = unquotaIfNeeded(mechanism);
        SupportedMechanism supportedMechanism = SupportedMechanism.retrieveMechanism(unquotedMechanism);

        session.setChoosedAuthenticationMechanism(supportedMechanism);
        session.setState(Session.State.AUTHENTICATION_IN_PROGRESS);
        AuthenticationProcessor authenticationProcessor = authenticationProcessorMap.get(supportedMechanism);
        return authenticationProcessor.initialServerResponse(session);
    }

    @Override
    public String authenticate(Session session, String suppliedData) throws AuthenticationException, SyntaxException {
        SupportedMechanism currentAuthenticationMechanism = session.getChoosedAuthenticationMechanism();
        AuthenticationProcessor authenticationProcessor = authenticationProcessorMap.get(currentAuthenticationMechanism);
        String authenticatedUsername = authenticationProcessor.isAuthenticationSuccesfull(session, suppliedData);
        if (authenticatedUsername != null) {
            session.setUser(authenticatedUsername);
            session.setState(Session.State.AUTHENTICATED);
            return "OK authentication successfull";
        } else {
            session.setState(Session.State.UNAUTHENTICATED);
            session.setUser(null);
            return "NO authentication failed";
        }
    }

    @Override
    public String unauthenticate(Session session) {
        if (session.isAuthenticated()) {
            session.setState(Session.State.UNAUTHENTICATED);
            session.setUser(null);
            return "OK";
        } else {
            return "NO UNAUTHENTICATE command must be issued in authenticated state";
        }
    }

    @Override
    public void logout() throws SessionTerminatedException {
        throw new SessionTerminatedException();
    }

    @Override
    public String startTLS(Session session) {
        if (session.getState() == Session.State.UNAUTHENTICATED) {
            if (session.isSslEnabled()) {
                return "NO You can't enable two time SSL encryption";
            }
            session.setState(Session.State.SSL_NEGOCIATION);
            return "OK";
        } else {
            return "NO command STARTTLS is issued in the wrong state. It must be issued as you are unauthenticated";
        }
    }

    protected void authenticationCheck(Session session) throws AuthenticationRequiredException {
        if (!session.isAuthenticated()) {
            throw new AuthenticationRequiredException();
        }
        ensureUser(session);
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
        return Joiner.on(' ').join(parser.getExtensions()).trim();
    }

    private String taggify(String tag) {
        String sanitizedTag = unquotaIfNeeded(tag.trim());
        return "(TAG {" + sanitizedTag.length() + "}\r\n" + sanitizedTag + ")";
    }

    private String unquotaIfNeeded(String tag) {
        if (Strings.isNullOrEmpty(tag)) {
            return "";
        }
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

    private Map<Capabilities, String> precomputedCapabilitiesBase(SieveParser parser) {
        String extensions = buildExtensions(parser);
        Map<Capabilities, String> capabilitiesBase = new HashMap<Capabilities, String>();
        capabilitiesBase.put(Capabilities.IMPLEMENTATION, IMPLEMENTATION_DESCRIPTION);
        capabilitiesBase.put(Capabilities.VERSION, MANAGE_SIEVE_VERSION);
        capabilitiesBase.put(Capabilities.SASL, constructSaslSupportedAuthenticationMechanisms());
        capabilitiesBase.put(Capabilities.STARTTLS, null);
        if (!extensions.isEmpty()) {
            capabilitiesBase.put(Capabilities.SIEVE, extensions);
        }
        return capabilitiesBase;
    }

    private String constructSaslSupportedAuthenticationMechanisms() {
        return Joiner.on(' ')
            .join(Lists.transform(
                Arrays.asList(SupportedMechanism.values()),
                new Function<SupportedMechanism, String>() {
                    public String apply(SupportedMechanism supportedMechanism) {
                        return supportedMechanism.toString();
                    }
                }));
    }
}