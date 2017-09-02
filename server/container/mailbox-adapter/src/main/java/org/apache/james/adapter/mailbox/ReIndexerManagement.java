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

package org.apache.james.adapter.mailbox;

import java.io.Closeable;
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.util.MDCBuilder;

import com.google.common.base.Throwables;

public class ReIndexerManagement implements ReIndexerManagementMBean {

    private ReIndexer reIndexer;

    @Inject
    public void setReIndexer(@Named("reindexer") ReIndexer reIndexer) {
        this.reIndexer = reIndexer;
    }

    @Override
    public void reIndex(String namespace, String user, String name) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "reIndex")
                     .build()) {
            reIndexer.reIndex(new MailboxPath(namespace, user, name));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void reIndex() throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "reIndex")
                     .build()) {
            reIndexer.reIndex();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
