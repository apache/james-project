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

package org.apache.james.protocols.smtp;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.protocols.api.AbstractResponse;

/**
 * Contains an SMTP result
 */
public class SMTPResponse extends AbstractResponse {

    protected SMTPResponse() {
        
    }
    /**
     * Construct a new SMTPResponse. The given code and description can not be null, if null an IllegalArgumentException
     * get thrown
     * 
     * @param code the returnCode
     * @param description the description 
     */
    public SMTPResponse(String code, CharSequence description) {
        super(code, description);
    }
    
    /**
     * Construct a new SMTPResponse. The given rawLine need to be in format [SMTPResponseReturnCode SMTResponseDescription].
     * If this is not the case an IllegalArgumentException get thrown.
     * 
     * @param rawLine the raw SMTPResponse
     */
    /**
     * Construct a new SMTPResponse. The given rawLine need to be in format [SMTPResponseReturnCode SMTResponseDescription].
     * If this is not the case an IllegalArgumentException get thrown.
     * 
     * @param rawLine the raw SMTPResponse
     */
    public SMTPResponse(String rawLine) {
        this(extractCode(rawLine), extractResponse(rawLine));
    }
    

    private  static String extractCode(String raw) {
        String args[] = raw.split(" ");
        if (args != null && args.length > 1) {
            return args[0];
            
        } else {
            throw new IllegalArgumentException("Invalid Response format. Format should be [Code Description]");
        }
    }
    
    private  static String extractResponse(String raw) {
        String args[] = raw.split(" ");
        if (args != null && args.length > 1) {
            return args[2];
            
        } else {
            return null;
        }
    }

    /**
     * @see org.apache.james.protocols.api.Response#getLines()
     */
    public List<CharSequence> getLines() {
        List<CharSequence> responseList = new ArrayList<>();

        for (int k = 0; k < lines.size(); k++) {
            StringBuilder respBuff = new StringBuilder(256);
            respBuff.append(getRetCode());
            if (k == lines.size() - 1) {
                respBuff.append(" ");
                respBuff.append(lines.get(k));

            } else {
                respBuff.append("-");
                respBuff.append(lines.get(k));

            }
            responseList.add(respBuff.toString());
        }

        return responseList;
    }


}
