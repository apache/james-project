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


package org.apache.james.transport.mailets;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

/**
 * Add an prefix (tag) to the subject of a message <br>
 * <br>
 * <p/>
 * Sample Configuration: <br>
 * <pre><code>
 * &lt;mailet match="RecipientIs=robot@james.apache.org" class="TagMessage"&gt;
 * &lt;subjectPrefix&gt;[robot]&lt;/subjectPrefix&gt; &lt;/mailet&gt; <br>
 * </code></pre>
 */
public class AddSubjectPrefix extends GenericMailet {

    private String subjectPrefix;

    @Override
    public void init() throws MessagingException {
        subjectPrefix = getInitParameter("subjectPrefix");

        if (Strings.isNullOrEmpty(subjectPrefix)) {
            throw new MessagingException("Please configure a valid subjectPrefix");
        }
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        MimeMessage m = mail.getMessage();
        String newSubject = prefixSubject(m);
        replaceSubject(m, newSubject);
    }

    private void replaceSubject(MimeMessage m, String newSubject) throws MessagingException {
        m.setSubject(null);
        m.setSubject(newSubject, Charsets.UTF_8.displayName());
    }

    private String prefixSubject(MimeMessage m) throws MessagingException {
        String subject = m.getSubject();

        if (subject != null) {
            return Joiner.on(' ').join(subjectPrefix, subject);
        } else {
            return subjectPrefix;
        }
    }

    public String getMailetInfo() {
        return "AddSubjectPrefix Mailet";
    }


}
