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

package org.apache.james.jmap.methods;

import static org.apache.james.jmap.methods.Method.JMAP_PREFIX;

import javax.inject.Inject;

import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;

public class SendMDNProcessor implements SetMessagesProcessor {
    private final MetricFactory metricFactory;

    @Inject
    public SendMDNProcessor(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    @Override
    public SetMessagesResponse process(SetMessagesRequest request, MailboxSession mailboxSession) {
        return metricFactory.withMetric(JMAP_PREFIX + "SendMDN",
            () -> handleMDNCreation(request));
    }

    public SetMessagesResponse handleMDNCreation(SetMessagesRequest request) {
        SetMessagesResponse.Builder builder = SetMessagesResponse.builder();

        request.getSendMDN()
            .forEach(creationMDNEntry -> builder.MDNNotSent(creationMDNEntry.getCreationId(),
                SetError.builder()
                    .description(String.format("Could not send MDN %s", creationMDNEntry.getCreationId().getId()))
                    .type("Not implemented yet")
                    .build()));

        return builder.build();
    }
}
