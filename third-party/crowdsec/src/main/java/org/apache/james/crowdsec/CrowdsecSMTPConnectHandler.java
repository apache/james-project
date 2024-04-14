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

import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.crowdsec.model.CrowdsecDecision;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.ConnectHandler;
import org.apache.james.protocols.smtp.SMTPSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrowdsecSMTPConnectHandler implements ConnectHandler<SMTPSession> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrowdsecSMTPConnectHandler.class);

    public static final Response NOOP = new Response() {

        @Override
        public String getRetCode() {
            return "";
        }

        @Override
        public List<CharSequence> getLines() {
            return Collections.emptyList();
        }

        @Override
        public boolean isEndSession() {
            return false;
        }

    };

    private final CrowdsecService crowdsecService;

    @Inject
    public CrowdsecSMTPConnectHandler(CrowdsecService service) {
        this.crowdsecService = service;
    }

    @Override
    public Response onConnect(SMTPSession session) {
        String ip = session.getRemoteAddress().getAddress().getHostAddress();
        return crowdsecService.findBanDecisions(session.getRemoteAddress())
                .map(decisions -> {
                    if (!decisions.isEmpty()) {
                        decisions.forEach(d -> logBanned(d, ip));
                        return Response.DISCONNECT;
                    } else {
                        return NOOP;
                    }
                }).block();
    }

    private boolean logBanned(CrowdsecDecision decision, String ip) {
        if (decision.getScope().equals("Ip")) {
            LOGGER.info("Ip {} is banned by crowdsec for {}. Full decision was {} ", decision.getValue(), decision.getDuration(), decision);
            return true;
        }
        if (decision.getScope().equals("Range")) {
            LOGGER.info("Ip {} belongs to range {} banned by crowdsec for {}. Full decision was {} ", ip, decision.getValue(), decision.getDuration(), decision);
            return true;
        }
        return false;
    }
}
