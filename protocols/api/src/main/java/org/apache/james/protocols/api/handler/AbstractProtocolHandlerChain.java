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
import java.util.List;
import java.util.stream.Collectors;


/**
 * Abstract base class for {@link ProtocolHandlerChain} implementations
 * 
 *
 */
public abstract class AbstractProtocolHandlerChain implements ProtocolHandlerChain{

    /**
     * Return an immutable List of all Handlers
     * 
     * @return handlerList
     */
    protected abstract List<ProtocolHandler> getHandlers();

    /**
     * @see org.apache.james.protocols.api.handler.ProtocolHandlerChain#getHandlers(java.lang.Class)
     */
    @SuppressWarnings("unchecked")
    public <T> LinkedList<T> getHandlers(Class<T> type) {
        List<ProtocolHandler> handlers = getHandlers();
        return handlers.stream()
            .filter(type::isInstance)
            .map(handler -> (T) handler)
            .collect(Collectors.toCollection(LinkedList::new));
    }
    
    /**
     * ExtensibleHandler wiring. This should get called after the class was constructed
     * and every stuff was set
     * 
     * @throws WiringException 
     */
    public void wireExtensibleHandlers() throws WiringException {
        List<ProtocolHandler> handlers = getHandlers();
        for (ProtocolHandler handler : handlers) {
            if (handler instanceof ExtensibleHandler) {
                final ExtensibleHandler extensibleHandler = (ExtensibleHandler) handler;
                final List<Class<?>> markerInterfaces = extensibleHandler.getMarkerInterfaces();
                for (Class<?> markerInterface : markerInterfaces) {
                    final List<?> extensions = getHandlers(markerInterface);
                    extensibleHandler.wireExtensions(markerInterface, extensions);
                }
            }
        }
    }
    

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.handler.ProtocolHandlerChain#destroy()
     */
    public void destroy() {
        List<ProtocolHandler> handlers = getHandlers(ProtocolHandler.class);
        for (ProtocolHandler handler: handlers) {
            handler.destroy();
        }
    }
}
