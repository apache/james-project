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
package org.apache.james.jmap.model;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.methods.JmapRequest;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = GetMailboxesRequest.Builder.class)
public class GetMailboxesRequest implements JmapRequest {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private String accountId;
        private String[] ids;
        private String[] properties;

        private Builder() {
        }

        public Builder accountId(String accountId) {
            if (accountId != null) {
                throw new NotImplementedException();
            }
            return this;
        }

        public Builder ids(String[] ids) {
            if (ids != null) {
                throw new NotImplementedException();
            }
            return this;
        }

        public Builder properties(String[] properties) {
            if (properties != null) {
                throw new NotImplementedException();
            }
            return this;
        }

        public GetMailboxesRequest build() {
            return new GetMailboxesRequest(accountId, ids, properties);
        }
    }

    private final String accountId;
    private final String[] ids;
    private final String[] properties;

    private GetMailboxesRequest(String accountId, String[] ids, String[] properties) {
        this.accountId = accountId;
        this.ids = ids;
        this.properties = properties;
    }

    public String getAccountId() {
        return accountId;
    }

    public String[] getIds() {
        return ids;
    }

    public String[] getProperties() {
        return properties;
    }
}
