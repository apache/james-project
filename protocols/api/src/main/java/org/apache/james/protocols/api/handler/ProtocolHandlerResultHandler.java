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
 * An special {@link ProtocolHandler} which allows to <strong>intercept</code> returned {@link Response}'s and act on them.
 * 
 * This could be to gather statistics or even replace them.
 *
 * @param <R>
 * @param <S>
 */
public interface ProtocolHandlerResultHandler<R extends Response, S extends ProtocolSession> extends ProtocolHandler {

    /**
     * Get called when a {@link Response} was returned from the {@link ProtocolHandler}
     * 
     * @param session
     * @param response
     * @param handler
     * @return response
     */
    Response onResponse(ProtocolSession session, R response, long executionTime, ProtocolHandler handler);
}
