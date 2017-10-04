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

package org.apache.james.mailbox.model.search;

public interface MailboxNameExpression {
    /**
     * Use this wildcard to match every char including the hierarchy delimiter
     */
    char FREEWILDCARD = '*';

    /**
     * Use this wildcard to match every char except the hierarchy delimiter
     */
    char LOCALWILDCARD = '%';

    /**
     * Is the given name a match for this expression?
     *
     * @param name
     *            name to be matched
     * @return true if the given name matches this expression, false otherwise
     */
    boolean isExpressionMatch(String name);

    /**
     * Get combined name formed by adding the expression to the base using the
     * given hierarchy delimiter. Note that the wildcards are retained in the
     * combined name.
     *
     * @return {@link #getBase()} combined with {@link #getExpression()},
     *         notnull
     */
    String getCombinedName();

    /**
     * Is this expression wild?
     *
     * @return true if wildcard contained, false otherwise
     */
    boolean isWild();

    /**
     * Gets wildcard character that matches any series of characters.
     *
     * @return the freeWildcard
     */
    default char getFreeWildcard() {
        return FREEWILDCARD;
    }

    /**
     * Gets wildcard character that matches any series of characters excluding
     * hierarchy delimiters. Effectively, this means that it matches any
     * sequence within a name part.
     *
     * @return the localWildcard
     */
    default char getLocalWildcard() {
        return LOCALWILDCARD;
    }
}
