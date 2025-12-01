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

import jakarta.inject.Inject;

import org.apache.james.managesieve.api.ManageSieveException;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SessionTerminatedException;
import org.apache.james.managesieve.util.ParserUtils;
import org.apache.james.sieverepository.api.exception.SieveRepositoryException;

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

    @Inject
    public ManageSieveProcessor(ArgumentParser argumentParser) {
        this.argumentParser = argumentParser;
    }

    public String handleRequest(Session session, String request) throws ManageSieveException, SieveRepositoryException {
        if (request.endsWith("\n")) {
            request = request.substring(0, request.length() - 1);
        }
        if (request.endsWith("\r")) {
            request = request.substring(0, request.length() - 1);
        }

        if (session.getState() == Session.State.AUTHENTICATION_IN_PROGRESS) {
            return matchCommandWithImplementation(session, request.trim(), AUTHENTICATE) + "\r\n";
        }

        int firstWordEndIndex = request.indexOf(' ');
        String arguments = parseArguments(request, firstWordEndIndex);
        String command = parseCommand(request, firstWordEndIndex);
        return matchCommandWithImplementation(session, arguments, command) + "\r\n";
    }

    private String parseCommand(String request, int firstWordEndIndex) {
        String command;
        if (request.contains(" ")) {
            command = request.substring(0, firstWordEndIndex);
        } else {
            command = request;
        }
        return command;
    }

    private String parseArguments(String request, int firstWordEndIndex) {
        if (request.contains(" ")) {
            return request.substring(firstWordEndIndex).trim();
        } else {
            return "";
        }
    }

    private String matchCommandWithImplementation(Session session, String arguments, String command) throws SessionTerminatedException {
        if (command.equalsIgnoreCase(AUTHENTICATE)) {
            // The RFC forbids the AUTHENTICATE command if the session is already authenticated.
            if (session.isAuthenticated()) {
                return "NO \"already authenticated\"";
            }

            // If no authentication is in progress, the authentication mechanism needs to be chosen.
            if (session.getState() != Session.State.AUTHENTICATION_IN_PROGRESS) {
                String mechanism = ParserUtils.unquoteFirst(arguments);
                String result = argumentParser.chooseMechanism(session, mechanism);
                // If the authentication is not in progress, return the result (error) because choosing the mechanism has failed.
                if (session.getState() != Session.State.AUTHENTICATION_IN_PROGRESS) {
                    return result;
                }

                // Skips the whole mechanism, the closing quote, and the space if present.
                // If the request is well-formatted, the arguments are now empty or contain the client's initial response.
                arguments = arguments.substring(arguments.indexOf(mechanism) + mechanism.length() + 1);
                if (arguments.startsWith(" ")) {
                    arguments = arguments.substring(1);
                }
                // If there are is no initial client response left, return the result (initial server response).
                if (arguments.isEmpty()) {
                    return result;
                }
                // Unquote the argument in this case because continuation is not used.
                arguments = ParserUtils.unquoteFirst(arguments);
            }

            // The authentication is in progress, the mechanism has been chosen, and the arguments contain an initial client response.
            return argumentParser.authenticate(session, arguments);
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
