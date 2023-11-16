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

import java.util.List;

import javax.inject.Inject;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.james.model.CrowdsecClientConfiguration;
import org.apache.james.model.CrowdsecDecision;
import org.apache.james.model.CrowdsecHttpClient;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;

import reactor.core.scheduler.Schedulers;

public class CrowdsecEhloHook implements HeloHook {
    private final CrowdsecClientConfiguration crowdsecClientConfiguration;

    @Inject
    public CrowdsecEhloHook(CrowdsecClientConfiguration configuration) {
        this.crowdsecClientConfiguration = configuration;
    }

    @Override
    public HookResult doHelo(SMTPSession session, String helo) {
        String ip = session.getRemoteAddress().toString().split("/")[1].split(":")[0];
        CrowdsecHttpClient client = new CrowdsecHttpClient(crowdsecClientConfiguration);
        return client.getCrowdsecDecisions()
            .map(decision -> apply(decision, ip))
            .subscribeOn(Schedulers.boundedElastic()).block();
    }

    private boolean isBanned(CrowdsecDecision decision, String ip) {
        if (decision.getScope().equals("Ip") && ip.contains(decision.getValue())) {
            return true;
        }
        if (decision.getScope().equals("Range") && belongToNetwork(decision.getValue(), ip)) {
            return true;
        }
        return false;
    }

    private boolean belongToNetwork(String value, String ip) {
        SubnetUtils subnetUtils = new SubnetUtils(value);
        subnetUtils.setInclusiveHostCount(true);

        return subnetUtils.getInfo().isInRange(ip);
    }

    private HookResult apply(List<CrowdsecDecision> decisions, String ip) {
        return decisions.stream()
            .filter(decision -> isBanned(decision, ip))
            .findFirst()
            .map(banned -> HookResult.DENY)
            .orElse(HookResult.DECLINED);
    }
}
