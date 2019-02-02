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

package org.apache.james.protocols.pop3;

import java.util.List;

import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.StartTlsResponse;

/**
 * Special sub-type of {@link POP3Response} which will trigger the start of TLS after the response was written to the client
 * 
 *
 */
public class POP3StartTlsResponse extends POP3Response implements StartTlsResponse {

    public POP3StartTlsResponse(String code, CharSequence description) {
        super(code, description);
    }

    public POP3StartTlsResponse(String code) {
        super(code);
    }

    /**
     * Return an immutable {@link StartTlsResponse}.
     */
    @Override
    public Response immutable() {
        // We need to override this and return a StartTlsResponse. See ROTOCOLS-89
        return new StartTlsResponse() {
            
            @Override
            public boolean isEndSession() {
                return POP3StartTlsResponse.this.isEndSession();
            }
            
            @Override
            public String getRetCode() {
                return POP3StartTlsResponse.this.getRetCode();
            }
            
            @Override
            public List<CharSequence> getLines() {
                return POP3StartTlsResponse.this.getLines();
            }
        };
    }

}
