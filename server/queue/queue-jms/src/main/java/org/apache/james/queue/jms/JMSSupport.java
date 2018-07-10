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

/**
 * Interface which should get implemented by JMS depending implementations
 */
public interface JMSSupport {

    /** JMS Property which holds the recipient as String */
    String JAMES_MAIL_RECIPIENTS = "JAMES_MAIL_RECIPIENTS";

    /** JMS Property which holds the sender as String */
    String JAMES_MAIL_SENDER = "JAMES_MAIL_SENDER";

    /** JMS Property which holds the error message as String */
    String JAMES_MAIL_ERROR_MESSAGE = "JAMES_MAIL_ERROR_MESSAGE";

    /** JMS Property which holds the last updated time as long (ms) */
    String JAMES_MAIL_LAST_UPDATED = "JAMES_MAIL_LAST_UPDATED";

    /** JMS Property which holds the mail size as long (bytes) */
    String JAMES_MAIL_MESSAGE_SIZE = "JAMES_MAIL_MESSAGE_SIZE";

    /** JMS Property which holds the mail name as String */
    String JAMES_MAIL_NAME = "JAMES_MAIL_NAME";

    /** JMS Property which holds the association between recipients and specific headers*/
    String JAMES_MAIL_PER_RECIPIENT_HEADERS = "JAMES_MAIL_PER_RECIPIENT_HEADERS";

    /**
     * Separator which is used for separate an array of String values in the JMS
     * Property value
     */
    String JAMES_MAIL_SEPARATOR = ";";

    /** JMS Property which holds the remote hostname as String */
    String JAMES_MAIL_REMOTEHOST = "JAMES_MAIL_REMOTEHOST";

    /** JMS Property which holds the remote ipaddress as String */
    String JAMES_MAIL_REMOTEADDR = "JAMES_MAIL_REMOTEADDR";

    /** JMS Property which holds the mail state as String */
    String JAMES_MAIL_STATE = "JAMES_MAIL_STATE";

    /** JMS Property which holds the mail attribute names as String */
    String JAMES_MAIL_ATTRIBUTE_NAMES = "JAMES_MAIL_ATTRIBUTE_NAMES";

    /** JMS Property which holds next delivery time as long (ms) */
    String JAMES_NEXT_DELIVERY = "JAMES_NEXT_DELIVERY";

}
