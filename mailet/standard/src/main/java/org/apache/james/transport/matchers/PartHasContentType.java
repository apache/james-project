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
 
package org.apache.james.transport.matchers;

import java.util.Collection;

import jakarta.mail.MessagingException;
import jakarta.mail.Part;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.matchers.utils.MimeWalk;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.annotations.VisibleForTesting;

/**
 * <P>Checks if at least one attachment has a content type header which matches any
 * element of a comma-separated or space-separated list of content type masks.</P>
 *
 * <P>Syntax: <CODE>match="PartHasContentType=[-d]<I>masks</I>"</CODE></P>
 *
 * <P>The match is case insensitive.</P>
 * <P>Multiple content types name masks can be specified, e.g.: 'application/json,image/png'.</P>
 * <P>If '<CODE>-d</CODE>' is coded, some debug info will be logged.</P>
 */
public class PartHasContentType extends GenericMatcher {
    @VisibleForTesting
    MimeWalk.Configuration configuration = MimeWalk.Configuration.DEFAULT;

    @Override
    public void init() throws MessagingException {
        configuration = MimeWalk.Configuration.parse(getCondition());
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        return new MimeWalk(configuration, this::partMatch)
            .matchMail(mail);
    }

    private boolean partMatch(Part part) throws MessagingException {
        for (MimeWalk.Mask mask : configuration.masks()) {
            if (part.getContentType().startsWith(mask.getMatchString())) {
                return true; // matching file found
            }
        }
        return false;
    }
}

