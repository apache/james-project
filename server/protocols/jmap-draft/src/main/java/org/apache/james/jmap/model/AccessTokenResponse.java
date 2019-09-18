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

import org.apache.james.jmap.api.access.AccessToken;

public class AccessTokenResponse {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AccessToken accessToken;
        private String api;
        private String eventSource;
        private String upload;
        private String download;

        private Builder() {

        }

        public Builder accessToken(AccessToken accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder api(String api) {
            this.api = api;
            return this;
        }

        public Builder eventSource(String eventSource) {
            this.eventSource = eventSource;
            return this;
        }

        public Builder upload(String upload) {
            this.upload = upload;
            return this;
        }

        public Builder download(String download) {
            this.download = download;
            return this;
        }

        public AccessTokenResponse build() {
            return new AccessTokenResponse(accessToken, api, eventSource, upload, download);
        }
    }

    private final AccessToken accessToken;
    private final String api;
    private final String eventSource;
    private final String upload;
    private final String download;

    private AccessTokenResponse(AccessToken accessToken, String api, String eventSource, String upload, String download) {
        this.accessToken = accessToken;
        this.api = api;
        this.eventSource = eventSource;
        this.upload = upload;
        this.download = download;
    }

    public String getAccessToken() {
        return accessToken.serialize();
    }

    public String getApi() {
        return api;
    }

    public String getEventSource() {
        return eventSource;
    }

    public String getUpload() {
        return upload;
    }

    public String getDownload() {
        return download;
    }

}