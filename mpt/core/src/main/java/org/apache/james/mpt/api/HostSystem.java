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

import org.apache.james.mpt.host.ExternalHostSystem;

/**
 * <p>Host system under test.</p>
 * <p>
 * This interface encapsulates the interaction between the server
 * under test and the test framework. MPT can be used to test components
 * without the need to serve the protocol though a socket by creating a 
 * suitable implementation of this interface.
 * </p>
 * @see ExternalHostSystem  ExternalHostSystem (a <code>HostSystem</code> for servers
 * running independently)
 * @see Session Session (supports multiple connection to the host system)
 */
public interface HostSystem extends SessionFactory {

    /**
     * Add a user for testing.
     * 
     * @param user
     *            user name
     * @param password
     *            user password
     * @throws Exception
     */
    boolean addUser(String user, String password) throws Exception;

    /**
     * Creates a new session for functional testing.
     * 
     * @return <code>Session</code>, not null
     * @throws Exception
     */
    @Override
    Session newSession(Continuation continuation) throws Exception;

    void beforeTest() throws Exception;

    void afterTest() throws Exception;

}
