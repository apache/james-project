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



package org.apache.james.protocols.smtp.dsn;


/**
 * Constants and getters for RFC 3463 Enhanced Mail System Status Codes
 *
 */
public class DSNStatus {
    // status code classes
    /**
     * Success
     */
    public static final int SUCCESS = 2;

    /**
     * Persistent Transient Failure
     */
    public static final int TRANSIENT = 4;

    /**
     * Permanent Failure
     */
    public static final int PERMANENT = 5;

    // subjects and details

    /**
     * Other or Undefined Status
     */
    public static final int UNDEFINED = 0;

    /**
     * Other undefined status
     */
    public static final String UNDEFINED_STATUS = "0.0";

    /**
     * Addressing Status
     */
    public static final int ADDRESS = 1;

    /**
     * Other address status
     */
    public static final String ADDRESS_OTHER = "1.0";

    /**
     * Bad destination mailbox address
     */
    public static final String ADDRESS_MAILBOX = "1.1";

    /**
     * Bad destination system address
     */
    public static final String ADDRESS_SYSTEM = "1.2";

    /**
     * Bad destination mailbox address syntax
     */
    public static final String ADDRESS_SYNTAX = "1.3";

    /**
     * Destination mailbox address ambiguous
     */
    public static final String ADDRESS_AMBIGUOUS = "1.4";

    /**
     * Destination Address valid
     */
    public static final String ADDRESS_VALID = "1.5";

    /**
     * Destimation mailbox has moved, no forwarding address
     */
    public static final String ADDRESS_MOVED = "1.6";

    /**
     * Bad sender's mailbox address syntax
     */
    public static final String ADDRESS_SYNTAX_SENDER = "1.7";

    /**
     * Bad sender's system address
     */
    public static final String ADDRESS_SYSTEM_SENDER = "1.8";


    /**
     * Mailbox Status
     */
    public static final int MAILBOX = 2;

    /**
     * Other or Undefined Mailbox Status
     */
    public static final String MAILBOX_OTHER = "2.0";

    /**
     * Mailbox disabled, not accepting messages
     */
    public static final String MAILBOX_DISABLED = "2.1";

    /**
     * Mailbox full
     */
    public static final String MAILBOX_FULL = "2.2";

    /**
     * Message length exceeds administrative limit
     */
    public static final String MAILBOX_MSG_TOO_BIG = "2.3";

    /**
     * Mailing list expansion problem
     */
    public static final String MAILBOX_LIST_EXPANSION = "2.4";


    /**
     * Mail System Status
     */
    public static final int SYSTEM = 3;

    /**
     * Other or undefined mail system status
     */
    public static final String SYSTEM_OTHER = "3.0";

    /**
     * Mail system full
     */
    public static final String SYSTEM_FULL = "3.1";

    /**
     * System not accepting messages
     */
    public static final String SYSTEM_NOT_ACCEPTING = "3.2";

    /**
     * System not capable of selected features
     */
    public static final String SYSTEM_NOT_CAPABLE = "3.3";

    /**
     * Message too big for system
     */
    public static final String SYSTEM_MSG_TOO_BIG = "3.4";

    /**
     * System incorrectly configured
     */
    public static final String SYSTEM_CFG_ERROR = "3.5";


    /**
     * Network and Routing Status
     */
    public static final int NETWORK = 4;

    /**
     * Other or undefined network or routing status
     */
    public static final String NETWORK_OTHER = "4.0";

    /**
     * No answer form host
     */
    public static final String NETWORK_NO_ANSWER = "4.1";

    /**
     * Bad Connection
     */
    public static final String NETWORK_CONNECTION = "4.2";

    /**
     * Directory server failure
     */
    public static final String NETWORK_DIR_SERVER = "4.3";

    /**
     * Unable to route
     */
    public static final String NETWORK_ROUTE = "4.4";

    /**
     * Mail system congestion
     */
    public static final String NETWORK_CONGESTION = "4.5";

    /**
     * Routing loop detected
     */
    public static final String NETWORK_LOOP = "4.6";

    /**
     * Delivery time expired
     */
    public static final String NETWORK_EXPIRED = "4.7";


    /**
     * Mail Delivery Protocol Status
     */
    public static final int DELIVERY = 5;

    /**
     * Other or undefined (SMTP) protocol status
     */
    public static final String DELIVERY_OTHER = "5.0";

    /**
     * Invalid command
     */
    public static final String DELIVERY_INVALID_CMD = "5.1";

    /**
     * Syntax error
     */
    public static final String DELIVERY_SYNTAX = "5.2";

    /**
     * Too many recipients
     */
    public static final String DELIVERY_TOO_MANY_REC = "5.3";

    /**
     * Invalid command arguments
     */
    public static final String DELIVERY_INVALID_ARG = "5.4";

    /**
     * Wrong protocol version
     */
    public static final String DELIVERY_VERSION = "5.5";


    /**
     * Message Content or Media Status
     */
    public static final int CONTENT = 6;

    /**
     * Other or undefined media error
     */
    public static final String CONTENT_OTHER = "6.0";

    /**
     * Media not supported
     */
    public static final String CONTENT_UNSUPPORTED = "6.1";

    /**
     * Conversion required and prohibited
     */
    public static final String CONTENT_CONVERSION_NOT_ALLOWED = "6.2";

    /**
     * Conversion required, but not supported
     */
    public static final String CONTENT_CONVERSION_NOT_SUPPORTED = "6.3";

    /**
     * Conversion with loss performed
     */
    public static final String CONTENT_CONVERSION_LOSS = "6.4";

    /**
     * Conversion failed
     */
    public static final String CONTENT_CONVERSION_FAILED = "6.5";


    /**
     * Security or Policy Status
     */
    public static final int SECURITY = 7;

    /**
     * Other or undefined security status
     */
    public static final String SECURITY_OTHER = "7.0";

    /**
     * Delivery not authorized, message refused
     */
    public static final String SECURITY_AUTH = "7.1";

    /**
     * Mailing list expansion prohibited
     */
    public static final String SECURITY_LIST_EXP = "7.2";

    /**
     * Security conversion required, but not possible
     */
    public static final String SECURITY_CONVERSION = "7.3";

    /**
     * Security features not supported
     */
    public static final String SECURITY_UNSUPPORTED = "7.4";

    /**
     * Cryptographic failure
     */
    public static final String SECURITY_CRYPT_FAIL = "7.5";

    /**
     * Cryptographic algorithm not supported
     */
    public static final String SECURITY_CRYPT_ALGO = "7.6";

    /**
     * Message integrity failure
     */
    public static final String SECURITY_INTEGRITY = "7.7";


    // get methods

    public static String getStatus(int type, String detail) {
        return type + "." + detail;
    }

    public static String getStatus(int type, int subject, int detail) {
        return type + "." + subject + "." + detail;
    }
}
