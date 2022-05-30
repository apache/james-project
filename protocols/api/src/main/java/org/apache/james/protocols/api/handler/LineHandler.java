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
 * Implementations of this Interface will get called after a full line (terminated with {@link ProtocolSession#getLineDelimiter()}) was received.
 * 
 * Only one {@link LineHandler} will get called per line
 */
public interface LineHandler<SessionT extends ProtocolSession> extends ProtocolHandler {
     
    /**
     * Processing the give line. The line includes the {@link ProtocolSession#getLineDelimiter()} delimiter.
     * 
     * @param session not null
     * @param buffer not null
     * @return response or null
     */
    Response onLine(SessionT session, byte[] buffer);
    
}
