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

import org.apache.james.jdkim.DKIMVerifier;
import org.apache.james.jdkim.api.BodyHasher;
import org.apache.james.jdkim.api.Headers;
import org.apache.james.jdkim.api.SignatureRecord;
import org.apache.james.jdkim.exceptions.FailException;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

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
 * behaviout then set forceCRLF attribute to false. 
 */
public class DKIMVerify extends GenericMailet {

    public static final String DKIM_AUTH_RESULT_ATTRIBUTE = "jDKIM.AUTHRESULT";
    
    protected DKIMVerifier verifier = null;
    private boolean forceCRLF;

    @Override
    public void init() {
        verifier = new DKIMVerifier();
        forceCRLF = getInitParameter("forceCRLF", true);
    }
    
    public void service(Mail mail) throws MessagingException {
        try {
            MimeMessage message = mail.getMessage();
            List<SignatureRecord> res = verify(verifier, message, forceCRLF);
            if (res == null || res.isEmpty()) {
                // neutral
                mail.setAttribute(DKIM_AUTH_RESULT_ATTRIBUTE, "neutral (no signatures)");
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
                mail.setAttribute(DKIM_AUTH_RESULT_ATTRIBUTE, msg.toString());
            }
        } catch (FailException e) {
            // fail
            mail.setAttribute(DKIM_AUTH_RESULT_ATTRIBUTE, "fail ("+(e.getRelatedRecordIdentity() != null ? "identity "+ e.getRelatedRecordIdentity() + ": " : "")+e.getMessage()+")");
        }
        
    }

	protected static List<SignatureRecord> verify(DKIMVerifier verifier, MimeMessage message, boolean forceCRLF)
			throws MessagingException, FailException {
		Headers headers = new MimeMessageHeaders(message);
		BodyHasher bh = verifier.newBodyHasher(headers);
		try {
		    if (bh != null) {
		    	OutputStream os = new HeaderSkippingOutputStream(bh
		                .getOutputStream());
		    	if (forceCRLF) os = new CRLFOutputStream(os);
		        message.writeTo(os);
		        bh.getOutputStream().close();
		    }
		    
		} catch (IOException e) {
		    throw new MessagingException("Exception calculating bodyhash: "
		            + e.getMessage(), e);
		}
		return verifier.verify(bh);
	}
}
