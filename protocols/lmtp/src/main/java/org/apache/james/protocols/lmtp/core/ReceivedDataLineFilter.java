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
package org.apache.james.protocols.lmtp.core;

import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.ReceivedHeaderGenerator;

/**
 * {@link ReceivedDataLineFilter} which will add the Received header to the message
 */
public class ReceivedDataLineFilter extends org.apache.james.protocols.smtp.core.ReceivedDataLineFilter {

    private static final String SERVICE_TYPE = "LMTP";
    private static final String SERVICE_TYPE_AUTH = "LMTPA";
    private static final String SERVICE_TYPE_SSL = "LMTPS";
    private static final String SERVICE_TYPE_SSL_AUTH = "LMTPSA";

    public ReceivedDataLineFilter() {
        super(new ReceivedHeaderGenerator() {

            /**
             * Always returns <code>LMTP</code>
             */
            @Override
            protected String getServiceType(SMTPSession session, String heloMode) {
                // Not successful auth
                if (session.getUsername() == null) {
                    if (session.isTLSStarted()) {
                        return SERVICE_TYPE_SSL;
                    } else {
                        return SERVICE_TYPE;
                    }
                } else {
                    // See RFC3848:
                    // The new keyword "ESMTPA" indicates the use of ESMTP when the SMTP
                    // AUTH [3] extension is also used and authentication is successfully achieved.
                    if (session.isTLSStarted()) {
                        return SERVICE_TYPE_SSL_AUTH;
                    } else {
                        return SERVICE_TYPE_AUTH;
                    }
                }
            }
        });
    }

}
