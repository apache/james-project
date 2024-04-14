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

package org.apache.james.app.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.james.container.spring.context.JamesServerApplicationContext;
import org.apache.james.events.InVMEventBus;
import org.apache.james.mailbox.lucene.search.LuceneMessageSearchIndex;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JamesSpringContextTest {
    private JamesServerApplicationContext context;

    @BeforeEach
    void setup() throws Exception {
        File accessFile = new File("../conf/jmxremote.access");
        accessFile.getParentFile().mkdirs();
        accessFile.createNewFile();
        try (FileOutputStream out = new FileOutputStream(accessFile)) {
            out.write("james-admin readwrite\r\n".getBytes());
            out.flush();
        }
        File passwordFile = new File("../conf/jmxremote.password");
        passwordFile.createNewFile();
        try (FileOutputStream out = new FileOutputStream(passwordFile)) {
            out.write("james-admin changeme\r\n".getBytes());
            out.flush();
        }
        context = new JamesServerApplicationContext(new String[] { "META-INF/org/apache/james/spring-server.xml" });
        context.registerShutdownHook();
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
        context.close();
    }

    @Test
    void springShouldLoadAndAddOnlyOneQuotaUpdaterListener() {
        InVMEventBus eventBus = context.getBean(InVMEventBus.class);

        assertThat(eventBus.registeredGroups())
            .containsExactlyInAnyOrder(MailboxAnnotationListener.GROUP, LuceneMessageSearchIndex.GROUP, ListeningCurrentQuotaUpdater.GROUP);
    }
}
