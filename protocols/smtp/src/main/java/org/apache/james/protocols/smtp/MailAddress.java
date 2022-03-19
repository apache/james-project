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

package org.apache.james.protocols.smtp;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * A representation of an email address.
 * 
 * <p>This class encapsulates functionality to access different
 * parts of an email address without dealing with its parsing.</p>
 *
 * <p>A MailAddress is an address specified in the MAIL FROM and
 * RCPT TO commands in SMTP sessions.  These are either passed by
 * an external server to the mailet-compliant SMTP server, or they
 * are created programmatically by the mailet-compliant server to
 * send to another (external) SMTP server.  Mailets and matchers
 * use the MailAddress for the purpose of evaluating the sender
 * and recipient(s) of a message.</p>
 *
 * <p>MailAddress parses an email address as defined in RFC 821
 * (SMTP) p. 30 and 31 where addresses are defined in BNF convention.
 * As the mailet API does not support the aged "SMTP-relayed mail"
 * addressing protocol, this leaves all addresses to be a {@code <mailbox>},
 * as per the spec. 
 *
 * <p>This class is a good way to validate email addresses as there are
 * some valid addresses which would fail with a simpler approach
 * to parsing address. It also removes the parsing burden from
 * mailets and matchers that might not realize the flexibility of an
 * SMTP address. For instance, "serge@home"@lokitech.com is a valid
 * SMTP address (the quoted text serge@home is the local-part and
 * lokitech.com is the domain). This means all current parsing to date
 * is incorrect as we just find the first '@' and use that to separate
 * local-part from domain.</p>
 *
 * <p>This parses an address as per the BNF specification for <mailbox>
 * from RFC 821 on page 30 and 31, section 4.1.2. COMMAND SYNTAX.
 * http://www.freesoft.org/CIE/RFC/821/15.htm</p>
 *
 * <strong>This version is copied from mailet-api with a few changes to not make it depend on javamail</strong>
 *
 * @Deprecated Use james-core {@link org.apache.james.core.MailAddress} instead.
 */
@Deprecated
public class MailAddress extends org.apache.james.core.MailAddress {
    public MailAddress(String address) throws AddressException {
        super(address);
    }

    public MailAddress(String localPart, String domain) throws AddressException {
        super(localPart, domain);
    }

    public MailAddress(InternetAddress address) throws AddressException {
        super(address);
    }

    public MailAddress(org.apache.james.core.MailAddress address) throws AddressException {
        super(address.asString());
    }
}
