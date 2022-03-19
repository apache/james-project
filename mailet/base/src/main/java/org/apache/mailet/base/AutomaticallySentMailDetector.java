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

import jakarta.mail.MessagingException;

import org.apache.mailet.Mail;

public interface AutomaticallySentMailDetector {

    String AUTO_SUBMITTED_HEADER = "Auto-Submitted";
    String AUTO_REPLIED_VALUE = "auto-replied";
    String AUTO_GENERATED_VALUE = "auto-generated";
    String AUTO_NOTIFIED_VALUE = "auto-notified";

    boolean isAutomaticallySent(Mail mail) throws MessagingException;

    boolean isMailingList(Mail mail) throws MessagingException;

    boolean isAutoSubmitted(Mail mail) throws MessagingException;

    boolean isMdnSentAutomatically(Mail mail) throws MessagingException;
}
