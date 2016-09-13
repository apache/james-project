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

import com.google.common.base.MoreObjects;

public class InitParametersSerializer {

    public static String serialize(InitParameters initParameters) {
        return MoreObjects.toStringHelper(initParameters.getClass())
                .add("static", initParameters.isStatic())
                .add("passThrough", initParameters.getPassThrough())
                .add("fakeDomainCheck", initParameters.getFakeDomainCheck())
                .add("sender", initParameters.getSender())
                .add("replyTo", initParameters.getReplyTo())
                .add("reversePath", initParameters.getReversePath())
                .add("message", initParameters.getMessage())
                .add("recipients", initParameters.getRecipients())
                .add("subject", initParameters.getSubject())
                .add("subjectPrefix", initParameters.getSubjectPrefix())
                .add("apparentlyTo", initParameters.getTo())
                .add("attachError", initParameters.isAttachError())
                .add("isReply", initParameters.isReply())
                .add("attachmentType", initParameters.getAttachmentType())
                .add("inLineType", initParameters.getInLineType())
                .toString();
    }
}
