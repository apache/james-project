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

package org.apache.james.mdn;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.mailet.base.mail.MimeMultipartReport;

/**
 * Class <code>MDNFactory</code> creates MimeMultipartReports containing
 * Message Delivery Notifications as specified by RFC 2298.
 */
public class MDNFactory {
    
    /**
     * Answers a MimeMultipartReport containing a
     * Message Delivery Notification as specified by RFC 2298.
     * 
     * @param humanText
     * @param mdnReport
     * @return MimeMultipartReport
     * @throws MessagingException
     */
    public static MimeMultipartReport create(String humanText,
            MDNReport mdnReport) throws MessagingException {
        // Create the message parts. According to RFC 2298, there are two
        // compulsory parts and one optional part...
        MimeMultipartReport multiPart = new MimeMultipartReport();
        multiPart.setReportType("disposition-notification");
        
        // Part 1: The 'human-readable' part
        MimeBodyPart humanPart = new MimeBodyPart();
        humanPart.setText(humanText);
        multiPart.addBodyPart(humanPart);

        // Part 2: MDN Report Part
        MimeBodyPart mdnPart = new MimeBodyPart();
        mdnPart.setContent(mdnReport.formattedValue(), "message/disposition-notification");
        multiPart.addBodyPart(mdnPart);

        // Part 3: The optional third part, the original message is omitted.
        // We don't want to propogate over-sized, virus infected or
        // other undesirable mail!
        // There is the option of adding a Text/RFC822-Headers part, which
        // includes only the RFC 822 headers of the failed message. This is
        // described in RFC 1892. It would be a useful addition!        
        return multiPart;
    }

}
