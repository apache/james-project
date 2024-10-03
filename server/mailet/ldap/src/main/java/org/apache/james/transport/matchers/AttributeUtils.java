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

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.RDNNameValuePair;

public class AttributeUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(HasLDAPAttribute.class);

    static String extractLdapAttributeValue(String attributeName, String ldapValue) {
        if (ldapValue.contains(",")) {
            try {
                return Arrays.stream(new DN(ldapValue).getRDNs())
                    .flatMap(rdn -> rdn.getNameValuePairs().stream())
                    .filter(pair -> pair.getAttributeName().equals("cn"))
                    .findFirst()
                    .map(RDNNameValuePair::getAttributeValue)
                    .orElse(ldapValue);
            } catch (LDAPException e) {
                LOGGER.info("Non DN value '{}' for attribute {} contains coma", ldapValue, attributeName);
                return ldapValue;
            }
        }
        return ldapValue;
    }
}
