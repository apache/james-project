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

package org.apache.james.jmap.draft.methods.integration.cucumber;

import static org.apache.james.jmap.JmapURIBuilder.baseUri;

import jakarta.inject.Inject;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

public class HttpClient {
    private final MainStepdefs mainStepdefs;
    private final UserStepdefs userStepdefs;

    public HttpResponse response;
    public DocumentContext jsonPath;

    @Inject
    public HttpClient(MainStepdefs mainStepdefs, UserStepdefs userStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.userStepdefs = userStepdefs;
    }

    public void post(String requestBody) throws Exception {
        response = Request.Post(baseUri(mainStepdefs.jmapServer).setPath("/jmap").build())
            .addHeader("Authorization", userStepdefs.authenticate(userStepdefs.getConnectedUser()).asString())
            .addHeader("Accept", org.apache.http.entity.ContentType.APPLICATION_JSON.getMimeType())
            .bodyString(requestBody, org.apache.http.entity.ContentType.APPLICATION_JSON)
            .execute()
            .returnResponse();
        jsonPath = JsonPath.using(Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS))
            .parse(response.getEntity().getContent());
    }
}
