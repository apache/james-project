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
import org.apache.james.protocols.api.StartTlsResponse;

/**
 * Special {@link FutureResponse} which will get notified once a {@link StartTlsResponse} is ready
 * 
 *
 */
public class FutureStartTlsResponse extends FutureResponseImpl implements StartTlsResponse{


    /**
     * Set the {@link StartTlsResponse} to wrap. If a non {@link StartTlsResponse} is set this implementation will throw an {@link IllegalArgumentException}
     * 
     */
    @Override
    public void setResponse(Response response) {
        if (response instanceof StartTlsResponse) {
            super.setResponse(response);
        } else {
            throw new IllegalArgumentException("Response MUST be of type " + StartTlsResponse.class.getName());
        }
    }

}
