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

package org.apache.james;

import org.apache.james.modules.CommonServicesModule;
import org.apache.james.modules.MailetProcessingWithoutCamelModule;
import org.apache.james.modules.ProtocolsModuleWithoutJMAP;
import org.apache.james.modules.data.JPADataJmapModule;
import org.apache.james.modules.data.JPADomainListModule;
import org.apache.james.modules.data.JPARecipientRewriteTableModule;
import org.apache.james.modules.data.JPAUsersRepositoryModule;
import org.apache.james.modules.mailbox.JPAMailboxModule;
import org.apache.james.modules.mailbox.LuceneSearchMailboxModule;
import org.apache.james.modules.server.ActiveMQQueueModule;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.server.QuotaModule;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class JPAJamesServerMain {
    public static final Module mainServerModule = Modules.combine(
            new CommonServicesModule(),
            new ProtocolsModuleWithoutJMAP(),
            new MailetProcessingWithoutCamelModule());

    public static final Module jpaServerModule = Modules.combine(
        new JPAMailboxModule(),
        new JPAUsersRepositoryModule(),
        new JPADomainListModule(),
        new JPARecipientRewriteTableModule(),
        new JPADataJmapModule(),
        new LuceneSearchMailboxModule(),
        new QuotaModule(),
        new ActiveMQQueueModule());


    public static void main(String[] args) throws Exception {
        GuiceJamesServer server = new GuiceJamesServer(mainServerModule)
                    .combineWith(jpaServerModule, new JMXServerModule());
        server.start();
    }

}
