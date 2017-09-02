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
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.james.mpt.api.ProtocolInteractor;


/**
 * A builder which generates scripts from textual input.
 * 
 * @author Darrell DeBoer <darrell@apache.org>
 * 
 * @version $Revision$
 */
public class ProtocolSessionBuilder {

    public static final String LOG = "LOG";

    public static final String WAIT = "WAIT";

    public static final String SERVER_CONTINUATION_TAG = "S: \\+";

    public static final String CLIENT_TAG = "C:";

    public static final String SERVER_TAG = "S:";

    public static final String OPEN_UNORDERED_BLOCK_TAG = "SUB {";

    public static final String CLOSE_UNORDERED_BLOCK_TAG = "}";

    public static final String COMMENT_TAG = "#";

    public static final String SESSION_TAG = "SESSION:";

    public static final String REINIT = "REINIT";

    public static final String TIMER = "TIMER";

    private final Properties variables;
    
    public ProtocolSessionBuilder() {
        variables = new Properties();
    }
    
    /**
     * Sets a substitution varaible.
     * The value of a variable will be substituted whereever
     * ${<code>NAME</code>} is found in the input
     * where <code>NAME</code> is the name of the variable.
     * @param name not null
     * @param value not null
     */
    public void setVariable(String name, String value) {
        variables.put(name, value);
    }
    
    /**
     * Builds a ProtocolSession by reading lines from the test file with the
     * supplied name.
     * 
     * @param fileName
     *            The name of the protocol session file.
     * @return The ProtocolSession
     */
    public ProtocolInteractor buildProtocolSession(String fileName)
            throws Exception {
        ProtocolInteractor session = new ProtocolSession();
        addTestFile(fileName, session);
        return session;
    }
    
    /**
     * Builds a ProtocolSession by reading lines from the reader.
     * 
     * @param scriptName not null
     * @param reader not null
     * @return The ProtocolSession
     */
    public ProtocolInteractor buildProtocolSession(String scriptName, Reader reader)
            throws Exception {
        ProtocolInteractor session = new ProtocolSession();
        addProtocolLines(scriptName, reader, session);
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
    public void addTestFile(String fileName, ProtocolInteractor session)
            throws Exception {
        // Need to find local resource.
        InputStream is = this.getClass().getResourceAsStream(fileName);
        if (is == null) {
            throw new Exception("Test Resource '" + fileName + "' not found.");
        }

        addProtocolLines(fileName, is, session);
    }

    /**
     * Reads ProtocolElements from the supplied InputStream and adds them to the
     * ProtocolSession.
     * @param scriptName
     *            The name of the source file, for error messages.
     * @param is
     *            The input stream containing the protocol definition.
     * @param session
     *            The ProtocolSession to add elements to.
     */
    public void addProtocolLines(String scriptName, InputStream is, ProtocolInteractor session) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        
        doAddProtocolLines(session, scriptName, reader);
    }

    /**
     * Reads ProtocolElements from the supplied Reader and adds them to the
     * ProtocolSession.
     * @param scriptName
     *            The name of the source file, for error messages.
     * @param reader
     *            the reader containing the protocol definition.
     * @param session
     *            The ProtocolSession to add elements to.
     */
    public void addProtocolLines(String scriptName, Reader reader, ProtocolInteractor session) throws Exception {
        final BufferedReader bufferedReader;
        if (reader instanceof BufferedReader) {
            bufferedReader = (BufferedReader) reader;
        } else {
            bufferedReader = new BufferedReader(reader);
        }
        doAddProtocolLines(session, scriptName, bufferedReader);
    }
    
    /**
     * Reads ProtocolElements from the supplied Reader and adds them to the
     * ProtocolSession.
     * 
     * @param reader
     *            the reader containing the protocol definition.
     * @param session
     *            The ProtocolSession to add elements to.
     * @param scriptName
     *            The name of the source file, for error messages.
     */
    private void doAddProtocolLines(ProtocolInteractor session, String scriptName, BufferedReader reader) throws Exception {
        String line;
        int sessionNumber = -1;
        int lineNumber = -1;
        String lastClientMsg = "";
        while ((line = reader.readLine()) != null) {
            line = substituteVariables(line);
            String location = scriptName + ":" + lineNumber;
            if (SERVER_CONTINUATION_TAG.equals(line)) {
                session.CONT(sessionNumber);
            } else if (line.startsWith(CLIENT_TAG)) {
                String clientMsg = "";
                if (line.length() > 3) {
                    clientMsg = line.substring(3);
                }
                session.CL(sessionNumber, clientMsg);
                lastClientMsg = clientMsg;
            } else if (line.startsWith(SERVER_TAG)) {
                String serverMsg = "";
                if (line.length() > 3) {
                    serverMsg = line.substring(3);
                }
                session.SL(sessionNumber, serverMsg, location, lastClientMsg);
            } else if (line.startsWith(OPEN_UNORDERED_BLOCK_TAG)) {
                List<String> unorderedLines = new ArrayList<>(5);
                line = reader.readLine();

                while (!line.startsWith(CLOSE_UNORDERED_BLOCK_TAG)) {
                    if (!line.startsWith(SERVER_TAG)) {
                        throw new Exception(
                                "Only 'S: ' lines are permitted inside a 'SUB {' block.");
                    }
                    String serverMsg = line.substring(3);
                    unorderedLines.add(serverMsg);
                    line = reader.readLine();
                    lineNumber++;
                }

                session.SUB(sessionNumber, unorderedLines, location,
                        lastClientMsg);
            } else if (line.startsWith(COMMENT_TAG)
                    || line.trim().length() == 0) {
                // ignore these lines.
            } else if (line.startsWith(SESSION_TAG)) {
                String number = line.substring(SESSION_TAG.length()).trim();
                if (number.length() == 0) {
                    throw new Exception("No session number specified");
                }
                sessionNumber = Integer.parseInt(number);
            } else {
                String prefix = line;
                if (line.length() > 3) {
                    prefix = line.substring(0, 3);
                }
                throw new Exception("Invalid line prefix: " + prefix);
            }
            lineNumber++;
        }
    }

    /**
     * Replaces ${<code>NAME</code>} with variable value.
     * @param line not null
     * @return not null
     */
    private String substituteVariables(String line) {
        if (variables.size() > 0) {
            final StringBuffer buffer = new StringBuffer(line);
            int start = 0;
            int end = 0;
            while (start >= 0 && end >= 0) { 
                start = buffer.indexOf("${", end);
                if (start < 0) {
                    break;
                }
                end = buffer.indexOf("}", start);
                if (end < 0) {
                    break;
                }
                final String name = buffer.substring(start+2, end);
                final String value = variables.getProperty(name);
                if (value != null) {
                    buffer.replace(start, end + 1, value);
                    final int variableLength = (end - start + 2);
                    end = end + (value.length() - variableLength);
                }
            }
            line = buffer.toString();
        }
        return line;
    }

}
