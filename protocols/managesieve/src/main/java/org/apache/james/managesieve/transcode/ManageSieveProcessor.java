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

package org.apache.james.managesieve.transcode;

import org.apache.james.managesieve.api.ManageSieveException;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SessionTerminatedException;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.sieverepository.api.exception.SieveRepositoryException;

import com.google.common.collect.ImmutableList;

public class ManageSieveProcessor {
    public static final String AUTHENTICATE = "AUTHENTICATE";
    public static final String CAPABILITY = "CAPABILITY";
    public static final String CHECKSCRIPT = "CHECKSCRIPT";
    public static final String DELETESCRIPT = "DELETESCRIPT";
    public static final String GETACTIVE = "GETACTIVE";
    public static final String GETSCRIPT = "GETSCRIPT";
    public static final String HAVESPACE = "HAVESPACE";
    public static final String LISTSCRIPTS = "LISTSCRIPTS";
    public static final String LOGOUT = "LOGOUT";
    public static final String NOOP = "NOOP";
    public static final String PUTSCRIPT = "PUTSCRIPT";
    public static final String RENAMESCRIPT = "RENAMESCRIPT";
    public static final String SETACTIVE = "SETACTIVE";
    public static final String STARTTLS = "STARTTLS";
    public static final String UNAUTHENTICATE = "UNAUTHENTICATE";

    private final ArgumentParser argumentParser;
    private final ManageSieveSaslProcessor saslProcessor;

    public ManageSieveProcessor(ArgumentParser argumentParser,
                                ImmutableList<SaslMechanism> saslMechanisms,
                                SaslAuthenticator saslAuthenticator) {
        this.argumentParser = argumentParser;
        this.saslProcessor = new ManageSieveSaslProcessor(saslMechanisms, saslAuthenticator);
    }

    public String handleRequest(Session session, String request) throws ManageSieveException, SieveRepositoryException {
        String requestWithoutLineEnding = removeLineEnding(request);
        if (session.getState() == Session.State.AUTHENTICATION_IN_PROGRESS) {
            return saslProcessor.handleContinuation(session, requestWithoutLineEnding) + "\r\n";
        }

        int firstWordEndIndex = requestWithoutLineEnding.indexOf(' ');
        String arguments = parseArguments(requestWithoutLineEnding, firstWordEndIndex);
        String command = parseCommand(requestWithoutLineEnding, firstWordEndIndex);
        return matchCommandWithImplementation(session, arguments, command) + "\r\n";
    }

    public void close(Session session) {
        saslProcessor.close(session);
    }

    private String removeLineEnding(String request) {
        if (request.endsWith("\r\n")) {
            return request.substring(0, request.length() - 2);
        }
        if (request.endsWith("\n") || request.endsWith("\r")) {
            return request.substring(0, request.length() - 1);
        }
        return request;
    }

    private String parseCommand(String request, int firstWordEndIndex) {
        if (firstWordEndIndex >= 0) {
            return request.substring(0, firstWordEndIndex);
        }
        return request;
    }

    private String parseArguments(String request, int firstWordEndIndex) {
        if (firstWordEndIndex >= 0) {
            return request.substring(firstWordEndIndex + 1).trim();
        }
        return "";
    }

    private String matchCommandWithImplementation(Session session, String arguments, String command) throws SessionTerminatedException {
        if (command.equalsIgnoreCase(AUTHENTICATE)) {
            return saslProcessor.startAuthentication(session, arguments);
        } else if (command.equalsIgnoreCase(CAPABILITY)) {
            return argumentParser.capability(session, arguments);
        } else if (command.equalsIgnoreCase(CHECKSCRIPT)) {
            return argumentParser.checkScript(session, arguments);
        } else if (command.equalsIgnoreCase(DELETESCRIPT)) {
            return argumentParser.deleteScript(session, arguments);
        } else if (command.equalsIgnoreCase(GETSCRIPT)) {
            return argumentParser.getScript(session, arguments);
        } else if (command.equalsIgnoreCase(HAVESPACE)) {
            return argumentParser.haveSpace(session, arguments);
        } else if (command.equalsIgnoreCase(LISTSCRIPTS)) {
            return argumentParser.listScripts(session, arguments);
        } else if (command.equalsIgnoreCase(LOGOUT)) {
            argumentParser.logout();
        } else if (command.equalsIgnoreCase(NOOP)) {
            return argumentParser.noop(arguments);
        } else if (command.equalsIgnoreCase(PUTSCRIPT)) {
            return argumentParser.putScript(session, arguments);
        } else if (command.equalsIgnoreCase(RENAMESCRIPT)) {
            return argumentParser.renameScript(session, arguments);
        } else if (command.equalsIgnoreCase(SETACTIVE)) {
            return argumentParser.setActive(session, arguments);
        } else if (command.equalsIgnoreCase(STARTTLS)) {
            return argumentParser.startTLS(session);
        } else if (command.equalsIgnoreCase(UNAUTHENTICATE)) {
            return argumentParser.unauthenticate(session, arguments);
        }
        return "NO unknown " + command + " command";
    }

    public String getAdvertisedCapabilities(Session session) {
        return argumentParser.capability(session, "") + "\r\n";
    }
}
