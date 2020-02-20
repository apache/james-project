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

package org.apache.james.queue.jms;

import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.plugin.StatisticsBrokerPlugin;
import org.apache.commons.text.RandomStringGenerator;
import org.apache.james.queue.api.MailQueueName;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.google.common.collect.ImmutableList;

public class BrokerExtension  implements ParameterResolver, BeforeAllCallback, AfterAllCallback {

    public static final String STATISTICS = "Statistics";

    public static MailQueueName generateRandomQueueName(BrokerService broker) {
        String queueName = new RandomStringGenerator.Builder().withinRange('a', 'z').build().generate(10);
        BrokerExtension.enablePrioritySupport(broker, queueName);
        return MailQueueName.of(queueName);
    }

    private static void enablePrioritySupport(BrokerService aBroker, String queueName) {
        PolicyMap pMap = new PolicyMap();
        PolicyEntry entry = new PolicyEntry();
        entry.setPrioritizedMessages(true);
        entry.setQueue(queueName);
        pMap.setPolicyEntries(ImmutableList.of(entry));
        aBroker.setDestinationPolicy(pMap);
    }

    private final BrokerService broker;

    public BrokerExtension() throws Exception  {
        broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.addConnector("tcp://127.0.0.1:61616");
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (context.getTags().contains(STATISTICS)) {
            enableStatistics(broker);
        }
        broker.start();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        broker.stop();
    }

    private void enableStatistics(BrokerService broker) {
        broker.setPlugins(new BrokerPlugin[]{new StatisticsBrokerPlugin()});
        broker.setEnableStatistics(true);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == BrokerService.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return broker;
    }
}
