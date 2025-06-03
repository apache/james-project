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

package org.apache.james.mailbox.opensearch;

import org.apache.james.backends.opensearch.IndexCreationFactory;
import org.apache.james.backends.opensearch.IndexName;
import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.opensearch.ReadAliasName;
import org.apache.james.backends.opensearch.WriteAliasName;

public class MailboxIndexCreationUtil {

    public static ReactorOpenSearchClient prepareClient(ReactorOpenSearchClient client,
                                                        ReadAliasName readAlias,
                                                        WriteAliasName writeAlias,
                                                        IndexName indexName,
                                                        OpenSearchConfiguration configuration,
                                                        MailboxMappingFactory mailboxMappingFactory) {
            return new IndexCreationFactory(configuration)
                .useIndex(indexName)
                .addAlias(readAlias)
                .addAlias(writeAlias)
                .createIndexAndAliases(client, mailboxMappingFactory.getMappingContent());
    }

    public static ReactorOpenSearchClient prepareDefaultClient(ReactorOpenSearchClient client, OpenSearchConfiguration configuration) {
        return prepareClient(client,
            MailboxOpenSearchConstants.DEFAULT_MAILBOX_READ_ALIAS,
            MailboxOpenSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS,
            MailboxOpenSearchConstants.DEFAULT_MAILBOX_INDEX,
            configuration,
            new DefaultMailboxMappingFactory());
    }
}
