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

package org.apache.james.protocols.api.future;

import org.apache.james.protocols.api.Response;

/**
 * An special {@link Response} which allows to populate it in an async fashion. It also allows to register listeners which will get notified once the 
 * {@link FutureResponse} is ready
 * 
 *
 */
public interface FutureResponse extends Response{

    /**
     * Add a {@link ResponseListener} which will get notified once {@link #isReady()} is true
     * 
     * @param listener
     */
    public void addListener(ResponseListener listener);
    
    /**
     * Remote a {@link ResponseListener}
     * 
     * @param listener
     */
    public void removeListener(ResponseListener listener);
    
    /**
     * Return <code>true</code> once the {@link FutureResponse} is ready and calling any of the get methods will not block any more.
     * 
     * @return ready
     */
    public boolean isReady();
    
    
    /**
     * Listener which will get notified once the {@link FutureResponse#isReady()} returns <code>true</code>
     * 
     *
     */
    public interface ResponseListener {

        /**
         * The {@link FutureResponse} is ready for processing
         * 
         * @param response
         */
        public void onResponse(FutureResponse response);
    }
}
