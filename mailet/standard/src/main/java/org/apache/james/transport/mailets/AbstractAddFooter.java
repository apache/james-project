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

import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.Mail;
import org.apache.mailet.base.RFC2822Headers;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * An abstract implementation of a mailet that add a Footer to an email
 */
public abstract class AbstractAddFooter extends GenericMailet {

    /**
     * Takes the message and attaches a footer message to it.  Right now, it only
     * supports simple messages.  Needs to have additions to make it support
     * messages with alternate content types or with attachments.
     *
     * @param mail the mail being processed
     *
     * @throws MessagingException if an error arises during message processing
     */
    public void service(Mail mail) throws MessagingException {
        try {
            MimeMessage message = mail.getMessage();

            if (attachFooter(message)) {
                message.saveChanges();
            } else {
                log("Unable to add footer to mail " + mail.getName());
            }
        } catch (UnsupportedEncodingException e) {
            log("UnsupportedEncoding Unable to add footer to mail "
                    + mail.getName());
        } catch (IOException ioe) {
            throw new MessagingException("Could not read message", ioe);
        }
    }

    /**
     * Prepends the content of the MimePart as text to the existing footer
     *
     * @param part the MimePart to attach
     *
     * @throws MessagingException
     * @throws IOException
     */
    protected void addToText(MimePart part) throws MessagingException,
            IOException {
        //        log("Trying to add footer to " + part.getContent().toString());
        String contentType = part.getContentType();
        String content = (String) part.getContent();

        if (!content.endsWith("\n")) {
            content += "\r\n";
        }
        content += getFooterText();

        part.setContent(content, contentType);
        part.setHeader(RFC2822Headers.CONTENT_TYPE, contentType);
        //        log("After adding footer: " + part.getContent().toString());
    }

    /**
     * Prepends the content of the MimePart as HTML to the existing footer
     *
     * @param part the MimePart to attach
     *
     * @throws MessagingException
     * @throws IOException
     */
    protected void addToHTML(MimePart part) throws MessagingException,
            IOException {
        //        log("Trying to add footer to " + part.getContent().toString());
        String contentType = part.getContentType();
        String content = (String) part.getContent();

        /* This HTML part may have a closing <BODY> tag.  If so, we
         * want to insert out footer immediately prior to that tag.
         */
        int index = content.lastIndexOf("</body>");
        if (index == -1)
            index = content.lastIndexOf("</BODY>");
        String insert = "<br>" + getFooterHTML();
        content = index == -1 ? content + insert : content.substring(0, index)
                + insert + content.substring(index);

        part.setContent(content, contentType);
        part.setHeader(RFC2822Headers.CONTENT_TYPE, contentType);
        //        log("After adding footer: " + part.getContent().toString());
    }

    /**
     * Attach a footer a MimePart
     *
     * @param part the MimePart to which the footer is to be attached
     *
     * @return whether a footer was successfully attached
     * @throws MessagingException
     * @throws IOException
     */
    protected boolean attachFooter(MimePart part) throws MessagingException,
            IOException {
        //        log("Content type is " + part.getContentType());
        if (part.isMimeType("text/plain")
                && part.getContent() instanceof String) {
            addToText(part);
            return true;
        } else if (part.isMimeType("text/html")
                && part.getContent() instanceof String) {
            addToHTML(part);
            return true;
        } else if (part.isMimeType("multipart/mixed")
                || part.isMimeType("multipart/related")) {
            //Find the first body part, and determine what to do then.
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            MimeBodyPart firstPart = (MimeBodyPart) multipart.getBodyPart(0);
            boolean isFooterAttached = attachFooter(firstPart);
            if (isFooterAttached) {
                //We have to do this because of a bug in JavaMail (ref id 4403733)
                //http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4403733
                part.setContent(multipart);
            }
            return isFooterAttached;
        } else if (part.isMimeType("multipart/alternative")) {
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            int count = multipart.getCount();
            //            log("number of alternatives = " + count);
            boolean isFooterAttached = false;
            for (int index = 0; index < count; index++) {
                //                log("processing alternative #" + index);
                MimeBodyPart mimeBodyPart = (MimeBodyPart) multipart
                        .getBodyPart(index);
                isFooterAttached |= attachFooter(mimeBodyPart);
            }
            if (isFooterAttached) {
                //We have to do this because of a bug in JavaMail (ref id 4403733)
                //http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4403733
                part.setContent(multipart);
            }
            return isFooterAttached;
        } else {
            //Give up... we won't attach the footer to this MimePart
            return false;
        }
    }

    /**
     * This is exposed as a method for easy subclassing to provide alternate ways
     * to get the footer text.
     *
     * @return the footer text
     */
    protected abstract String getFooterText();

    /**
     * This is exposed as a method for easy subclassing to provide alternate ways
     * to get the footer text.  By default, this will take the footer text,
     * converting the linefeeds to &lt;br&gt; tags.
     *
     * @return the HTML version of the footer text
     */
    protected abstract String getFooterHTML();

}
