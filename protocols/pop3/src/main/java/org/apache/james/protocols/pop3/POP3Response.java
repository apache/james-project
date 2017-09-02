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

import java.util.ArrayList;
import java.util.List;

import org.apache.james.protocols.api.AbstractResponse;
import org.apache.james.protocols.api.Response;

/**
 * Contains an POP3 result
 */
public class POP3Response extends AbstractResponse {

    /** OK response. Requested content will follow */
    public final static String OK_RESPONSE = "+OK";

    /**
     * Error response. Requested content will not be provided. This prefix is
     * followed by a more detailed error message.
     */
    public final static String ERR_RESPONSE = "-ERR";

    public final static String WS = " ";
    
    
    /**
     * {@link #OK_RESPONSE} with no description
     */
    public static final Response OK = new POP3Response(OK_RESPONSE).immutable();
    
    /**
     * {@link #ERR_RESPONSE} with no description
     */
    public static final Response ERR = new POP3Response(ERR_RESPONSE).immutable();

    
    /**
     * Construct a new POP3Response. The given code can not be
     * </code>null</code>, if <code>null</code> an {@link IllegalArgumentException} get thrown
     * 
     * @param code
     *            the returnCode
     * @param description
     *            the description or <code>null</code> if no description should be used
     */
    public POP3Response(String code, CharSequence description) {
        super(code, description);
    }

    /**
     * {@link #POP3Response(String, CharSequence)}
     * 
     */
    public POP3Response(String code) {
        setRetCode(code);
    }
    
    protected POP3Response() {
    }


    /**
     * Return a List of all responseLines stored in this POP3Response
     * 
     * @return all responseLines
     */
    public List<CharSequence> getLines() {
        List<CharSequence> responseList = new ArrayList<>();
        if (lines.isEmpty()) {
            responseList.add(getRetCode());
        } else {
            for (int i = 0; i < lines.size(); i++) {
                if (i == 0) {
                    responseList.add(getRetCode() + WS +lines.get(i));
                } else {
                    responseList.add(lines.get(i));
                }
            }
        }
        return responseList;
    }

}
