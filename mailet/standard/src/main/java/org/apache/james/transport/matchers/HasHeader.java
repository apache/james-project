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

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMatcher;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.Collection;
import java.util.LinkedList;
import java.util.StringTokenizer;

/**
 * use: <pre><code>&lt;mailet match="HasHeader={&lt;header&gt;[=value]}+" class="..." /&gt;</code></pre>
 * <p/>
 * <p>This matcher checks if the header named is present. If complements the
 * AddHeader mailet.</p>
 */
public class HasHeader extends GenericMatcher {

    private LinkedList<String> conditionline_ = new LinkedList<String>();

    // set headernames and values
    public void init() throws MessagingException {
        StringTokenizer st = new StringTokenizer(getCondition(), "+");
        conditionline_ = new LinkedList<String>();

        // separates the headernames from the matchline
        while (st.hasMoreTokens()) {
            String condition = st.nextToken().trim();
            conditionline_.add(condition);
        }
    }

    public Collection<MailAddress> match(Mail mail) throws javax.mail.MessagingException {
        boolean match = false;
        MimeMessage message = mail.getMessage();

        for (String element : conditionline_) {
            StringTokenizer st = new StringTokenizer(element, "=", false);
            String header;

            // read the headername
            if (st.hasMoreTokens()) {
                header = st.nextToken().trim();
            } else {
                throw new MessagingException("Missing headerName");
            }

            // try to read headervalue
            String headerValue;
            if (st.hasMoreTokens()) {
                headerValue = st.nextToken().trim();
            } else {
                headerValue = null;
            }

            // find headername in Mailheaders
            String[] headerArray = message.getHeader(header);
            if (headerArray != null && headerArray.length > 0) {
                // if there is the headername specified without the headervalue
                // only the existence of the headername ist performed
                if (headerValue != null) {
                    //
                    if (headerArray[0].trim().equalsIgnoreCase(headerValue)) {
                        // headername and value found and match to the condition
                        match = true;
                    } else {
                        // headername and value found but the value does not match the condition
                        match = false;
                        // if one condition fails the matcher returns false
                        break;
                    }
                } else {
                    // just the headername is specified
                    match = true;
                }
            } else {
                // no headername is found
                match = false;
                // if one condition fails the matcher returns false
                break;
            }
        }

        return (match) ? mail.getRecipients() : null;

    }
} 

