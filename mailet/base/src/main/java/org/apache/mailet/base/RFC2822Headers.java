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


package org.apache.mailet.base;

/**
 * This utility class provides the set of header names explicitly defined in RFC 2822
 *
 */
public class RFC2822Headers  {

    // See Section 3.6.1 of RFC 2822

    /**
     * The name of the RFC 2822 header that stores the mail date.
     */
    public final static String DATE = "Date";

    // See Section 3.6.2 of RFC 2822

    /**
     * The name of the RFC 2822 header that stores the mail author(s).
     */
    public final static String FROM = "From";

    /**
     * The name of the RFC 2822 header that stores the actual mail transmission agent,
     * if this differs from the author of the message.
     */
    public final static String SENDER = "Sender";

    /**
     * The name of the RFC 2822 header that stores the reply-to address.
     */
    public final static String REPLY_TO = "Reply-To";

    // See Section 3.6.3 of RFC 2822

    /**
     * The name of the RFC 2822 header that stores the primary mail recipients.
     */
    public final static String TO = "To";

    /**
     * The name of the RFC 2822 header that stores the carbon copied mail recipients.
     */
    public final static String CC = "Cc";

    /**
     * The name of the RFC 2822 header that stores the blind carbon copied mail recipients.
     */
    public final static String BCC = "Bcc";

    // See Section 3.6.4 of RFC 2822

    /**
     * The name of the RFC 2822 header that stores the message id.
     */
    public final static String MESSAGE_ID = "Message-ID";

    /**
     * A common variation on the name of the RFC 2822 header that
     * stores the message id.  This is needed for certain filters and
     * processing of incoming mail.
     */
    public final static String MESSAGE_ID_VARIATION = "Message-Id";

    /**
     * The name of the RFC 2822 header that stores the message id of the message
     * that to which this email is a reply.
     */
    public final static String IN_REPLY_TO = "In-Reply-To";

    /**
     * The name of the RFC 2822 header that is used to identify the thread to
     * which this message refers.
     */
    public final static String REFERENCES = "References";

    // See Section 3.6.5 of RFC 2822

    /**
     * The name of the RFC 2822 header that stores the subject.
     */
    public final static String SUBJECT = "Subject";

    /**
     * The name of the RFC 2822 header that stores human-readable comments.
     */
    public final static String COMMENTS = "Comments";

    /**
     * The name of the RFC 2822 header that stores human-readable keywords.
     */
    public final static String KEYWORDS = "Keywords";

    // See Section 3.6.6 of RFC 2822

    /**
     * The name of the RFC 2822 header that stores the date the message was resent.
     */
    public final static String RESENT_DATE = "Resent-Date";

    /**
     * The name of the RFC 2822 header that stores the originator of the resent message.
     */
    public final static String RESENT_FROM = "Resent-From";

    /**
     * The name of the RFC 2822 header that stores the transmission agent
     * of the resent message.
     */
    public final static String RESENT_SENDER = "Resent-Sender";

    /**
     * The name of the RFC 2822 header that stores the recipients
     * of the resent message.
     */
    public final static String RESENT_TO = "Resent-To";

    /**
     * The name of the RFC 2822 header that stores the carbon copied recipients
     * of the resent message.
     */
    public final static String RESENT_CC = "Resent-Cc";

    /**
     * The name of the RFC 2822 header that stores the blind carbon copied recipients
     * of the resent message.
     */
    public final static String RESENT_BCC = "Resent-Bcc";

    /**
     * The name of the RFC 2822 header that stores the message id
     * of the resent message.
     */
    public final static String RESENT_MESSAGE_ID = "Resent-Message-ID";

    // See Section 3.6.7 of RFC 2822

    /**
     * The name of the RFC 2822 headers that store the tracing data for the return path.
     */
    public final static String RETURN_PATH = "Return-Path";

    /**
     * The name of the RFC 2822 headers that store additional tracing data.
     */
    public final static String RECEIVED = "Received";

    // MIME headers

    /**
     * The name of the MIME header that stores the content type.
     */
    public final static String CONTENT_TYPE = "Content-Type";

    /**
     * Private constructor to prevent instantiation
     */
    private RFC2822Headers() {}

}
