/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mpt.protocol;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.james.mpt.protocol.ProtocolSession.TimerCommand;

/**
 * A builder which generates a ProtocolSession from a test file.
 * 
 * @version $Revision$
 */
public class FileProtocolSessionBuilder extends ProtocolSessionBuilder {

    public static final String DEBUG = "DEBUG";
    public static final String INFO = "INFO";
    public static final String WARN = "WARN";
    public static final String ERR = "ERR";
    /** Reads and discards server lines until one matches: <code>UNTIL: regex</code>. */
    public static final String UNTIL_TAG = "UNTIL:";
    /** Defines a runtime variable: <code>SET: name=value</code>. */
    public static final String SET_TAG = "SET:";
    /** Defines a runtime variable holding a base64 encoded value (\0 = NUL): <code>SET-BASE64: name=value</code>. */
    public static final String SET_BASE64_TAG = "SET-BASE64:";
    /** Upgrades the current session to TLS. */
    public static final String STARTTLS = "STARTTLS";

    private static final int TIMER_COMMAND_START = TIMER.length() + 1;
    private static final int TIMER_COMMAND_END = TIMER_COMMAND_START + 5;

    /**
     * Builds a ProtocolSession by reading lines from the test file with the
     * supplied name.
     * 
     * @param fileName
     *            The name of the protocol session file.
     * @return The ProtocolSession
     */
    @Override
    public ProtocolSession buildProtocolSession(String fileName) throws Exception {
        ProtocolSession session = new ProtocolSession();
        addTestFile(fileName, session);
        return session;
    }

    /**
     * Adds all protocol elements from a test file to the ProtocolSession
     * supplied.
     * 
     * @param fileName
     *            The name of the protocol session file.
     * @param session
     *            The ProtocolSession to add the elements to.
     */
    public void addTestFile(String fileName, ProtocolSession session) throws Exception {
        // Need to find local resource.
        try (InputStream is = this.getClass().getResourceAsStream(fileName)) {
            if (is == null) {
                throw new Exception("Test Resource '" + fileName + "' not found.");
            }
            addProtocolLinesFromStream(is, session, fileName);
        }
    }

