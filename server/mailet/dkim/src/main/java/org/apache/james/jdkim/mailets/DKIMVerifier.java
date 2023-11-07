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

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.jdkim.api.BodyHasher;
import org.apache.james.jdkim.api.Headers;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.api.SignatureRecord;
import org.apache.james.jdkim.exceptions.FailException;
import org.apache.james.server.core.MimeMessageWrapper;

public class DKIMVerifier {
    private final org.apache.james.jdkim.DKIMVerifier originalVerifier;

    public DKIMVerifier(PublicKeyRecordRetriever publicKeyRecordRetriever) {
        this.originalVerifier = new org.apache.james.jdkim.DKIMVerifier(publicKeyRecordRetriever);
    }

    public List<SignatureRecord> verifyUsingCRLF(MimeMessage message) throws MessagingException, FailException {
        return verify((MimeMessageWrapper) message, true);
    }

    public List<SignatureRecord> verify(MimeMessageWrapper message, boolean forceCRLF) throws MessagingException, FailException {
        Headers headers = new MimeMessageHeaders(message);
        BodyHasher bh = originalVerifier.newBodyHasher(headers);
        try {
            if (bh != null) {
                OutputStream os = new HeaderSkippingOutputStream(bh
                    .getOutputStream());
                if (forceCRLF) {
                    os = new CRLFOutputStream(os);
                }
                message.getInputStream().transferTo(os);
            }

        } catch (IOException e) {
            throw new MessagingException("Exception calculating bodyhash: "
                    + e.getMessage(), e);
        } finally {
            try {
                if (bh != null) {
                    bh.getOutputStream().close();
                }
            } catch (IOException e) {
                throw new MessagingException("Exception calculating bodyhash: "
                        + e.getMessage(), e);
            }
        }
        return originalVerifier.verify(bh);
    }
}
