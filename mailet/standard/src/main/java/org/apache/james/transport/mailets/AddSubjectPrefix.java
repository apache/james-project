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
import org.apache.mailet.base.RFC2822Headers;

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
        
        /*
         * Get sure to use the right encoding when add the subjectPrefix..
         * otherwise we get problems with some special chars
         */
        String rawSubject = getFirstSubjectHeader(m);
        String mimeCharset = determineMailHeaderEncodingCharset(rawSubject);
        if (mimeCharset == null) { // most likely ASCII
            // it uses the system charset or the value of the
            // mail.mime.charset property if set
            m.setSubject(newSubject, Charsets.UTF_8.displayName());
        } else { // original charset determined
            String javaCharset = javax.mail.internet.MimeUtility
                    .javaCharset(mimeCharset);
            try {
                m.setSubject(newSubject, javaCharset);
            } catch (MessagingException e) {
                // known, but unsupported encoding
                // this should be logged, the admin may setup a more i18n
                // capable JRE, but the log API cannot be accessed from here
                // if (charset != null) log(charset +
                // " charset unsupported by the JRE, email subject may be
                // damaged");
                m.setSubject(newSubject); // recover
            }
            m.saveChanges();
        }
    }

    private String getFirstSubjectHeader(MimeMessage m) throws MessagingException {
        String delimiter = null;
        return m.getHeader(RFC2822Headers.SUBJECT, delimiter);
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

    /**
     * It attempts to determine the charset used to encode an "unstructured" RFC
     * 822 header (like Subject). The encoding is specified in RFC 2047. If it
     * cannot determine or the the text is not encoded then it returns null.
     * <p/>
     * Here is an example raw text: Subject:
     * =?iso-8859-2?Q?leg=FAjabb_pr=F3ba_l=F5elemmel?=
     * <p/>
     * TODO: Should we include this in a util class ?
     *
     * @param rawText the raw (not decoded) value of the header. Null means that the
     *                header was not present (in this case it always return null).
     * @return the MIME charset name or null if no encoding applied
     */
    static private String determineMailHeaderEncodingCharset(String rawText) {
        if (rawText == null)
            return null;
        int iEncodingPrefix = rawText.indexOf("=?");
        if (iEncodingPrefix == -1)
            return null;
        int iCharsetBegin = iEncodingPrefix + 2;
        int iSecondQuestionMark = rawText.indexOf('?', iCharsetBegin);
        if (iSecondQuestionMark == -1)
            return null;
        // safety checks
        if (iSecondQuestionMark == iCharsetBegin)
            return null; // empty charset? impossible
        int iThirdQuestionMark = rawText.indexOf('?', iSecondQuestionMark + 1);
        if (iThirdQuestionMark == -1)
            return null; // there must be one after encoding
        if (-1 == rawText.indexOf("?=", iThirdQuestionMark + 1))
            return null; // closing tag
        return rawText.substring(iCharsetBegin, iSecondQuestionMark);
    }

}
