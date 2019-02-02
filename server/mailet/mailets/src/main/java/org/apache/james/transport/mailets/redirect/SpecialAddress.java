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

package org.apache.james.transport.mailets.redirect;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;

public class SpecialAddress {

    public static final MailAddress SENDER = AddressMarker.SENDER;
    public static final MailAddress REVERSE_PATH = AddressMarker.REVERSE_PATH;
    public static final MailAddress FROM = AddressMarker.FROM;
    public static final MailAddress REPLY_TO = AddressMarker.REPLY_TO;
    public static final MailAddress TO = AddressMarker.TO;
    public static final MailAddress RECIPIENTS = AddressMarker.RECIPIENTS;
    public static final MailAddress DELETE = AddressMarker.DELETE;
    public static final MailAddress UNALTERED = AddressMarker.UNALTERED;
    public static final MailAddress NULL = AddressMarker.NULL;

    public static class AddressMarker {

        public static final Domain ADDRESS_MARKER = Domain.of("address.marker");
        public static final MailAddress SENDER = mailAddressUncheckedException(SpecialAddressKind.SENDER, ADDRESS_MARKER);
        public static final MailAddress REVERSE_PATH = mailAddressUncheckedException(SpecialAddressKind.REVERSE_PATH, ADDRESS_MARKER);
        public static final MailAddress FROM = mailAddressUncheckedException(SpecialAddressKind.FROM, ADDRESS_MARKER);
        public static final MailAddress REPLY_TO = mailAddressUncheckedException(SpecialAddressKind.REPLY_TO, ADDRESS_MARKER);
        public static final MailAddress TO = mailAddressUncheckedException(SpecialAddressKind.TO, ADDRESS_MARKER);
        public static final MailAddress RECIPIENTS = mailAddressUncheckedException(SpecialAddressKind.RECIPIENTS, ADDRESS_MARKER);
        public static final MailAddress DELETE = mailAddressUncheckedException(SpecialAddressKind.DELETE, ADDRESS_MARKER);
        public static final MailAddress UNALTERED = mailAddressUncheckedException(SpecialAddressKind.UNALTERED, ADDRESS_MARKER);
        public static final MailAddress NULL = mailAddressUncheckedException(SpecialAddressKind.NULL, ADDRESS_MARKER);

        private static MailAddress mailAddressUncheckedException(SpecialAddressKind kind, Domain domain) {
            try {
                return new MailAddress(kind.getValue(), domain);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static boolean isSpecialAddress(MailAddress mailAddress) {
        return mailAddress.getDomain().equals(AddressMarker.ADDRESS_MARKER);
    }
}