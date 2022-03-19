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

package org.apache.mailet.base;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;

public class MailAddressFixture {
    public static final String JAMES_LOCAL = "localhost";
    public static final String JAMES_APACHE_ORG = "james.apache.org";
    public static final String JAMES2_APACHE_ORG = "james2.apache.org";

    public static final Domain JAMES_LOCAL_DOMAIN = Domain.of(JAMES_LOCAL);
    public static final Domain JAMES_APACHE_ORG_DOMAIN = Domain.of(JAMES_APACHE_ORG);
    public static final Domain JAMES2_APACHE_ORG_DOMAIN = Domain.of(JAMES2_APACHE_ORG);

    public static final MailAddress SENDER = createMailAddress("sender@" + JAMES_LOCAL);
    public static final MailAddress SENDER2 = createMailAddress("sender2@" + JAMES_LOCAL);
    public static final MailAddress SENDER3 = createMailAddress("sender3@" + JAMES_LOCAL);
    public static final MailAddress RECIPIENT1 = createMailAddress("recipient1@" + JAMES_LOCAL);
    public static final MailAddress RECIPIENT2 = createMailAddress("recipient2@" + JAMES_LOCAL);
    public static final MailAddress RECIPIENT3 = createMailAddress("recipient3@" + JAMES_LOCAL);

    public static final MailAddress ANY_AT_LOCAL = createMailAddress("any@" + JAMES_LOCAL);
    public static final MailAddress OTHER_AT_LOCAL = createMailAddress("other@" + JAMES_LOCAL);
    public static final MailAddress ANY_AT_JAMES = createMailAddress("any@" + JAMES_APACHE_ORG);
    public static final MailAddress POSTMASTER_AT_JAMES = createMailAddress("postmaster@" + JAMES_APACHE_ORG);
    public static final MailAddress OTHER_AT_JAMES = createMailAddress("other@" + JAMES_APACHE_ORG);
    public static final MailAddress ANY_AT_JAMES2 = createMailAddress("any@" + JAMES2_APACHE_ORG);
    public static final MailAddress OTHER_AT_JAMES2 = createMailAddress("other@" + JAMES2_APACHE_ORG);

    private static MailAddress createMailAddress(String mailAddress) {
        try {
            return new MailAddress(mailAddress);
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }
}