    /**
     * Reads ProtocolElements from the supplied InputStream and adds them to the
     * ProtocolSession.
     * 
     * @param is
     *            The input stream containing the protocol definition.
     * @param session
     *            The ProtocolSession to add elements to.
     * @param fileName
     *            The name of the source file, for error messages.
     */
    public void addProtocolLinesFromStream(InputStream is, ProtocolSession session, String fileName) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            new Parser(reader, session, fileName).parse();
        }
    }

    private static class Parser {
        private final BufferedReader reader;
        private final ProtocolSession session;
        private final String fileName;
        private int sessionNumber = -1;
        private int lineNumber = 0;
        private String lastClientMsg = "";

        Parser(BufferedReader reader, ProtocolSession session, String fileName) {
            this.reader = reader;
            this.session = session;
            this.fileName = fileName;
        }

        private String nextLine() throws Exception {
            String line = reader.readLine();
            if (line != null) {
                lineNumber++;
            }
            return line;
        }

        void parse() throws Exception {
            String next;
            while ((next = nextLine()) != null) {
                String location = fileName + ":" + lineNumber;
                try {
                    parseLine(next, location);
                } catch (Exception e) {
                    throw new Exception("Parse error at " + location + ": " + e.getMessage(), e);
                }
            }
        }

        private void parseLine(String next, String location) throws Exception {
            if (SERVER_CONTINUATION_TAG.equals(next)) {
                session.cont(sessionNumber);
            } else if (next.startsWith(CLIENT_TAG)) {
                String clientMsg = "";
                if (next.length() > 3) {
                    clientMsg = next.substring(3);
                }
                session.cl(sessionNumber, clientMsg);
                lastClientMsg = clientMsg;
            } else if (next.startsWith(SERVER_TAG)) {
                String serverMsg = "";
                if (next.length() > 3) {
                    serverMsg = next.substring(3);
                }
                session.sl(sessionNumber, validRegex(serverMsg), location, lastClientMsg);
            } else if (next.startsWith(UNTIL_TAG)) {
                String serverMsg = next.substring(UNTIL_TAG.length()).trim();
                session.until(sessionNumber, validRegex(serverMsg), location, lastClientMsg);
            } else if (next.startsWith(SET_TAG) || next.startsWith(SET_BASE64_TAG)) {
                boolean base64 = next.startsWith(SET_BASE64_TAG);
                String assignment = next.substring(base64 ? SET_BASE64_TAG.length() : SET_TAG.length()).trim();
                int separator = assignment.indexOf('=');
                if (separator <= 0) {
                    throw new Exception("Expecting 'SET: name=value' or 'SET-BASE64: name=value', got: " + next);
                }
                String name = assignment.substring(0, separator).trim();
                String value = assignment.substring(separator + 1);
                if (base64) {
                    session.setBase64(name, value);
                } else {
                    session.set(name, value);
                }
            } else if (next.trim().equals(STARTTLS)) {
                session.startTls(sessionNumber);
            } else if (next.startsWith(WAIT)) {
                if (next.length() > 5) {
                    session.wait(sessionNumber, Long.parseLong(next.substring(5).trim()));
                } else {
                    throw new Exception("Invalid line length on WAIT instruction : " + next);
                }
            } else if (next.startsWith(LOG)) {
                String logInstruction = next.substring(Math.min(next.length(), 4));
                if (logInstruction.startsWith(DEBUG)) {
                    session.log(sessionNumber, ProtocolSession.LolLevel.Debug, logInstruction.substring(Math.min(logInstruction.length(), 6)));
                } else if (logInstruction.startsWith(INFO)) {
                    session.log(sessionNumber, ProtocolSession.LolLevel.Info, logInstruction.substring(Math.min(logInstruction.length(), 5)));
                } else if (logInstruction.startsWith(WARN)) {
                    session.log(sessionNumber, ProtocolSession.LolLevel.Warn, logInstruction.substring(Math.min(logInstruction.length(), 5)));
                } else if (logInstruction.startsWith(ERR)) {
                    session.log(sessionNumber, ProtocolSession.LolLevel.Err, logInstruction.substring(Math.min(logInstruction.length(), 4)));
                } else {
                    throw new Exception("Unrecognized log level for " + next);
                }
            } else if (next.startsWith(REINIT)) {
                session.reinit(sessionNumber);
            } else if (next.startsWith(AWAIT)) {
                session.await(sessionNumber);
            } else if (next.startsWith(OPEN_UNORDERED_BLOCK_TAG)) {
                List<String> unorderedLines = new ArrayList<>(5);
                next = nextLine();
                while (next == null || !next.startsWith(CLOSE_UNORDERED_BLOCK_TAG)) {
                    if (next == null) {
                        throw new Exception("Unterminated 'SUB {' block");
                    }
                    if (!next.startsWith(SERVER_TAG)) {
                        throw new Exception("Only 'S: ' lines are permitted inside a 'SUB {' block (line " + lineNumber + ")");
                    }
                    unorderedLines.add(validRegex(next.length() > 3 ? next.substring(3) : ""));
                    next = nextLine();
                }
                session.sub(sessionNumber, unorderedLines, location, lastClientMsg);
            } else if (next.startsWith(COMMENT_TAG) || next.trim().isEmpty()) {
                // ignore these lines.
            } else if (next.startsWith(SESSION_TAG)) {
                String number = next.substring(SESSION_TAG.length()).trim();
                if (number.isEmpty()) {
                    throw new Exception("No session number specified");
                }
                sessionNumber = Integer.parseInt(number);
                if (sessionNumber < 0) {
                    throw new Exception("Session number must be positive");
                }
            } else if (next.startsWith(TIMER)) {
                if (next.length() < TIMER_COMMAND_END + 2) {
                    throw new Exception("TIMER expects 'TIMER start|print|reset name', got: " + next);
                }
                TimerCommand timerCommand = TimerCommand.from(next.substring(TIMER_COMMAND_START, TIMER_COMMAND_END));
                String timerName = next.substring(TIMER_COMMAND_END + 1);
                session.timer(timerCommand, timerName);
            } else {
                String prefix = next;
                if (next.length() > 3) {
                    prefix = next.substring(0, 3);
                }
                throw new Exception("Invalid line prefix: " + prefix);
            }
        }

        private String validRegex(String regex) throws Exception {
            try {
                // variables are substituted at runtime: validate the pattern with placeholders
                Pattern.compile(regex.replaceAll("\\$\\{[A-Za-z][A-Za-z0-9_]*}", "x"));
                return regex;
            } catch (PatternSyntaxException e) {
                throw new Exception("Invalid regular expression '" + regex + "': " + e.getDescription());
            }
        }
    }

}
