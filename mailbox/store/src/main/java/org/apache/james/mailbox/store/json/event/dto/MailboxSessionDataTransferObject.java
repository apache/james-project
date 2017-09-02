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

package org.apache.james.mailbox.store.json.event.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.store.SimpleMailboxSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;

public class MailboxSessionDataTransferObject {
    @JsonProperty()
    private long sessionId;
    @JsonProperty()
    private String username;
    @JsonProperty()
    private List<String> sharedSpaces;
    @JsonProperty()
    private String otherUserSpace;
    @JsonProperty()
    private char separator;
    @JsonProperty()
    private List<LocaleDataTransferObject> locales;
    @JsonProperty("r")
    private int sessionType;

    private static final Logger LOG = LoggerFactory.getLogger(MailboxSessionDataTransferObject.class);

    public MailboxSessionDataTransferObject() {

    }

    public MailboxSessionDataTransferObject(MailboxSession session) {
        username = session.getUser().getUserName();
        sharedSpaces = new ArrayList<>(session.getSharedSpaces());
        otherUserSpace = session.getOtherUsersSpace();
        separator = session.getPathDelimiter();
        sessionType = extractSessionType(session);
        sessionId = session.getSessionId();
        locales = Lists.transform(session.getUser().getLocalePreferences(), LocaleDataTransferObject::new);
    }

    @JsonIgnore
    public MailboxSession getMailboxSession() {
        return new SimpleMailboxSession(sessionId,
            username,
            "",
            retrieveLocales(),
            sharedSpaces,
            otherUserSpace,
            separator,
            retrieveSessionType());
    }

    private List<Locale> retrieveLocales() {
        if (locales != null) {
            return Lists.transform(locales, LocaleDataTransferObject::getLocale);
        } else {
            return new ArrayList<>();
        }
    }

    private MailboxSession.SessionType retrieveSessionType() {
        switch(this.sessionType) {
            case 0:
                return MailboxSession.SessionType.User;
            case 1:
                return MailboxSession.SessionType.System;
            default:
                LOG.warn("Unknown session type number while deserializing. Assuming user instead");
                return MailboxSession.SessionType.User;
        }
    }

    private int extractSessionType(MailboxSession session) {
        switch(session.getType()) {
            case User:
                return 0;
            case System:
                return 1;
            default:
                LOG.warn("Unknow session type while serializing mailbox session");
                return 0;
        }
    }

}