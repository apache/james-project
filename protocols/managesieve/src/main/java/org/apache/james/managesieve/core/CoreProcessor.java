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
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.james.managesieve.api.ArgumentException;
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
    public String capability(Session session) {
        return Joiner.on("\r\n").join(
            Iterables.transform(computeCapabilityMap(session).entrySet(), new Function<Map.Entry<Capabilities,String>, String>() {
                public String apply(Map.Entry<Capabilities, String> capabilitiesStringEntry) {
                    return computeCapabilityEntryString(capabilitiesStringEntry);
                }
            })) + "\r\nOK";
    }

    private Map<Capabilities, String> computeCapabilityMap(Session session) {
        Map<Capabilities, String> capabilities = Maps.newHashMap(capabilitiesBase);
        if (session.isAuthenticated()) {
            capabilities.put(Capabilities.OWNER, session.getUser());
        }
        return capabilities;
    }

    private String computeCapabilityEntryString(Map.Entry<Capabilities, String> entry) {
        return "\"" + entry.getKey().toString() + "\"" +
            ( entry.getValue() == null ? "" : " \"" + entry.getValue() + "\"" );
    }

    @Override
    public String checkScript(Session session, String content) {
        try {
            authenticationCheck(session);
            return manageWarnings(parser.parse(content));
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (SyntaxException ex) {
            return sanitizeString("NO \"Syntax Error: " + ex.getMessage() + "\"");
        }
    }

    private String manageWarnings(List<String> warnings) {
        if (!warnings.isEmpty()) {
            return "OK (WARNINGS) " + Joiner.on(' ').join(Iterables.transform(warnings, new Function<String, String>() {
                public String apply(String s) {
                    return '\"' + s + '"';
                }
            }));
        } else {
            return "OK";
        }
    }

    private String sanitizeString(String message) {
        return Joiner.on("\r\n").join(Splitter.on('\n').split(message));
    }

    @Override
    public String deleteScript(Session session, String name) {
        try {
            authenticationCheck(session);
            sieveRepository.deleteScript(session.getUser(), name);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"There is no script by that name\"";
        } catch (IsActiveException ex) {
            return "NO (ACTIVE) \"You may not delete an active script\"";
        }  catch (UserNotFoundException e) {
            return "NO : Invalid user " + session.getUser();
        } catch (StorageException e) {
            return "NO : Storage Exception : " + e.getMessage();
        }
        return "OK";
    }

    @Override
    public String getScript(Session session, String name) {
        try {
            authenticationCheck(session);
            String scriptContent = sieveRepository.getScript(session.getUser(), name);
            return "{" + scriptContent.length() + "}" + "\r\n" + scriptContent + "\r\nOK";
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"There is no script by that name\"";
        } catch (StorageException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        } catch (UserNotFoundException e) {
            return "NO : Invalid user " + session.getUser();
        }
    }

    @Override
    public String haveSpace(Session session, String name, long size) {
        try {
            authenticationCheck(session);
            sieveRepository.haveSpace(session.getUser(), name, size);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (QuotaExceededException ex) {
            return "NO (QUOTA/MAXSIZE) \"Quota exceeded\"";
        } catch (UserNotFoundException e) {
            return "NO user not found : " + session.getUser();
        } catch (StorageException e) {
            return "NO storage exception : " + e.getMessage();
        }
        return "OK";
    }

    @Override
    public String listScripts(Session session) {
        try {
            authenticationCheck(session);
            String list = Joiner.on("\r\n").join(
                Iterables.transform(sieveRepository.listScripts(session.getUser()), new Function<ScriptSummary, String>() {
                    public String apply(ScriptSummary scriptSummary) {
                        return '"' + scriptSummary.getName() + '"' + (scriptSummary.isActive() ? " ACTIVE" : "");
                    }
                }));
            if (Strings.isNullOrEmpty(list)) {
                return "OK";
            } else {
                return list + "\r\nOK";
            }
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (UserNotFoundException e) {
            return "NO user not found : " + session.getUser();
        } catch (StorageException e) {
            return "NO storage exception : " + e.getMessage();
        }
    }

    @Override
    public String putScript(Session session, String name, String content) {
        try {
            authenticationCheck(session);
            sieveRepository.putScript(session.getUser(), name, content);
            return manageWarnings(parser.parse(content));
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (SyntaxException ex) {
            return Joiner.on("\r\n").join(Splitter.on('\n').split("NO \"Syntax Error: " + ex.getMessage() + "\""));
        } catch (QuotaExceededException ex) {
            return "NO (QUOTA/MAXSIZE) \"Quota exceeded\"";
        } catch (UserNotFoundException e) {
            return "NO user not found : " + session.getUser();
        } catch (StorageException e) {
            return "NO storage exception : " + e.getMessage();
        }
    }

    @Override
    public String renameScript(Session session, String oldName, String newName) {
        try {
            authenticationCheck(session);
            sieveRepository.renameScript(session.getUser(), oldName, newName);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"There is no script by that name\"";
        }  catch (DuplicateException ex) {
            return "NO (ALREADYEXISTS) \"A script with that name already exists\"";
        } catch (UserNotFoundException e) {
            return "NO user not found : " + session.getUser();
        } catch (StorageException e) {
            return "NO storage exception : " + e.getMessage();
        }
        return "OK";
    }

    @Override
    public String setActive(Session session, String name) {
        try {
            authenticationCheck(session);
            sieveRepository.setActive(session.getUser(), name);
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"There is no script by that name\"";
        } catch (UserNotFoundException e) {
            return "NO : User not found";
        } catch (StorageException e) {
            return "NO : Storage exception : " + e.getMessage();
        }
        return "OK";
    }

    @Override
    public String getActive(Session session) {
        try {
            authenticationCheck(session);
            return sieveRepository.getActive(session.getUser()) + "\r\nOK";
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"" + ex.getMessage() + "\"";
        } catch (StorageException ex) {
            return "NO \"" + ex.getMessage() + "\"";
        } catch (UserNotFoundException e) {
            return "NO : User not found";
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
    public String chooseMechanism(Session session, String mechanism) {
        try {
            if (Strings.isNullOrEmpty(mechanism)) {
                return "NO ManageSieve syntax is incorrect : You must specify a SASL mechanism as an argument of AUTHENTICATE command";
            }
            String unquotedMechanism = unquotaIfNeeded(mechanism);
            SupportedMechanism supportedMechanism = SupportedMechanism.retrieveMechanism(unquotedMechanism);

            session.setChoosedAuthenticationMechanism(supportedMechanism);
            session.setState(Session.State.AUTHENTICATION_IN_PROGRESS);
            AuthenticationProcessor authenticationProcessor = authenticationProcessorMap.get(supportedMechanism);
            return authenticationProcessor.initialServerResponse(session);
        } catch (UnknownSaslMechanism unknownSaslMechanism) {
            return "NO " + unknownSaslMechanism.getMessage();
        }
    }

    @Override
    public String authenticate(Session session, String suppliedData) {
        try {
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
        } catch (AuthenticationException e) {
            return "NO Authentication failed with " + e.getCause().getClass() + " : " + e.getMessage();
        } catch (SyntaxException e) {
            return "NO ManageSieve syntax is incorrect : " + e.getMessage();
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