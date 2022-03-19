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

package org.apache.james.transport.matchers;

import static org.apache.mailet.DsnParameters.Notify.FAILURE;

import java.util.Collection;
import java.util.Optional;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.util.FunctionalUtils;
import org.apache.mailet.DsnParameters;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * Returns the list of Recipients for which DSN FAILURE notifications
 * should be generated.
 *
 * This include only by default recipients explicitly positioning FAILURE value as part of the NOTIFY RCPT parameter,
 * as well as those not having specified NOTIFY RCPT parameters.
 *
 * Example:
 *
 * <pre>
 * &lt;mailet match="DSNFailureRequested" class="XXX"/&gt;
 * </pre>
 *
 * This can be configured to not include recipients not having specified NOTIFY RCPT parameters.
 *
 * Example:
 *
 * <pre>
 * &lt;mailet match="DSNFailureRequested=shouldNotMatchByDefault" class="XXX"/&gt;
 * </pre>
 */
public class DSNFailureRequested extends GenericMatcher {
    private static final boolean DEFAULT_PRESENT = true;
    public static final String CONDITION = "shouldNotMatchByDefault";

    private boolean shouldBeMatchedByDefault;

    @Override
    public void init() throws MessagingException {
        Preconditions.checkState(Strings.isNullOrEmpty(getCondition()) || CONDITION.equals(getCondition()),
            "DSNFailureRequested condition, when specified, should be '%s'", CONDITION);
        shouldBeMatchedByDefault = Optional.ofNullable(getCondition())
            .map(CONDITION::equals)
            .map(FunctionalUtils.negate())
            .orElse(DEFAULT_PRESENT);
    }

    @Override
    public Collection<MailAddress> match(Mail mail) {
        return mail.getRecipients().stream()
            .filter(recipient -> failureRequested(mail, recipient))
            .collect(ImmutableList.toImmutableList());
    }

    private Boolean failureRequested(Mail mail, MailAddress recipient) {
        return mail.dsnParameters()
            .map(dsnParameters -> failureRequested(recipient, dsnParameters))
            .orElse(shouldBeMatchedByDefault);
    }

    private boolean failureRequested(MailAddress recipient, DsnParameters dsnParameters) {
        return Optional.ofNullable(dsnParameters.getRcptParameters().get(recipient))
            .map(rcptParams -> rcptParams.getNotifyParameter()
                .map(notifies -> notifies.contains(FAILURE))
                .orElse(shouldBeMatchedByDefault))
            .orElse(shouldBeMatchedByDefault);
    }

    @Override
    public String getMatcherName() {
        return "DSNFailureRequested";
    }

}
