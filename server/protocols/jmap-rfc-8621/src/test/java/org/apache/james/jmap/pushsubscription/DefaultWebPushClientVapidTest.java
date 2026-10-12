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

package org.apache.james.jmap.pushsubscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

import org.apache.james.jmap.api.model.PushSubscriptionServerURL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;

import reactor.core.publisher.Mono;

class DefaultWebPushClientVapidTest {
    @RegisterExtension
    PushServerExtension pushServerExtension = new PushServerExtension();

    private HttpRequest push(DefaultWebPushClient client, ClientAndServer pushServer) {
        PushSubscriptionServerURL url = PushSubscriptionServerURL.from(pushServerExtension.getBaseUrl() + "/push").get();
        Mono.from(client.push(url, WebPushClientTestFixture.PUSH_REQUEST_SAMPLE())).block();
        return pushServer.retrieveRecordedRequests(request().withPath("/push"))[0];
    }

    @Test
    void pushShouldBeSignedWhenVapidIsConfigured(ClientAndServer pushServer) {
        String authorization = push(VapidTestFixture.SIGNING_PUSH_CLIENT(), pushServer).getFirstHeader("Authorization");

        assertThat(authorization).endsWith(", k=" + VapidTestFixture.PUBLIC_KEY());
        assertThat(VapidTestFixture.payload(authorization, VapidTestFixture.NOW())).contains("\"aud\":\"http://127.0.0.1:" + pushServer.getLocalPort() + "\"");
        assertThat(VapidTestFixture.subject(authorization)).isEqualTo(VapidTestFixture.SUBJECT());
    }

    @Test
    void pushShouldKeepItsOtherHeadersWhenSigned(ClientAndServer pushServer) {
        HttpRequest pushed = push(VapidTestFixture.SIGNING_PUSH_CLIENT(), pushServer);

        assertThat(pushed.getFirstHeader("TTL")).isEqualTo("15");
        assertThat(pushed.getFirstHeader("Topic")).isEqualTo("topicabc");
        assertThat(pushed.getFirstHeader("Urgency")).isEqualTo("high");
    }

    @Test
    void pushShouldNotBeSignedWithoutVapid(ClientAndServer pushServer) {
        assertThat(push(VapidTestFixture.UNSIGNED_PUSH_CLIENT(), pushServer).containsHeader("Authorization")).isFalse();
    }
}
