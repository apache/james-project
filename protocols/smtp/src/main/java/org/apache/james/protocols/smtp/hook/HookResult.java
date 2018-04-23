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

import java.util.Objects;
import java.util.Optional;

/**
 * Result which get used for hooks
 */
public final class HookResult {

    public static final HookResult DECLINED = new HookResult(HookReturnCode.declined());
    public static final HookResult OK = new HookResult(HookReturnCode.ok());
    public static final HookResult DENY = new HookResult(HookReturnCode.deny());
    public static final HookResult DENYSOFT = new HookResult(HookReturnCode.denySoft());
    public static final HookResult DISCONNECT = new HookResult(new HookReturnCode(HookReturnCode.Action.NONE, HookReturnCode.ConnectionStatus.Disconnected));

    private final HookReturnCode result;
    private final String smtpRetCode;
    private final String smtpDescription;

    public HookResult(HookReturnCode result, String smtpRetCode, CharSequence smtpDescription) {
        this.result = result;
        this.smtpRetCode = smtpRetCode;
        this.smtpDescription = Optional.ofNullable(smtpDescription)
            .map(CharSequence::toString)
            .orElse(null);
    }

    public HookResult(HookReturnCode result, String smtpDescription) {
        this(result, null, smtpDescription);
    }

    private HookResult(HookReturnCode result) {
        this(result, null, null);
    }

    public HookReturnCode getResult() {
        return result;
    }
    
    /**
     * Return the SMTPRetCode which should used. If not set return null.
     */
    public String getSmtpRetCode() {
        return smtpRetCode;
    }
    
    /**
     * Return the SMTPDescription which should used. If not set return null
     */
    public String getSmtpDescription() {
        return smtpDescription;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof HookResult) {
            HookResult that = (HookResult) o;

            return Objects.equals(this.result, that.result)
                && Objects.equals(this.smtpRetCode, that.smtpRetCode)
                && Objects.equals(this.smtpDescription, that.smtpDescription);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(result, smtpRetCode, smtpDescription);
    }
}
