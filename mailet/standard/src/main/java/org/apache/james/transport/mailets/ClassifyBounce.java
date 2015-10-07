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

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Assesses the message to determine if it was a hard or soft bounce, and if it was a soft bounce, something of its nature..
 * <p/>
 * Sample configuration:
 * <p/>
 * <mailet match="All" class="ClassifyBounce">
 * <headerName>X-MailetHeader</headerName>
 * </mailet>
 */
public class ClassifyBounce extends GenericMailet {

    /**
     * The name of the header to be added.
     */
    private String headerName;

    /**
     * Initialize the mailet.
     */
    public void init() throws MessagingException {
        headerName = getInitParameter("headerName");

        // Check if needed config values are used
        if (headerName == null || headerName.equals("")) {
            throw new MessagingException("Please configure a header name to contain the classification (if any).");
        }
    }

    /**
     * Takes the message and adds a header to it.
     *
     * @param mail the mail being processed
     * @throws MessagingException if an error arises during message processing
     */
    public void service(Mail mail) {
        try {
            MimeMessage message = mail.getMessage();
            Classifier classifier = this.new Classifier(message);
            String classification = classifier.getClassification();
            //if ( !classification.equals("Normal") ) {
            message.setHeader(headerName, classification);
            message.saveChanges();
            //}
        } catch (javax.mail.MessagingException me) {
            log("Error classifying message: " + me.getMessage());
        }
    }

    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "SetMimeHeader Mailet";
    }

    private class Classifier {

        public Classifier(Message message) throws MessagingException {
            subject = message.getSubject();
            try {
                text = getRawText(message.getContent());
            } catch (IOException e) {
                throw (new MessagingException("Unable to extract message body. [" + e.getClass().getName() + "] " + e.getMessage()));
            }
        }

        public String getClassification() throws MessagingException {
            String classification = "Normal";
            switch (assess()) {
                case TYPE_DELIVERY_FAILURE:
                    classification = "Delivery failure";
                    break;
                case TYPE_MAILBOX_FULL:
                    classification = "Mailbox full";
                    break;
                case TYPE_OUT_OF_OFFICE:
                    classification = "Out of the office";
                    break;
            }
            return classification;
        }

        private int assess() throws MessagingException {

            int messageNature = TYPE_NORMAL;

            boolean outOfOffice;
            boolean mailBoxFull = false;
            boolean failure = false;

            outOfOffice = assessMessageSubjectOutOfOffice();
            if (!outOfOffice) {
                mailBoxFull = assessMessageSubjectMailboxFull();
            }
            if ((!outOfOffice) && (!mailBoxFull)) {
                failure = assessMessageSubjectFailure();
            }
            if (!(outOfOffice || mailBoxFull || failure)) {
                if (assessMessageUnreadable()) {
                    //get quite a few of these
                    failure = true;
                } else {
                    outOfOffice = assessMessageOutOfOffice();
                    mailBoxFull = assessMessageMailboxFull();
                    if ((!outOfOffice) && (!mailBoxFull)) {
                        failure = assessMessageFailure();
                    }
                }
            }

            if (failure) {
                messageNature = TYPE_DELIVERY_FAILURE;
            }
            if (mailBoxFull) {
                messageNature = TYPE_MAILBOX_FULL;
            }
            if (outOfOffice) {
                messageNature = TYPE_OUT_OF_OFFICE;
            }

            return messageNature;
        }

        private String getRawText(Object o) throws MessagingException, IOException {

            String s = null;

            if (o instanceof Multipart) {
                Multipart multi = (Multipart) o;
                for (int i = 0; i < multi.getCount(); i++) {
                    s = getRawText(multi.getBodyPart(i));
                    if (s != null) {
                        if (s.length() > 0) {
                            break;
                        }
                    }
                }
            } else if (o instanceof BodyPart) {
                BodyPart aBodyContent = (BodyPart) o;
                StringTokenizer aTypeTokenizer = new StringTokenizer(aBodyContent.getContentType(), "/");
                String abstractType = aTypeTokenizer.nextToken();
                if (abstractType.compareToIgnoreCase("MESSAGE") == 0) {
                    Message inlineMessage = (Message) aBodyContent.getContent();
                    s = getRawText(inlineMessage.getContent());
                }
                if (abstractType.compareToIgnoreCase("APPLICATION") == 0) {
                    s = "Attached File: " + aBodyContent.getFileName();
                }
                if (abstractType.compareToIgnoreCase("TEXT") == 0) {
                    try {
                        Object oS = aBodyContent.getContent();
                        if (oS instanceof String) {
                            s = (String) oS;
                        } else {
                            throw (new MessagingException("Unkown MIME Type (?): " + oS.getClass()));
                        }
                    } catch (Exception e) {
                        throw (new MessagingException("Unable to read message contents (" + e.getMessage() + ")"));
                    }
                }
                if (abstractType.compareToIgnoreCase("MULTIPART") == 0) {
                    s = getRawText(aBodyContent.getContent());
                }
            }

            if (o instanceof String) {
                s = (String) o;
            }

//          else {
//              if (m.isMimeType("text/html")) {
//                  s = m.getContent().toString();
//              }
//              if (m.isMimeType("text/plain")) {
//                  s = m.getContent().toString();
//              }   
//          }

            return s;
        }

        private boolean assessMessageUnreadable() throws MessagingException {
            boolean evil = false;
            if (subject.compareToIgnoreCase("Delivery Status Notification (Failure)") == 0) {
                evil = findInBody("Unable to read message contents");
            }
            return evil;
        }

        private boolean assessMessageFailure() {
            boolean failed = findInBody("User[\\s]+unknown");
            if (!failed) {
                failed = findInBody("No[\\s]+such[\\s]+user");
            }
            if (!failed) {
                failed = findInBody("550[\\s]+Invalid[\\s]+recipient");
            }
            if (!failed) {
                failed = findInBody("550[\\s]+Bogus[\\s]+Address");
            }
            if (!failed) {
                failed = findInBody("addresses[\\s]+were[\\s]+unknown");
            }
            if (!failed) {
                failed = findInBody("user[\\s]+is[\\s]+no[\\s]+longer[\\s]+associated[\\s]+with[\\s]+this[\\s]+company");
            }
            if (!failed) {
                failed = findInBody("Unknown[\\s]+Recipient");
            }
            if (!failed) {
                failed = findInBody("destination[\\s]+addresses[\\s]+were[\\s]+unknown");
            }
            if (!failed) {
                failed = findInBody("unknown[\\s]+user");
            }
            if (!failed) {
                failed = findInBody("recipient[\\s]+name[\\s]+is[\\s]+not[\\s]+recognized");
            }
            if (!failed) {
                failed = findInBody("not[\\s]+listed[\\s]+in[\\s]+Domino[\\s]+Directory");
            }
            if (!failed) {
                failed = findInBody("Delivery[\\s]+Status[\\s]+Notification[\\s]+\\Q(\\EFailure\\Q)\\E");
            }
            if (!failed) {
                failed = findInBody("This[\\s]+is[\\s]+a[\\s]+permanent[\\s]+error");
            }
            if (!failed) {
                failed = findInBody("This[\\s]+account[\\s]+has[\\s]+been[\\s]+closed");
            }
            if (!failed) {
                failed = findInBody("addresses[\\s]+had[\\s]+permanent[\\s]+fatal[\\s]+errors");
            }
            return failed;
        }

        private boolean assessMessageMailboxFull() {
            boolean full = findInBody("Connection[\\s]+timed[\\s]+out");
            if (!full) {
                full = findInBody("Over[\\s]+quota");
            }
            if (!full) {
                full = findInBody("size[\\s]+limit");
            }
            if (!full) {
                full = findInBody("account[\\s]+is[\\s]+full");
            }
            if (!full) {
                full = findInBody("diskspace[\\s]+quota");
            }
            if (!full) {
                full = findInBody("rejected[\\s]+for[\\s]+policy[\\s]+reasons");
            }
            if (!full) {
                full = findInBody("mailbox[\\s]+size[\\s]+limit");
            }
            return full;
        }

        private boolean assessMessageOutOfOffice() {
            //if ( subject.toLowerCase().startsWith("re:") ) {
            boolean out = findInBody("out[\\s]+of[\\s]+the[\\s]+office");
            if (!out) {
                out = findInBody("out[\\s]+of[\\s]+my[\\s]+office");
            }
            if (!out) {
                out = findInBody("back[\\s]+in[\\s]+the[\\s]+office");
            }
            if (!out) {
                out = findInBody("I[\\s]+am[\\s]+overseas");
            }
            if (!out) {
                out = findInBody("I[\\s]+am[\\s]+away");
            }
            if (!out) {
                out = findInBody("I[\\s]+am[\\s]+on[\\s]+leave");
            }
            if (!out) {
                out = (findInBody("Auto[\\s]*generated") && findInBody("on[\\s]+leave"));
            }
            //}
            return out;
        }

        private boolean assessMessageSubjectOutOfOffice() {
            boolean out = findInSubject("Out[\\s]+of[\\s]+office");
            if (!out) {
                out = findInSubject("Out[\\s]+of[\\s]+my[\\s]+office");
            }
            if (!out) {
                out = findInSubject("out[\\s]+of[\\s]+the[\\s]+office");
            }
            if (!out) {
                out = findInSubject("away[\\s]+from[\\s]+my[\\s]+mail");
            }
            if (!out) {
                out = findInSubject("on[\\s]+leave");
            }
            return out;
        }

        private boolean assessMessageSubjectMailboxFull() {
            return ((findInSubject("Email[\\s]+message[\\s]+blocked") && findInSubject("Image[\\s]+File[\\s]+Type")));
        }

        private boolean assessMessageSubjectFailure() {
            boolean failed = ((findInSubject("DELIVERY[\\s]+FAILURE") && findInSubject("does[\\s]+not[\\s]+exist")));
            if (!failed) {
                failed = ((findInSubject("DELIVERY[\\s]+FAILURE") && findInSubject("not[\\s]+listed")));
            }
            if (!failed) {
                failed = findInSubject("Permanent[\\s]+Delivery[\\s]+Failure");
            }
            if (!failed) {
                failed = findInSubject("User[\\s]+unknown");
            }
            if (!failed) {  //no idea...
                String s = subject.toLowerCase();
                failed = (s.indexOf("user") > 0 && (s.indexOf("unknown") > s.indexOf("user")));
            }
            return failed;
        }

        private boolean findInBody(String regExp) {
            boolean retval = false;
            if (text != null) {
                Pattern pat = Pattern.compile(regExp, Pattern.CASE_INSENSITIVE);
                Matcher m = pat.matcher(text);
                retval = m.find();
            }
            return retval;
        }

        private boolean findInSubject(String regExp) {
            Pattern pat = Pattern.compile(regExp, Pattern.CASE_INSENSITIVE);
            Matcher m = pat.matcher(subject);
            return m.find();
        }

        private String subject;
        private String text;

        public final static int TYPE_NORMAL = 1;
        public final static int TYPE_SPAM = 2;  // should be trapped elsewhere
        public final static int TYPE_OUT_OF_OFFICE = 3;
        public final static int TYPE_DELIVERY_FAILURE = 4;
        public final static int TYPE_MAILBOX_FULL = 5;
        public final static int TYPE_OUTBOUND = 6;

    }

}

