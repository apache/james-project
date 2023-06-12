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

package org.apache.james.transport.mailets.jsieve;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

/**
 * Utility methods helpful for actions.
 */
public class ActionUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionUtils.class);

    private static final String ATTRIBUTE_PREFIX = ActionUtils.class.getPackage().getName() + ".";

    /**
     * Detect and handle locally looping mail. External loop detection is left
     * to the MTA.
     */
    public static void detectAndHandleLocalLooping(Mail aMail, ActionContext context, String anAttributeSuffix) {
        MailAddress thisRecipient = context.getRecipient();
        AttributeName attributeName = AttributeName.of(ATTRIBUTE_PREFIX + anAttributeSuffix);
        AttributeUtils
            .getValueAndCastFromMail(aMail, attributeName, String.class)
            .filter(Throwing.predicate(lastRecipient -> new MailAddress(lastRecipient).equals(thisRecipient)))
            .ifPresent(Throwing.consumer(any -> {
                MessagingException ex = new MessagingException(
                        "This message is looping! Message ID: "
                                + aMail.getMessage().getMessageID());
                LOGGER.warn(ex.getMessage(), ex);
                throw ex;
            }).sneakyThrow());
        aMail.setAttribute(new Attribute(attributeName, AttributeValue.of(thisRecipient.asString())));
    }
}
