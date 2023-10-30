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

package org.apache.james.sieve.postgres.model;

import java.time.OffsetDateTime;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.ScriptSummary;

import com.google.common.base.Preconditions;

public class PostgresSieveScript {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String username;
        private String scriptName;
        private String scriptContent;
        private long scriptSize;
        private boolean isActive;
        private OffsetDateTime activationDateTime;
        private PostgresSieveScriptId id;

        public Builder username(String username) {
            Preconditions.checkNotNull(username);
            this.username = username;
            return this;
        }

        public Builder scriptName(String scriptName) {
            Preconditions.checkNotNull(scriptName);
            this.scriptName = scriptName;
            return this;
        }

        public Builder scriptContent(String scriptContent) {
            this.scriptContent = scriptContent;
            return this;
        }

        public Builder scriptSize(long scriptSize) {
            this.scriptSize = scriptSize;
            return this;
        }

        public Builder id(PostgresSieveScriptId id) {
            this.id = id;
            return this;
        }

        public Builder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public Builder activationDateTime(OffsetDateTime offsetDateTime) {
            this.activationDateTime = offsetDateTime;
            return this;
        }

        public PostgresSieveScript build() {
            Preconditions.checkState(StringUtils.isNotBlank(username), "'username' is mandatory");
            Preconditions.checkState(StringUtils.isNotBlank(scriptName), "'scriptName' is mandatory");
            Preconditions.checkState(id != null, "'id' is mandatory");

            return new PostgresSieveScript(id, username, scriptName, scriptContent, scriptSize, isActive, activationDateTime);
        }
    }

    private final PostgresSieveScriptId id;
    private final String username;
    private final String scriptName;
    private final String scriptContent;
    private final long scriptSize;
    private final boolean isActive;
    private final OffsetDateTime activationDateTime;

    private PostgresSieveScript(PostgresSieveScriptId id, String username, String scriptName, String scriptContent,
                                long scriptSize, boolean isActive, OffsetDateTime activationDateTime) {
        this.id = id;
        this.username = username;
        this.scriptName = scriptName;
        this.scriptContent = scriptContent;
        this.scriptSize = scriptSize;
        this.isActive = isActive;
        this.activationDateTime = activationDateTime;
    }

    public PostgresSieveScriptId getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getScriptName() {
        return scriptName;
    }

    public String getScriptContent() {
        return scriptContent;
    }

    public long getScriptSize() {
        return scriptSize;
    }

    public boolean isActive() {
        return isActive;
    }

    public OffsetDateTime getActivationDateTime() {
        return activationDateTime;
    }

    public ScriptSummary toScriptSummary() {
        return new ScriptSummary(new ScriptName(scriptName), isActive, scriptSize);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PostgresSieveScript) {
            PostgresSieveScript that = (PostgresSieveScript) o;

            return Objects.equals(this.scriptSize, that.scriptSize)
                && Objects.equals(this.isActive, that.isActive)
                && Objects.equals(this.username, that.username)
                && Objects.equals(this.scriptName, that.scriptName)
                && Objects.equals(this.scriptContent, that.scriptContent)
                && Objects.equals(this.activationDateTime, that.activationDateTime);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(username, scriptName);
    }
}
