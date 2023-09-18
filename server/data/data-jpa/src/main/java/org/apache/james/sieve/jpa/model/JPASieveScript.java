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

package org.apache.james.sieve.jpa.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.ScriptSummary;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

@Entity(name = "JamesSieveScript")
@Table(name = "JAMES_SIEVE_SCRIPT")
@NamedQueries({
        @NamedQuery(name = "findAllByUsername", query = "SELECT sieveScript FROM JamesSieveScript sieveScript WHERE sieveScript.username=:username"),
        @NamedQuery(name = "findActiveByUsername", query = "SELECT sieveScript FROM JamesSieveScript sieveScript WHERE sieveScript.username=:username AND sieveScript.isActive=true"),
        @NamedQuery(name = "findSieveScript", query = "SELECT sieveScript FROM JamesSieveScript sieveScript WHERE sieveScript.username=:username AND sieveScript.scriptName=:scriptName")
})
public class JPASieveScript {

    public static Builder builder() {
        return new Builder();
    }

    public static ScriptSummary toSummary(JPASieveScript script) {
        return new ScriptSummary(new ScriptName(script.getScriptName()), script.isActive(), script.getScriptSize());
    }

    public static class Builder {

        private String username;
        private String scriptName;
        private String scriptContent;
        private long scriptSize;
        private boolean isActive;
        private OffsetDateTime activationDateTime;

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

        public Builder scriptContent(ScriptContent scriptContent) {
            Preconditions.checkNotNull(scriptContent);
            this.scriptContent = scriptContent.getValue();
            this.scriptSize = scriptContent.length();
            return this;
        }

        public Builder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public JPASieveScript build() {
            Preconditions.checkState(StringUtils.isNotBlank(username), "'username' is mandatory");
            Preconditions.checkState(StringUtils.isNotBlank(scriptName), "'scriptName' is mandatory");
            this.activationDateTime = isActive ? OffsetDateTime.now() : null;
            return new JPASieveScript(username, scriptName, scriptContent, scriptSize, isActive, activationDateTime);
        }
    }

    @Id
    private String uuid = UUID.randomUUID().toString();

    @Column(name = "USER_NAME", nullable = false, length = 100)
    private String username;

    @Column(name = "SCRIPT_NAME", nullable = false, length = 255)
    private String scriptName;

    @Column(name = "SCRIPT_CONTENT", nullable = false, length = 1024)
    private String scriptContent;

    @Column(name = "SCRIPT_SIZE", nullable = false)
    private long scriptSize;

    @Column(name = "IS_ACTIVE", nullable = false)
    private boolean isActive;

    @Column(name = "ACTIVATION_DATE_TIME")
    private OffsetDateTime activationDateTime;

    /**
     * @deprecated enhancement only
     */
    @Deprecated
    protected JPASieveScript() {
    }

    private JPASieveScript(String username, String scriptName, String scriptContent, long scriptSize, boolean isActive, OffsetDateTime activationDateTime) {
        this.username = username;
        this.scriptName = scriptName;
        this.scriptContent = scriptContent;
        this.scriptSize = scriptSize;
        this.isActive = isActive;
        this.activationDateTime = activationDateTime;
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

    public void activate() {
        this.isActive = true;
        this.activationDateTime = OffsetDateTime.now();
    }

    public void deactivate() {
        this.isActive = false;
        this.activationDateTime = null;
    }

    public void renameTo(ScriptName newName) {
        this.scriptName = newName.getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JPASieveScript that = (JPASieveScript) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("uuid", uuid)
                .add("username", username)
                .add("scriptName", scriptName)
                .add("isActive", isActive)
                .toString();
    }
}
