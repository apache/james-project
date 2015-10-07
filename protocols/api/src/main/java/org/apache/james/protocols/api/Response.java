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

package org.apache.james.protocols.api;

import java.util.Collections;
import java.util.List;

/**
 * Protocol response to send to the client
 * 
 *
 */
public interface Response {
    
    /**
     * Special {@link Response} implementation which will just disconnect the client
     */
    public static final Response DISCONNECT = new Response() {

        public String getRetCode() {
            return "";
        }

        @SuppressWarnings("unchecked")
        public List<CharSequence> getLines() {
            return Collections.EMPTY_LIST;
        }

        public boolean isEndSession() {
            return true;
        }
        
    };
    
    
    /**
     * Return return-code
     * @return the return code
     */
    String getRetCode();

   
    /**
     * Return a List of all response lines stored in this Response. This should be used for encoding
     * the {@link Response} before sending it to the client.
     * 
     * @return all responseLines
     */
    List<CharSequence> getLines();


    /**
     * Return true if the session is ended
     * 
     * @return true if session is ended
     */
    boolean isEndSession();

}
