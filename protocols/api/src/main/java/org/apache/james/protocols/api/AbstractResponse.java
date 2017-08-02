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

import java.util.LinkedList;
import java.util.List;

/**
 * Abstract base implementation of {@link Response}
 * 
 *
 */
public abstract class AbstractResponse implements Response{


    private String retCode = null;
    protected final List<CharSequence> lines = new LinkedList<>();
    private boolean endSession = false;
    
    protected AbstractResponse() {
        
    }
    
    /**
     * Construct a new SMTPResponse. The given code and description can not be null, if null an IllegalArgumentException
     * get thrown
     * 
     * @param code the returnCode
     * @param description the description 
     */
    public AbstractResponse(String code, CharSequence description) {
        if (code == null) throw new IllegalArgumentException("code can not be null");    
        this.setRetCode(code);
        this.appendLine(description);
    }
    

    
    /**
     * Append the responseLine to the SMTPResponse
     * 
     * @param line the responseLine to append
     */
    public void appendLine(CharSequence line) {
        lines.add(line);
    }
    
    /**
     * Return the SMTPCode 
     * 
     * @return the SMTPCode
     */
    public String getRetCode() {
        return retCode;
    }

    /**
     * Set the SMTPCode
     *  
     * @param retCode the SMTPCode
     */
    public void setRetCode(String retCode) {
        this.retCode = retCode.trim();
    }

   
    /**
     * Return true if the session is ended
     * 
     * @return true if session is ended
     */
    public boolean isEndSession() {
        return endSession;
    }

    /**
     * Set to true to end the session
     * 
     * @param endSession
     */
    public void setEndSession(boolean endSession) {
        this.endSession = endSession;
    }

    /**
     * @see java.lang.Object#toString()
     */
    public final String toString() {
        return getLines().toString();
    }
    
    /**
     * Return a immutable instance of this {@link AbstractResponse}
     * 
     * @return immutable
     */
    public Response immutable() {
        return new Response() {
            
            public boolean isEndSession() {
                return AbstractResponse.this.isEndSession();
            }
            
            public String getRetCode() {
                return AbstractResponse.this.getRetCode();
            }
            
            public List<CharSequence> getLines() {
                return AbstractResponse.this.getLines();
            }
        };
    }

}
