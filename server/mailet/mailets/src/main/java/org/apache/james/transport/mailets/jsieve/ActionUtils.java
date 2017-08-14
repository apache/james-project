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

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods helpful for actions.
 */
public class ActionUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionUtils.class);

    private final static String ATTRIBUTE_PREFIX = ActionUtils.class.getPackage().getName() + ".";

    /**
     * Answers the sole intended recipient for aMail.
     * 
     * @param aMail
     * @return String
     * @throws MessagingException
     */
    public static MailAddress getSoleRecipient(Mail aMail) throws MessagingException
    {
        if (aMail.getRecipients() == null) {
            throw new MessagingException("Invalid number of recipients - 0"
                    + ". Exactly 1 recipient is expected.");
        } else if (1 != aMail.getRecipients().size())
            throw new MessagingException("Invalid number of recipients - "
                    + Integer.toString(aMail.getRecipients().size())
                    + ". Exactly 1 recipient is expected.");
        return aMail.getRecipients().iterator().next();
    }

    /**
     * Detect and handle locally looping mail. External loop detection is left
     * to the MTA.
     * 
     * @param aMail
     * @param context not null
     * @param anAttributeSuffix
     * @throws MessagingException
     */
    public static void detectAndHandleLocalLooping(Mail aMail, ActionContext context, String anAttributeSuffix)
            throws MessagingException
    {
        MailAddress thisRecipient = getSoleRecipient(aMail);
        MailAddress lastRecipient = (MailAddress) aMail
                .getAttribute(ATTRIBUTE_PREFIX + anAttributeSuffix);
        if (null != lastRecipient && lastRecipient.equals(thisRecipient))
        {
            MessagingException ex = new MessagingException(
                    "This message is looping! Message ID: "
                            + aMail.getMessage().getMessageID());
            LOGGER.warn(ex.getMessage(), ex);
            throw ex;
        }
        aMail.setAttribute(ATTRIBUTE_PREFIX + anAttributeSuffix,
                thisRecipient);
    }
}
