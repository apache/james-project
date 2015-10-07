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



package org.apache.james.protocols.api.handler;

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Response;

/**
 * Custom connect handlers must implement this interface
 * The connect handlers will be server-wide common to all the Handlers ,
 * therefore the handlers must store all the state information
 * in the Session object
 */
public interface ConnectHandler<Session extends ProtocolSession> extends ProtocolHandler{
    /**
     * Handle connection and disconnect if true is returned
     * 
     * @param session
     * @return response or null
     * 
    **/
    Response onConnect(Session session);

}
