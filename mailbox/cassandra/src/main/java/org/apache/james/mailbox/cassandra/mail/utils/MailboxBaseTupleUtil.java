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

package org.apache.james.mailbox.cassandra.mail.utils;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;

import com.datastax.oss.driver.api.core.data.UdtValue;

public class MailboxBaseTupleUtil {
    private final CassandraTypesProvider typesProvider;

    public MailboxBaseTupleUtil(CassandraTypesProvider typesProvider) {
        this.typesProvider = typesProvider;
    }

    public UdtValue createMailboxBaseUDT(String namespace, Username user) {
        return typesProvider.getDefinedUserType(CassandraMailboxTable.MAILBOX_BASE)
            .newValue()
            .setString(CassandraMailboxTable.MailboxBase.NAMESPACE, namespace)
            .setString(CassandraMailboxTable.MailboxBase.USER, user.asString());
    }
}
