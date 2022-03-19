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

import static org.apache.james.transport.mailets.remote.delivery.Bouncer.DELIVERY_ERROR_CODE;

import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;
import org.apache.mailet.base.MailetUtil;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * <p>
 * Checks the SMTP error code attached to remote delivery failures 
 * </p>
 */
public class RemoteDeliveryFailedWithSMTPCode extends GenericMatcher {

    private Integer errorCode;

    @Override
    public void init() throws MessagingException {
        this.errorCode = MailetUtil.getInitParameterAsStrictlyPositiveInteger(getCondition());
        Preconditions.checkArgument(errorCode >= 101 && errorCode <= 554, "Invalid SMTP code");
    }

    @Override
    public Collection<MailAddress> match(Mail mail) {
        return AttributeUtils.getValueAndCastFromMail(mail, DELIVERY_ERROR_CODE, Integer.class)
            .filter(errorCode::equals)
            .map(any -> mail.getRecipients())
            .orElse(ImmutableList.of());
    }
}
