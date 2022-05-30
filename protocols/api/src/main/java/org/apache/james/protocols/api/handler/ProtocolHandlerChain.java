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

import java.util.LinkedList;
import java.util.Optional;

/**
 * Chain which can be used to get all Handlers for a given Class.
 */
public interface ProtocolHandlerChain {

    /**
     * Returns a list of handler of the requested type.
     * 
     * @param type the type of handler we're interested in
     * @return a List of handlers
     */
    <T> LinkedList<T> getHandlers(Class<T> type);

    <T> Optional<T> getFirstHandler(Class<T> type);

    /**
     * Destroy the {@link ProtocolHandlerChain}. After this call it will not be usable anymore
     */
    void destroy();

}