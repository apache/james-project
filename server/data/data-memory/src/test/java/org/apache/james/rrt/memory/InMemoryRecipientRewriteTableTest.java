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

package org.apache.james.rrt.memory;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTableTest;

public class InMemoryRecipientRewriteTableTest extends AbstractRecipientRewriteTableTest {

    @Override
    protected AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception {
        AbstractRecipientRewriteTable rrt = new MemoryRecipientRewriteTable();
        rrt.configure(new DefaultConfigurationBuilder());
        return rrt;
    }

    @Override
    protected boolean addMapping(String user, String domain, String mapping, int type) throws RecipientRewriteTableException {
        try {
            switch (type) {
            case ERROR_TYPE:
                virtualUserTable.addErrorMapping(user, domain, mapping);
                return true;
            case REGEX_TYPE:
                virtualUserTable.addRegexMapping(user, domain, mapping);
                return true;
            case ADDRESS_TYPE:
                virtualUserTable.addAddressMapping(user, domain, mapping);
                return true;
            case ALIASDOMAIN_TYPE:
                virtualUserTable.addAliasDomainMapping(domain, mapping);
                return true;
            default:
                return false;
            }
        } catch (RecipientRewriteTableException e) {
            return false;
        }
    }

    @Override
    protected boolean removeMapping(String user, String domain, String mapping, int type) throws RecipientRewriteTableException {
        try {
            switch (type) {
            case ERROR_TYPE:
                virtualUserTable.removeErrorMapping(user, domain, mapping);
                return true;
            case REGEX_TYPE:
                virtualUserTable.removeRegexMapping(user, domain, mapping);
                return true;
            case ADDRESS_TYPE:
                virtualUserTable.removeAddressMapping(user, domain, mapping);
                return true;
            case ALIASDOMAIN_TYPE:
                virtualUserTable.removeAliasDomainMapping(domain, mapping);
                return true;
            default:
                return false;
            }
        } catch (RecipientRewriteTableException e) {
            return false;
        }
    }

}
