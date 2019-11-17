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
package org.apache.james.protocols.lib.handler;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.protocols.api.handler.ProtocolHandler;

/**
 * Implementations of this interface are responsible for loading instances
 * of {@link ProtocolHandler}. This includes to inject all needed resources and 
 * execute any lifecycle methods
 */
public interface ProtocolHandlerLoader {

    /**
     * Load the {@link ProtocolHandler} and make sure all lifecycle methods are called and all
     * needed services injected.
     */
    ProtocolHandler load(String name, Configuration config) throws LoadingException;
    
    /**
     * Exception which will get thrown if the loading of a {@link ProtocolHandler} failed 
     * 
     *
     */
    class LoadingException extends Exception {
        /**
         * 
         */
        private static final long serialVersionUID = 1710169767810301710L;

        public LoadingException(String msg, Throwable t) {
            super(msg, t);
        }
        
        public LoadingException(String msg) {
            super(msg);
        }

    }

}
