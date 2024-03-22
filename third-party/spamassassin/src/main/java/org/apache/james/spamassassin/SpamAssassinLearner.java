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

package org.apache.james.spamassassin;


import java.io.InputStream;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.Host;

import com.github.fge.lambdas.Throwing;

public class SpamAssassinLearner {

    private final MetricFactory metricFactory;
    private final Host spamAssassinHost;

    @Inject
    public SpamAssassinLearner(MetricFactory metricFactory, SpamAssassinConfiguration spamAssassinConfiguration) {
        this.metricFactory = metricFactory;
        this.spamAssassinHost = spamAssassinConfiguration.getHost();
    }

    public void learnSpam(List<InputStream> messages, Username username) {
        SpamAssassinInvoker invoker = new SpamAssassinInvoker(metricFactory, spamAssassinHost.getHostName(), spamAssassinHost.getPort());
        messages
            .forEach(Throwing.consumer(message -> invoker.learnAsSpam(message, username)));
    }

    public void learnHam(List<InputStream> messages, Username username) {
        SpamAssassinInvoker invoker = new SpamAssassinInvoker(metricFactory, spamAssassinHost.getHostName(), spamAssassinHost.getPort());
        messages
            .forEach(Throwing.consumer(message -> invoker.learnAsHam(message, username)));
    }
}
