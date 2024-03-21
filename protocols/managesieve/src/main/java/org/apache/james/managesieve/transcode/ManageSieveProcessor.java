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

import org.apache.commons.lang3.StringUtils;
import org.apache.james.managesieve.api.ManageSieveException;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SessionTerminatedException;
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
        if (command.endsWith("\n")) {
            command = command.substring(0, command.length() - 1);
        }
        if (command.endsWith("\r")) {
            command = command.substring(0, command.length() - 1);
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
        if (session.getState() == Session.State.AUTHENTICATION_IN_PROGRESS) {
            return argumentParser.authenticate(session, arguments);
        }
        if (command.equalsIgnoreCase(AUTHENTICATE)) {
            if (StringUtils.countMatches(arguments, "\"") == 4) {
                argumentParser.chooseMechanism(session, arguments);
                int bracket1 = arguments.indexOf('\"');
                int bracket2 = arguments.indexOf('\"', bracket1 + 1);
                int bracket3 = arguments.indexOf('\"', bracket2 + 1);
                int bracket4 = arguments.indexOf('\"', bracket3 + 1);

                return argumentParser.authenticate(session, arguments.substring(bracket3 + 1, bracket4));
            }
            return argumentParser.chooseMechanism(session, arguments);
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

    public String getAdvertisedCapabilities() {
        return argumentParser.getAdvertisedCapabilities();
    }

}
