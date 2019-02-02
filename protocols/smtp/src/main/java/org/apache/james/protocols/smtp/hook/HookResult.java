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

import com.google.common.base.Preconditions;

/**
 * Result which get used for hooks
 */
public final class HookResult {

    public static class Builder {
        private HookReturnCode result;
        private Optional<String> smtpReturnCode;
        private Optional<String> smtpDescription;

        public Builder() {
            smtpDescription = Optional.empty();
            smtpReturnCode = Optional.empty();
        }

        public Builder hookReturnCode(HookReturnCode hookReturnCode) {
            this.result = hookReturnCode;
            return this;
        }

        public Builder smtpReturnCode(String smtpReturnCode) {
            this.smtpReturnCode = Optional.of(smtpReturnCode);
            return this;
        }

        public Builder smtpDescription(String smtpDescription) {
            this.smtpDescription = Optional.of(smtpDescription);
            return this;
        }

        public HookResult build() {
            Preconditions.checkNotNull(result);

            return new HookResult(result,
                smtpReturnCode.orElse(null),
                smtpDescription.orElse(null));
        }
    }

    public static final HookResult DECLINED = builder()
        .hookReturnCode(HookReturnCode.declined())
        .build();
    public static final HookResult OK =  builder()
        .hookReturnCode(HookReturnCode.ok())
        .build();
    public static final HookResult DENY =  builder()
        .hookReturnCode(HookReturnCode.deny())
        .build();
    public static final HookResult DENYSOFT =  builder()
        .hookReturnCode(HookReturnCode.denySoft())
        .build();
    public static final HookResult DISCONNECT =  builder()
        .hookReturnCode(new HookReturnCode(HookReturnCode.Action.NONE, HookReturnCode.ConnectionStatus.Disconnected))
        .build();

    public static Builder builder() {
        return new Builder();
    }
    
    private final HookReturnCode result;
    private final String smtpRetCode;
    private final String smtpDescription;

    private HookResult(HookReturnCode result, String smtpRetCode, CharSequence smtpDescription) {
        this.result = result;
        this.smtpRetCode = smtpRetCode;
        this.smtpDescription = Optional.ofNullable(smtpDescription)
            .map(CharSequence::toString)
            .orElse(null);
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
