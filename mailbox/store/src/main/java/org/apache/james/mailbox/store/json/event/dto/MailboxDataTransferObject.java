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

package org.apache.james.mailbox.store.json.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MailboxDataTransferObject {

    public static class Builder {
        private String serializedMailboxId;
        private String namespace;
        private String user;
        private String name;
        private long uidValidity;
        private String serializedACL;

        private Builder() {

        }

        public Builder serializedMailboxId(String serializedMailboxId) {
            this.serializedMailboxId = serializedMailboxId;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder uidValidity(long uidValidity) {
            this.uidValidity = uidValidity;
            return this;
        }

        public Builder serializedACL(String serializedACL) {
            this.serializedACL = serializedACL;
            return this;
        }

        public MailboxDataTransferObject build() {
            return new MailboxDataTransferObject(serializedMailboxId,
                namespace,
                user,
                name,
                uidValidity,
                serializedACL);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty()
    private String serializedMailboxId;
    @JsonProperty()
    private String namespace;
    @JsonProperty()
    private String user;
    @JsonProperty()
    private String name;
    @JsonProperty()
    private long uidValidity;
    @JsonProperty()
    private String serializedACL;

    public MailboxDataTransferObject() {

    }

    private MailboxDataTransferObject(String serializedMailboxId,
                                      String namespace,
                                      String user,
                                      String name,
                                      long uidValidity,
                                      String serializedACL) {
        this.serializedMailboxId = serializedMailboxId;
        this.namespace = namespace;
        this.user = user;
        this.name = name;
        this.uidValidity = uidValidity;
        this.serializedACL = serializedACL;
    }

    @JsonIgnore
    public String getSerializedMailboxId() {
        return serializedMailboxId;
    }

    @JsonIgnore
    public String getNamespace() {
        return namespace;
    }

    @JsonIgnore
    public String getUser() {
        return user;
    }

    @JsonIgnore
    public String getName() {
        return name;
    }

    @JsonIgnore
    public long getUidValidity() {
        return uidValidity;
    }

    @JsonIgnore
    public String getSerializedACL() {
        return serializedACL;
    }
}
