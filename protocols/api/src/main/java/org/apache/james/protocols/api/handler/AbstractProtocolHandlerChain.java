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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;


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
        LinkedList<T> result = new LinkedList<T>();
        List<ProtocolHandler> handlers = getHandlers();
        for (Iterator<?> i = handlers.iterator(); i.hasNext(); ) {
            Object handler = i.next();
            if (type.isInstance(handler)) {
                result.add((T)handler);
            }
        }
        return result;
    }
    
    /**
     * ExtensibleHandler wiring. This should get called after the class was constructed
     * and every stuff was set
     * 
     * @throws WiringException 
     */
    public void wireExtensibleHandlers() throws WiringException {
        List<ProtocolHandler> handlers = getHandlers();
        for (Iterator<?> h = handlers.iterator(); h.hasNext(); ) {
            Object handler = h.next();
            if (handler instanceof ExtensibleHandler) {
                final ExtensibleHandler extensibleHandler = (ExtensibleHandler) handler;
                final List<Class<?>> markerInterfaces = extensibleHandler.getMarkerInterfaces();
                for (int i= 0;i < markerInterfaces.size(); i++) {
                    final Class<?> markerInterface = markerInterfaces.get(i);
                    final List<?> extensions = getHandlers(markerInterface);
                    extensibleHandler.wireExtensions(markerInterface,extensions);
                }
            }
        }
    }
    

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.handler.ProtocolHandlerChain#destroy()
     */
    public void destroy() {
        List<LifecycleAwareProtocolHandler> handlers = getHandlers(LifecycleAwareProtocolHandler.class);
        for (LifecycleAwareProtocolHandler handler: handlers) {
            handler.destroy();
        }
    }
}
