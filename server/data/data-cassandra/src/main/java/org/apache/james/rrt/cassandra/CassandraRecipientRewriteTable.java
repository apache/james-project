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
package org.apache.james.rrt.cassandra;

import java.util.Collection;
import java.util.Map;

import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;

public class CassandraRecipientRewriteTable extends AbstractRecipientRewriteTable {

    @Override
    protected void addMappingInternal(String user, String domain, String mapping) throws RecipientRewriteTableException {
    }

    @Override
    protected void removeMappingInternal(String user, String domain, String mapping) throws RecipientRewriteTableException {
    }

    @Override
    protected Collection<String> getUserDomainMappingsInternal(String user, String domain) throws RecipientRewriteTableException {
        return null;
    }

    @Override
    protected Map<String, Collection<String>> getAllMappingsInternal() throws RecipientRewriteTableException {
        return null;
    }

    @Override
    protected String mapAddressInternal(String user, String domain) throws RecipientRewriteTableException {
        return null;
    }

}
