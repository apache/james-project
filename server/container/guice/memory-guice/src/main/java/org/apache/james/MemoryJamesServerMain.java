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

import org.apache.james.jmap.send.PostDequeueDecoratorFactory;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.data.MemoryDataJmapModule;
import org.apache.james.modules.data.MemoryDataModule;
import org.apache.james.modules.mailbox.MemoryMailboxModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.JMAPServerModule;
import org.apache.james.modules.protocols.LMTPServerModule;
import org.apache.james.modules.protocols.ManageSieveServerModule;
import org.apache.james.modules.protocols.POP3ServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.server.MailboxRoutesModule;
import org.apache.james.modules.server.MemoryMailQueueModule;
import org.apache.james.modules.server.SwaggerRoutesModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class MemoryJamesServerMain {

    public static final Module webadmin = Modules.combine(
        new WebAdminServerModule(),
        new DataRoutesModules(),
        new MailboxRoutesModule(),
        new SwaggerRoutesModule());

    public static final Module protocols = Modules.combine(
        new IMAPServerModule(),
        new LMTPServerModule(),
        new ManageSieveServerModule(),
        new POP3ServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule());

    public static final Module jmap = Modules.combine(
        new MemoryDataJmapModule(),
        new JMAPServerModule(),
        binder -> binder.bind(MailQueueItemDecoratorFactory.class).to(PostDequeueDecoratorFactory.class));

    public static final Module inMemoryServerModule = Modules.combine(
        new MemoryDataModule(),
        new MemoryMailboxModule(),
        new MemoryMailQueueModule(),
        new MailboxModule());

    public static final Module inMemoryServerAggregateModule = Modules.combine(
        inMemoryServerModule,
        protocols,
        jmap,
        webadmin);

    public static void main(String[] args) throws Exception {
        new GuiceJamesServer()
            .combineWith(inMemoryServerAggregateModule, new JMXServerModule())
            .start();
    }

}
