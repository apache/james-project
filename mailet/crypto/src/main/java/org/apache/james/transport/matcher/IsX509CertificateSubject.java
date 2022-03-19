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



package org.apache.james.transport.matcher;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

/**
 * <p>
 * Checks if the subject of a X509Certificate contains the supplied string. The
 * certificate is read from the specified mail attribute.
 * </p><p>
 * If the specified attribute contains more than one certificate the matcher matches if at
 * least one of the certificates contains the given string.
 * </p>
 * <p>
 * Configuration string:
 * <ul>
 * <li>mailAttribute;string</li>
 * </ul>
 * 
 */
public class IsX509CertificateSubject extends GenericMatcher {
    protected AttributeName sourceAttribute;
    protected String check;
    
    @Override
    public void init() throws MessagingException {
        String condition = getCondition();
        if (condition == null || !condition.contains(";")) {
            throw new MessagingException("Invalid matcher configuration: " + condition);
        }
        
        int pos = condition.indexOf(";");
        sourceAttribute = AttributeName.of(condition.substring(0,pos).trim());
        check = condition.substring(pos + 1);
    }
    
    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {

        return AttributeUtils.getAttributeValueFromMail(mail, sourceAttribute)
            .map(this::getCertificates)
            .filter(this::hasCertificate)
            .map(any -> mail.getRecipients())
            .orElse(null);
    }

    private boolean hasCertificate(List<X509Certificate> certificates) {
        for (X509Certificate cert : certificates) {
            // Here I should use the method getSubjectX500Principal, but
            // that would break the compatibility with jdk13.
            Principal prin = cert.getSubjectDN();
            // TODO: Maybe here a more strong check should be done ...
            if ((prin.toString().indexOf(check)) > 0) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private List<X509Certificate> getCertificates(Object attributeValue) {
        if (attributeValue instanceof X509Certificate) {
            return Collections.singletonList((X509Certificate) attributeValue);
        } else {
            return (List<X509Certificate>) attributeValue;
        }
    }
}

