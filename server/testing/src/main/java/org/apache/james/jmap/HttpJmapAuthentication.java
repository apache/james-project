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

package org.apache.james.jmap;

import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.james.core.Username;
import org.hamcrest.core.IsAnything;

import com.jayway.jsonpath.JsonPath;

public class HttpJmapAuthentication {

    public static AccessToken authenticateJamesUser(URIBuilder uriBuilder, Username username, String password) {
        return calmlyAwait
            .atMost(Duration.ofMinutes(2))
            .until(
            () -> doAuthenticate(uriBuilder, username, password), IsAnything.anything());
    }

    public static AccessToken doAuthenticate(URIBuilder uriBuilder, Username username, String password) throws ClientProtocolException, IOException, URISyntaxException {
        String continuationToken = getContinuationToken(uriBuilder, username);

        Response response = postAuthenticate(uriBuilder, password, continuationToken);

        return AccessToken.of(
            JsonPath.parse(response.returnContent().asString())
                .read("accessToken"));
    }

    private static Response postAuthenticate(URIBuilder uriBuilder, String password, String continuationToken) throws ClientProtocolException, IOException, URISyntaxException {
        return Request.Post(uriBuilder.setPath("/authentication").build())
                .bodyString("{\"token\": \"" + continuationToken + "\", \"method\": \"password\", \"password\": \"" + password + "\"}", 
                        ContentType.APPLICATION_JSON)
                .setHeader("Accept", ContentType.APPLICATION_JSON.getMimeType())
                .execute();
    }

    private static String getContinuationToken(URIBuilder uriBuilder, Username username) throws ClientProtocolException, IOException, URISyntaxException {
        Response response = Request.Post(uriBuilder.setPath("/authentication").build())
            .bodyString("{\"username\": \"" + username.asString() + "\", \"clientName\": \"Mozilla Thunderbird\", \"clientVersion\": \"42.0\", \"deviceName\": \"Joe Blogg’s iPhone\"}",
                ContentType.APPLICATION_JSON)
            .setHeader("Accept", ContentType.APPLICATION_JSON.getMimeType())
            .execute();

        return JsonPath.parse(response.returnContent().asString())
            .read("continuationToken");
    }

}
