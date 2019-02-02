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

package org.apache.james.mpt.api;

/**
 * A connection to the host.
 */
public interface Session {
    
    /**
     * Reads a line from the session input,
     * blocking until a new line is available.
     * @return not null
     * @throws Exception
     */
    String readLine() throws Exception;

    /**
     * Writes a line to the session output.
     * @param line not null
     * @throws Exception
     */
    void writeLine(String line) throws Exception;

    /**
     * Opens the session.
     * 
     * @throws Exception
     */
    void start() throws Exception;

    /**
     * Reopens the session to reinitialize the server state
     * 
     * @throws Exception
     */
    void restart() throws Exception;

    /**
     * Closes the session.
     * 
     * @throws Exception
     */
    void stop() throws Exception;

    void await() throws Exception;
}