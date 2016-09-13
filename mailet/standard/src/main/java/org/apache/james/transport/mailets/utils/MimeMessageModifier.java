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

package org.apache.james.transport.mailets.utils;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;

public class MimeMessageModifier {

    private final MimeMessage message;

    public MimeMessageModifier(MimeMessage message) {
        this.message = message;
    }

    public void addSubjectPrefix(String subjectPrefix) throws MessagingException {
        String newSubject = prefixSubject(message, subjectPrefix);
        replaceSubject(message, newSubject);
    }
    
    private void replaceSubject(MimeMessage message, String newSubject) throws MessagingException {
        message.setSubject(null);
        message.setSubject(newSubject, Charsets.UTF_8.displayName());
    }
    
    private String prefixSubject(MimeMessage message, String subjectPrefix) throws MessagingException {
        String subject = message.getSubject();
    
        if (subject != null) {
            return Joiner.on(' ').join(subjectPrefix, subject);
        } else {
            return subjectPrefix;
        }
    }
}
