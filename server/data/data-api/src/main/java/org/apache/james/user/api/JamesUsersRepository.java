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

package org.apache.james.user.api;

import org.apache.james.rrt.api.RecipientRewriteTable;

/**
 * @deprecated Use {@link UsersRepository}
 */
@Deprecated
public interface JamesUsersRepository extends UsersRepository, RecipientRewriteTable {

    /**
     * enable/disable aliases in case of JamesUsers
     * 
     * @param enableAliases
     *            enable
     */
    void setEnableAliases(boolean enableAliases);

    /**
     * enable/disable aliases in case of JamesUsers
     * 
     * @param enableForwarding
     *            enable
     */
    void setEnableForwarding(boolean enableForwarding);

    /**
     * set case sensitive/insensitive operations
     * 
     * @param ignoreCase
     *            ignore
     */
    void setIgnoreCase(boolean ignoreCase);

}
