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

package org.apache.james.jmap.memory.cucumber;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(features = {
    "classpath:cucumber/sharing/SharingParentMailboxWithAndWithoutChildren.feature",
    "classpath:cucumber/sharing/SharingChildrenWithoutSharingParent.feature",
    "classpath:cucumber/sharing/GetMessageAndSharing.feature",
    "classpath:cucumber/sharing/SharingMailboxWithOtherDomain.feature",
    "classpath:cucumber/sharing/MailboxCreationAndSharing.feature",
    "classpath:cucumber/sharing/MailboxDeletionAndSharing.feature",
    "classpath:cucumber/sharing/SetMessagesOnSharedMailbox.feature",
    "classpath:cucumber/sharing/DownloadAndSharing.feature",
    "classpath:cucumber/sharing/KeywordsConsistencyOnDelegationMailbox.feature",
    "classpath:cucumber/sharing/GetMessageListAndSharing.feature",
    "classpath:cucumber/sharing/MoveMessageAndSharing.feature",
    "classpath:cucumber/sharing/MoveMailboxAndSharing.feature",
    "classpath:cucumber/sharing/SetFlagAndSharing.feature",
    "classpath:cucumber/sharing/RenamingMailboxAndSharing.feature",
    "classpath:cucumber/sharing/CopyAndSharing.feature" },
    glue = { "org.apache.james.jmap.draft.methods.integration", "org.apache.james.jmap.memory.cucumber" },
    tags = {"not @Ignore"},
    strict = true)
public class MemorySharingTest {
}
