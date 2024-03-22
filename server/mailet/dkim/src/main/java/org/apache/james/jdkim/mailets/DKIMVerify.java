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

package org.apache.james.jdkim.mailets;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.api.SignatureRecord;
import org.apache.james.jdkim.exceptions.FailException;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.google.common.annotations.VisibleForTesting;

/**
 * This mailet verify a message using the DKIM protocol
 *
 * Sample configuration:
 * <pre><code>
 * &lt;mailet match=&quot;All&quot; class=&quot;DKIMVerify&quot;&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 *
 * By default the mailet assume that Javamail will use LF instead of CRLF
 * so it will verify the hash using converted newlines. If you don't want this
 * behaviour then set forceCRLF attribute to false.
 */
public class DKIMVerify extends GenericMailet {

    public static final AttributeName DKIM_AUTH_RESULT = AttributeName.of("jDKIM.AUTHRESULT");

    @VisibleForTesting
    DKIMVerifier verifier;

    private boolean forceCRLF;

    @Inject
    public DKIMVerify(PublicKeyRecordRetriever publicKeyRecordRetriever) {
        verifier = new DKIMVerifier(publicKeyRecordRetriever);
    }

    @Override
    public void init() {
        forceCRLF = getInitParameter("forceCRLF", true);
    }

    public void service(Mail mail) throws MessagingException {
        try {
            MimeMessage message =  mail.getMessage();
            List<SignatureRecord> res = verifier.verify(message, forceCRLF);
            if (res == null || res.isEmpty()) {
                // neutral
                mail.setAttribute(new Attribute(DKIM_AUTH_RESULT, AttributeValue.of("neutral (no signatures)")));
            } else {
                // pass
                StringBuilder msg = new StringBuilder();
                msg.append("pass");
                for (SignatureRecord rec : res) {
                    msg.append(" (");
                    msg.append("identity ");
                    msg.append(rec.getIdentity().toString());
                    msg.append(")");
                }
                mail.setAttribute(new Attribute(DKIM_AUTH_RESULT, AttributeValue.of(msg.toString())));
            }
        } catch (FailException e) {
            // fail
            String relatedRecordIdentity = Optional.ofNullable(e.getRelatedRecordIdentity())
                .map(value -> "identity" + value + ":")
                .orElse("");
            mail.setAttribute(new Attribute(DKIM_AUTH_RESULT, AttributeValue.of("fail (" + relatedRecordIdentity + e.getMessage() + ")")));
        }
    }
}
