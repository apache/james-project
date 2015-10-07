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

package org.apache.james.protocols.smtp;

/**
 * Result code defined in RFC 2821
 */
public class SMTPRetCode {
    /** System status, or system help reply */
    public static final String SYSTEM_STATUS = "211";

    /**
     * Help message (Information on how to use the receiver or the meaning of a
     * particular non-standard command; this reply is useful only to the human
     * user)
     */
    public static final String HELP_MESSAGE = "214";

    /** <domain> Service ready */
    public static final String SERVICE_READY = "220";

    /** <domain> Service closing transmission channel */
    public static final String SYSTEM_QUIT = "221";

    /** Auth ok */
    public static final String AUTH_OK = "235";

    /** Requested mail action okay, completed */
    public static final String MAIL_OK = "250";

    /**
     * 251 User not local; will forward to <forward-path> (See section 3.4)
     */
    public static final String MAIL_FORWARDING = "251";

    /**
     * Cannot VRFY user, but will accept message and attempt delivery (See
     * section 3.5.3)
     */
    public static final String MAIL_UNDEFINDED = "252";

    public static final String AUTH_READY = "334";

    /** Start mail input; end with <CRLF>.<CRLF> */
    public static final String DATA_READY = "354";

    /**
     * <domain> Service not available, closing transmission channel (This may be
     * a reply to any command if the service knows it must shut down)
     */
    public static final String SERVICE_NOT_AVAILABLE = "421";

    /**
     * This response to the AUTH command indicates that the user needs to
     * transition to the selected authentication mechanism. This typically done
     * by authenticating once using the PLAIN authentication mechanism.
     */
    public static final String AUTH_PASSWORD_TRANSITION_ERROR = "432";

    /**
     * Requested mail action not taken: mailbox unavailable (e.g., mailbox busy)
     */
    public static final String MAILBOX_TEMP_UNAVAILABLE = "450";

    /**
     * Requested action aborted: local error in processing
     */
    public static final String LOCAL_ERROR = "451";

    /**
     * Requested action not taken: insufficient system storage
     */
    public static final String SYSTEM_STORAGE_ERROR = "452";

    /**
     * This response to the AUTH command indicates that the authentication
     * failed due to a temporary server failure.
     */
    public static final String AUTH_TEMPORARY_ERROR = "454";

    /**
     * Syntax error, command unrecognized (This may include errors such as
     * command line too long)
     */
    public static final String SYNTAX_ERROR_COMMAND_UNRECOGNIZED = "500";

    /**
     * Syntax error in parameters or arguments
     */
    public static final String SYNTAX_ERROR_ARGUMENTS = "501";

    /**
     * Command not implemented (see section 4.2.4)
     */
    public static final String UNIMPLEMENTED_COMMAND = "502";

    /**
     * Bad sequence of commands
     */
    public static final String BAD_SEQUENCE = "503";

    /**
     * Command parameter not implemented
     */
    public static final String PARAMETER_NOT_IMPLEMENTED = "504";

    /**
     * This response may be returned by any command other than AUTH, EHLO, HELO,
     * NOOP, RSET, or QUIT. It indicates that server policy requires
     * authentication in order to perform the requested action.
     */
    public static final String AUTH_REQUIRED = "530";

    /**
     * Auth failed
     */
    public static final String AUTH_FAILED = "535";

    /**
     * This response to the AUTH command indicates that the selected
     * authentication mechanism is weaker than server policy permits for that
     * user.
     */
    public static final String AUTH_MECHANISM_WEAK = "534";

    /**
     * This response to the AUTH command indicates that the selected
     * authentication mechanism may only be used when the underlying SMTP
     * connection is encrypted.
     */
    public static final String AUTH_ENCRYPTION_REQUIRED = "538";

    /**
     * Requested action not taken: mailbox unavailable (e.g., mailbox not found,
     * no access, or command rejected for policy reasons)
     */
    public static final String MAILBOX_PERM_UNAVAILABLE = "550";

    /**
     * User not local; please try <forward-path> (See section 3.4)
     */
    public static final String USER_NOT_LOCAL = "551";

    /**
     * Requested mail action aborted: exceeded storage allocation
     */
    public static final String QUOTA_EXCEEDED = "552";

    /**
     * Requested action not taken: mailbox name not allowed (e.g., mailbox
     * syntax incorrect)
     */
    public static final String SYNTAX_ERROR_MAILBOX = "553";

    /**
     * Transaction failed (Or, in the case of a connection-opening response,
     * "No SMTP service here")
     */
    public static final String TRANSACTION_FAILED = "554";

}
