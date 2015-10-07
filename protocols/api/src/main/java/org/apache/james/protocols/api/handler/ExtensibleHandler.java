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

import java.util.List;




/**
 * Handlers implement this interface to be notified of available
 * extensions of the given type.
 */
public interface ExtensibleHandler {
     
    /**
     * Return a List of interfaces of plugins that will
     * extend this.
     */
    List<Class<?>> getMarkerInterfaces();
    
    /**
     * Method called during initialization after all the handlers have been declared
     * in the ProtocolHandlerChain.
     * 
     * @param interfaceName
     * @param extension a list of objects implementing the marker interface
     */
    void wireExtensions(Class<?> interfaceName, List<?> extension) throws WiringException;
    
}
