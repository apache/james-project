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

import org.apache.mailet.base.GenericMatcher;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;
import java.util.Collection;

/**
 * Checks whether this message has an attachment
 *
 * @version CVS $Revision$ $Date$
 */
public class HasAttachment extends GenericMatcher {

    /** 
     * Either every recipient is matching or neither of them.
     * @throws MessagingException if no attachment is found and at least one exception was thrown
     */
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        
        Exception anException = null;
        
        try {
            MimeMessage message = mail.getMessage();
            Object content;
            
            /**
             * if there is an attachment and no inline text,
             * the content type can be anything
             */
            if (message.getContentType() == null) {
                return null;
            }
            
            content = message.getContent();
            if (content instanceof Multipart) {
                Multipart multipart = (Multipart) content;
                for (int i = 0; i < multipart.getCount(); i++) {
                    try {
                        Part part = multipart.getBodyPart(i);
                        String fileName = part.getFileName();
                        if (fileName != null) {
                            return mail.getRecipients(); // file found
                        }
                    } catch (MessagingException e) {
                        anException = e;
                    } // ignore any messaging exception and process next bodypart
                }
            } else {
                String fileName = message.getFileName();
                if (fileName != null) {
                    return mail.getRecipients(); // file found
                }
            }
        } catch (Exception e) {
            anException = e;
        }
        
        // if no attachment was found and at least one exception was catched rethrow it up
        if (anException != null) {
            throw new MessagingException("Malformed message", anException);
        }
        
        return null; // no attachment found
    }
}
