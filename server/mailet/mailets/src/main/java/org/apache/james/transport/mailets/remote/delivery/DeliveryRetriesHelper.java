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

package org.apache.james.transport.mailets.remote.delivery;

import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;

public class DeliveryRetriesHelper {

    public static final AttributeName DELIVERY_RETRY_COUNT = AttributeName.of("delivery_retry_count");

    public static int retrieveRetries(Mail mail) {
        return AttributeUtils
            .getValueAndCastFromMail(mail, DELIVERY_RETRY_COUNT, Integer.class)
            .orElse(0);
    }

    public static void initRetries(Mail mail) {
        mail.setAttribute(makeAttribute(0));
    }

    public static void incrementRetries(Mail mail) {
        mail.setAttribute(makeAttribute(retrieveRetries(mail) + 1));
    }

    public static Attribute makeAttribute(Integer value) {
        return new Attribute(DELIVERY_RETRY_COUNT, AttributeValue.of(value));
    }
}
