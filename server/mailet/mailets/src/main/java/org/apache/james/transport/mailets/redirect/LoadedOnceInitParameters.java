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

package org.apache.james.transport.mailets.redirect;

import com.google.common.base.Optional;

public class LoadedOnceInitParameters implements InitParameters {

    public static LoadedOnceInitParameters from(InitParameters initParameters) {
        return new LoadedOnceInitParameters(true,
                initParameters.getPassThrough(),
                initParameters.getFakeDomainCheck(),
                initParameters.getInLineType(),
                initParameters.getAttachmentType(),
                initParameters.getMessage(),
                initParameters.getSubject(),
                initParameters.getSubjectPrefix(),
                initParameters.isAttachError(),
                initParameters.isReply(),
                initParameters.getRecipients(),
                initParameters.getTo(),
                initParameters.getReversePath(),
                initParameters.getSender(),
                initParameters.getReplyTo(),
                initParameters.isDebug());
    }

    private final boolean isStatic;
    private final boolean passThrough;
    private final boolean fakeDomainCheck;
    private final TypeCode inline;
    private final TypeCode attachment;
    private final String message;
    private final String subject;
    private final String prefix;
    private final boolean attachError;
    private final boolean isReply;
    private final Optional<String> recipients;
    private final Optional<String> to;
    private final Optional<String> reversePath;
    private final Optional<String> sender;
    private final Optional<String> replyTo;
    private final boolean debug;

    private LoadedOnceInitParameters(boolean isStatic, boolean passThrough, boolean fakeDomainCheck, TypeCode inline, TypeCode attachment, String message,
            String subject, String prefix, boolean attachError, boolean isReply, 
            Optional<String> recipients, Optional<String> to, Optional<String> reversePath, Optional<String> sender, Optional<String> replyTo, boolean debug) {
        this.isStatic = isStatic;
        this.passThrough = passThrough;
        this.fakeDomainCheck = fakeDomainCheck;
        this.inline = inline;
        this.attachment = attachment;
        this.message = message;
        this.subject = subject;
        this.prefix = prefix;
        this.attachError = attachError;
        this.isReply = isReply;
        this.recipients = recipients;
        this.to = to;
        this.reversePath = reversePath;
        this.sender = sender;
        this.replyTo = replyTo;
        this.debug = debug;
    }

    @Override
    public boolean getPassThrough() {
        return passThrough;
    }

    @Override
    public boolean getFakeDomainCheck() {
        return fakeDomainCheck;
    }

    @Override
    public TypeCode getInLineType() {
        return inline;
    }

    @Override
    public TypeCode getAttachmentType() {
        return attachment;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getSubject() {
        return subject;
    }

    @Override
    public String getSubjectPrefix() {
        return prefix;
    }

    @Override
    public boolean isAttachError() {
        return attachError;
    }

    @Override
    public boolean isReply() {
        return isReply;
    }

    @Override
    public Optional<String> getRecipients() {
        return recipients;
    }

    @Override
    public Optional<String> getTo() {
        return to;
    }

    @Override
    public Optional<String> getReversePath() {
        return reversePath;
    }

    @Override
    public Optional<String> getSender() {
        return sender;
    }

    @Override
    public Optional<String> getReplyTo() {
        return replyTo;
    }

    @Override
    public boolean isDebug() {
        return debug;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    @Override
    public String asString() {
        return InitParametersSerializer.serialize(this);
    }
}
