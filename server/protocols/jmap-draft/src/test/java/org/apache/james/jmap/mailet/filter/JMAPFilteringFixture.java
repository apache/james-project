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

package org.apache.james.jmap.mailet.filter;

import org.apache.james.core.Username;
import org.apache.mailet.AttributeValue;

public interface JMAPFilteringFixture {
    String GA_BOU_ZO_MEU_FULL_ADDRESS = "GA BOU ZO MEU <GA.BOU.ZO.MEU@james.org>";
    String BOU = "BOU";

    String USER_1_FULL_ADDRESS = "user1 <user1@james.org>";
    String USER_1_ADDRESS = "user1@james.org";
    String USER_1_USERNAME = "user1";

    String USER_2_FULL_ADDRESS = "user2 <user2@james.org>";
    String USER_2_ADDRESS = "user2@james.org";
    String USER_2_USERNAME = "user2";

    String USER_1_AND_UNFOLDED_USER_FULL_ADDRESS = "user2 <sender1@james.org>, \r\nunfolded\r\n_user\r\n <unfolded_user@james.org>";

    String USER_3_FULL_ADDRESS = "user3 <user3@james.org>";
    String USER_3_ADDRESS = "user3@james.org";
    String USER_3_USERNAME = "user3";

    String USER_4_FULL_ADDRESS = "user4 <user4@james.org>";

    String SCRAMBLED_SUBJECT = "this is the subject =?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= of the mail";
    String UNSCRAMBLED_SUBJECT = "this is the subject Frédéric MARTIN of the mail";
    String SHOULD_NOT_MATCH = "should not match";

    String RECIPIENT_1 = "recipient1@james.org";
    Username RECIPIENT_1_USERNAME = Username.of("recipient1");
    AttributeValue<String> RECIPIENT_1_MAILBOX_1 = AttributeValue.of("recipient1_maibox1");

    String FRED_MARTIN_FULLNAME = "Frédéric MARTIN";
    String FRED_MARTIN_FULL_SCRAMBLED_ADDRESS = "=?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <fred.martin@linagora.com>";

    String UNFOLDED_USERNAME = "unfolded_user";

    String EMPTY = "";

    String TO_HEADER = "to";
    String CC_HEADER = "cc";
}
