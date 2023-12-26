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

package org.apache.james;

import static org.apache.james.model.CrowdsecClientConfiguration.DEFAULT_TIMEOUT;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.james.exception.CrowdsecException;
import org.apache.james.imap.api.ConnectionCheck;
import org.apache.james.model.CrowdsecClientConfiguration;
import org.apache.james.model.CrowdsecDecision;
import org.apache.james.model.CrowdsecHttpClient;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class CrowdsecImapConnectionCheck implements ConnectionCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrowdsecImapConnectionCheck.class);

    private final CrowdsecHttpClient client;

    @Inject
    public CrowdsecImapConnectionCheck(CrowdsecClientConfiguration crowdsecClientConfiguration) {
        this.client = new CrowdsecHttpClient(crowdsecClientConfiguration);
    }

    @Override
    public Publisher<Void> validate(InetSocketAddress remoteAddress) {
        String ip = remoteAddress.getAddress().getHostAddress();

        return client.getCrowdsecDecisions()
            .timeout(DEFAULT_TIMEOUT)
            .onErrorResume(TimeoutException.class, e -> Mono.fromRunnable(() -> LOGGER.warn("Timeout while questioning to CrowdSec. May need to check the CrowdSec configuration.")))
            .filter(decisions -> decisions.stream().anyMatch(decision -> isBanned(decision, ip)))
            .handle((crowdsecDecisions, synchronousSink) -> synchronousSink.error(new CrowdsecException("Ip " + ip + " is not allowed to connect to IMAP server by Crowdsec")));
    }

    private boolean isBanned(CrowdsecDecision decision, String ip) {
        if (decision.getScope().equals("Ip") && ip.contains(decision.getValue())) {
            LOGGER.warn("Connection from IP {} has been blocked by CrowdSec for duration {}", ip, decision.getDuration());
            return true;
        }
        if (decision.getScope().equals("Range") && belongToNetwork(decision.getValue(), ip)) {
            LOGGER.warn("Connection from IP {} has been blocked by CrowdSec for duration {}", ip, decision.getDuration());
            return true;
        }
        return false;
    }

    private boolean belongToNetwork(String value, String ip) {
        SubnetUtils subnetUtils = new SubnetUtils(value);
        subnetUtils.setInclusiveHostCount(true);

        return subnetUtils.getInfo().isInRange(ip);
    }
}
