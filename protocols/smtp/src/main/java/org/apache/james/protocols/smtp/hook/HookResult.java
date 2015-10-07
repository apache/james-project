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

package org.apache.james.protocols.smtp.hook;

/**
 * Result which get used for hooks
 * 
 */
public final class HookResult {

    private static final HookResult DECLINED = new HookResult(HookReturnCode.DECLINED);
    private static final HookResult OK = new HookResult(HookReturnCode.OK);
    private static final HookResult DENY = new HookResult(HookReturnCode.DENY);
    private static final HookResult DENYSOFT = new HookResult(HookReturnCode.DENYSOFT);
    private static final HookResult DISCONNECT = new HookResult(HookReturnCode.DISCONNECT);

    private int result;
    private String smtpRetCode;
    private String smtpDescription;
    
    /**
     * Construct new HookResult
     * 
     * @param result 
     * @param smtpRetCode 
     * @param smtpDescription
     */
    public HookResult(int result, String smtpRetCode, CharSequence smtpDescription) {
        boolean match = false;

        if ((result & HookReturnCode.DECLINED) == HookReturnCode.DECLINED) {
            if (match == true) throw new IllegalArgumentException();
            match = true;
        }
        if ((result & HookReturnCode.OK) == HookReturnCode.OK) {
            if (match == true) throw new IllegalArgumentException();
            match = true;
        }
        if ((result & HookReturnCode.DENY) == HookReturnCode.DENY) {
            if (match == true) throw new IllegalArgumentException();
            match = true;
        }
        if ((result & HookReturnCode.DENYSOFT) == HookReturnCode.DENYSOFT) {
            if (match == true) throw new IllegalArgumentException();
            match = true;
        }
        this.result = result;
        this.smtpRetCode = smtpRetCode;
        this.smtpDescription = (smtpDescription == null) ? null : smtpDescription.toString();
    }
    
    /**
     * Construct new HookResult
     * 
     * @param result
     * @param smtpDescription
     */
    public HookResult(int result, String smtpDescription) {
        this(result,null,smtpDescription);
    }
    
    /**
     * Construct new HookResult
     * 
     * @param result
     */
    public HookResult(int result) {
        this(result,null,null);
    }
    
   
    /**
     * Return the result
     * 
     * @return result
     */
    public int getResult() {
        return result;
    }
    
    /**
     * Return the SMTPRetCode which should used. If not set return null. 
     * 
     * @return smtpRetCode
     */
    public String getSmtpRetCode() {
        return smtpRetCode;
    }
    
    /**
     * Return the SMTPDescription which should used. If not set return null
     *  
     * @return smtpDescription
     */
    public String getSmtpDescription() {
        return smtpDescription;
    }
    
    public static HookResult declined() {
        return DECLINED;
    }
    
    public static HookResult ok() {
        return OK;
    }
    
    public static HookResult deny() {
        return DENY;
    }
    
    public static HookResult denysoft() {
        return DENYSOFT;
    }
    
    public static HookResult disconnect() {
        return DISCONNECT;
    }
}
