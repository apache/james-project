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

import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.james.crowdsec.client.CrowdsecClientConfiguration;
import org.apache.james.crowdsec.client.CrowdsecHttpClient;
import org.apache.james.crowdsec.model.CrowdsecDecision;

import reactor.core.publisher.Mono;

class CrowdsecService {
    private final CrowdsecHttpClient crowdsecHttpClient;

    @Inject
    public CrowdsecService(CrowdsecClientConfiguration configuration) {
        this.crowdsecHttpClient = new CrowdsecHttpClient(configuration);
    }

    public Mono<List<CrowdsecDecision>> findBanDecisions(InetSocketAddress remoteAddress) {
        return crowdsecHttpClient.getCrowdsecDecisions()
                .map(decisions ->
                        decisions.stream().filter(
                                decision -> isBanned(decision, remoteAddress.getAddress().getHostAddress())
                        ).collect(Collectors.toList())
                );
    }

    private boolean isBanned(CrowdsecDecision decision, String ip) {
        if (decision.getScope().equals("Ip") && ip.contains(decision.getValue())) {
            return true;
        }
        if (decision.getScope().equals("Range") && belongsToNetwork(decision.getValue(), ip)) {
            return true;
        }
        return false;
    }

    private boolean belongsToNetwork(String bannedRange, String ip) {
        SubnetUtils subnetUtils = new SubnetUtils(bannedRange);
        subnetUtils.setInclusiveHostCount(true);

        return subnetUtils.getInfo().isInRange(ip);
    }

}
