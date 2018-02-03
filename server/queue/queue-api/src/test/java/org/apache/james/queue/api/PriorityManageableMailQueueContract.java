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

package org.apache.james.queue.api;

import static org.apache.james.queue.api.Mails.defaultMail;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.mailet.Mail;
import org.junit.jupiter.api.Test;

public interface PriorityManageableMailQueueContract extends ManageableMailQueueContract, PriorityMailQueueContract {

    ManageableMailQueue getManageableMailQueue();

    @Test
    default void browseShouldBeOrderedByPriority() throws Exception {
        getManageableMailQueue().enQueue(defaultMail()
            .name("name3")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 3)
            .build());

        getManageableMailQueue().enQueue(defaultMail()
            .name("name9")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 9)
            .build());

        getManageableMailQueue().enQueue(defaultMail()
            .name("name1")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 1)
            .build());

        getManageableMailQueue().enQueue(defaultMail()
            .name("name8")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 8)
            .build());

        getManageableMailQueue().enQueue(defaultMail()
            .name("name6")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 6)
            .build());

        getManageableMailQueue().enQueue(defaultMail()
            .name("name0")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 0)
            .build());

        getManageableMailQueue().enQueue(defaultMail()
            .name("name7")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 7)
            .build());

        getManageableMailQueue().enQueue(defaultMail()
            .name("name4")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 4)
            .build());

        getManageableMailQueue().enQueue(defaultMail()
            .name("name2")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 2)
            .build());

        getManageableMailQueue().enQueue(defaultMail()
            .name("name5")
            .attribute(MailPrioritySupport.MAIL_PRIORITY, 5)
            .build());

        assertThat(getManageableMailQueue().browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getMail)
            .extracting(Mail::getName)
            .containsExactly("name9", "name8", "name7", "name6", "name5", "name4", "name3", "name2", "name1", "name0");
    }
}
