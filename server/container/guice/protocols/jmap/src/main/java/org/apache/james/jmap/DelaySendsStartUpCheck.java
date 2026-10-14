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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;

/**
 * A mail queue ignoring delays would send delayed mails right away: refuse to start rather than
 * advertising delayed sends we cannot honour.
 */
@Singleton
public class DelaySendsStartUpCheck implements StartUpCheck {
    public static final String CHECK_NAME = "DelaySendsForJMAP";

    private final boolean supportsDelaySends;
    private final MailQueueFactory<? extends MailQueue> mailQueueFactory;

    @Inject
    public DelaySendsStartUpCheck(@Named("supportsDelaySends") boolean supportsDelaySends, MailQueueFactory<? extends MailQueue> mailQueueFactory) {
        this.supportsDelaySends = supportsDelaySends;
        this.mailQueueFactory = mailQueueFactory;
    }

    @Override
    public CheckResult check() {
        if (supportsDelaySends && !mailQueueFactory.supportsDelays()) {
            return CheckResult.builder()
                .checkName(checkName())
                .resultType(ResultType.BAD)
                .description("'delay.sends.enabled' is set in jmap.properties but the mail queue does not support delays")
                .build();
        }
        return CheckResult.builder()
            .checkName(checkName())
            .resultType(ResultType.GOOD)
            .build();
    }

    @Override
    public String checkName() {
        return CHECK_NAME;
    }
}
