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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.james.mpt.protocol.ProtocolSession.TimerCommand;

/**
 * A builder which generates a ProtocolSession from a test file.
 * 
 * @author Darrell DeBoer <darrell@apache.org>
 * 
 * @version $Revision$
 */
public class FileProtocolSessionBuilder extends ProtocolSessionBuilder {

    public static final String DEBUG = "DEBUG";
    public static final String INFO = "INFO";
    public static final String WARN = "WARN";
    public static final String ERR = "ERR";

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
        InputStream is = this.getClass().getResourceAsStream(fileName);
        if (is == null) {
            throw new Exception("Test Resource '" + fileName + "' not found.");
        }

        try {
            addProtocolLinesFromStream(is, session, fileName);
        }
        finally {
            IOUtils.closeQuietly(is);
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
        int sessionNumber = -1;
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        try {
            String next;
            int lineNumber = -1;
            String lastClientMsg = "";
            while ((next = reader.readLine()) != null) {
                String location = fileName + ":" + lineNumber;
                if (SERVER_CONTINUATION_TAG.equals(next)) {
                    session.CONT(sessionNumber);
                }
                else if (next.startsWith(CLIENT_TAG)) {
                    String clientMsg = "";
                    if (next.length() > 3) {
                        clientMsg = next.substring(3);
                    }
                    session.CL(sessionNumber, clientMsg);
                    lastClientMsg = clientMsg;
                }
                else if (next.startsWith(SERVER_TAG)) {
                    String serverMsg = "";
                    if (next.length() > 3) {
                        serverMsg = next.substring(3);
                    }
                    session.SL(sessionNumber, serverMsg, location, lastClientMsg);
                }
                else if (next.startsWith(WAIT)) {
                    if (next.length() > 5) {
                        session.WAIT(sessionNumber, Long.valueOf(next.substring(5)));
                    } else {
                        throw new Exception("Invalid line length on WAIT instruction : " + next);
                    }
                }
                else if (next.startsWith(LOG)) {
                    String logInstruction = next.substring(4);
                    if (logInstruction.startsWith(DEBUG)) {
                        session.LOG(sessionNumber, ProtocolSession.LolLevel.Debug, logInstruction.substring(6));
                    } else if (logInstruction.startsWith(INFO)) {
                        session.LOG(sessionNumber, ProtocolSession.LolLevel.Info, logInstruction.substring(5));
                    } else if (logInstruction.startsWith(WARN)) {
                        session.LOG(sessionNumber, ProtocolSession.LolLevel.Warn, logInstruction.substring(5));
                    } else if (logInstruction.startsWith(ERR)) {
                        session.LOG(sessionNumber, ProtocolSession.LolLevel.Err, logInstruction.substring(4));
                    } else {
                        throw new Exception("Unrecognized log level for " + next);
                    }
                }
                else if (next.startsWith(REINIT)) {
                    session.REINIT(sessionNumber);
                }
                else if (next.startsWith(OPEN_UNORDERED_BLOCK_TAG)) {
                    List<String> unorderedLines = new ArrayList<>(5);
                    next = reader.readLine();

                    if (next == null)
                        throw new Exception("Readline doesn't contain any data, but must not be 'null' (linenumber="
                                + lineNumber);

                    while (!next.startsWith(CLOSE_UNORDERED_BLOCK_TAG)) {
                        if (!next.startsWith(SERVER_TAG)) {
                            throw new Exception("Only 'S: ' lines are permitted inside a 'SUB {' block.");
                        }
                        String serverMsg = next.substring(3);
                        unorderedLines.add(serverMsg);
                        next = reader.readLine();
                        lineNumber++;

                        if (next == null)
                            throw new Exception(
                                    "Readline doesn't contain any data, but must not be 'null' (linenumber="
                                            + lineNumber);

                    }

                    session.SUB(sessionNumber, unorderedLines, location, lastClientMsg);
                }
                else if (next.startsWith(COMMENT_TAG) || next.trim().length() == 0) {
                    // ignore these lines.
                }
                else if (next.startsWith(SESSION_TAG)) {
                    String number = next.substring(SESSION_TAG.length()).trim();
                    if (number.length() == 0) {
                        throw new Exception("No session number specified");
                    }
                    sessionNumber = Integer.parseInt(number);
                }
                else if (next.startsWith(TIMER)) {
                    TimerCommand timerCommand = TimerCommand.from(next.substring(TIMER_COMMAND_START, TIMER_COMMAND_END));
                    String timerName = next.substring(TIMER_COMMAND_END + 1);
                    session.TIMER(timerCommand, timerName);
                }
                else {
                    String prefix = next;
                    if (next.length() > 3) {
                        prefix = next.substring(0, 3);
                    }
                    throw new Exception("Invalid line prefix: " + prefix);
                }
                lineNumber++;
            }
        }
        finally {
            IOUtils.closeQuietly(reader);
        }
    }

}
