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

package org.apache.james.crowdsec;

import static org.apache.james.crowdsec.CrowdsecUtils.isBanned;
import static org.apache.james.protocols.api.Response.DISCONNECT;
import static org.apache.james.protocols.pop3.POP3Response.OK;

import java.util.List;

import javax.inject.Inject;

import org.apache.james.crowdsec.client.CrowdsecHttpClient;
import org.apache.james.crowdsec.model.CrowdsecDecision;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.ConnectHandler;
import org.apache.james.protocols.pop3.POP3Session;

public class CrowdsecPOP3CheckHandler implements ConnectHandler<POP3Session> {
    private final CrowdsecHttpClient crowdsecHttpClient;

    @Inject
    public CrowdsecPOP3CheckHandler(CrowdsecHttpClient crowdsecHttpClient) {
        this.crowdsecHttpClient = crowdsecHttpClient;
    }

    @Override
    public Response onConnect(POP3Session session) {
        // rely on the Netty transport layer to get the real client IP, and just be application layer agnostic
        String realClientIp = session.getLocalAddress().getAddress().getHostAddress();

        return crowdsecHttpClient.getCrowdsecDecisions()
            .map(decisions -> toResponse(decisions, realClientIp))
            .block();
    }

    private Response toResponse(List<CrowdsecDecision> decisions, String ip) {
        if (decisions.stream().anyMatch(decision -> isBanned(decision, ip))) {
            return DISCONNECT;
        }
        return OK;
    }
}
