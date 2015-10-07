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
package org.apache.james.mailbox.model;

/**
 * A {@link Quota} restriction
 */
public interface Quota {

    /**
     * Unlimited value
     */
    long UNLIMITED = -1;

    /**
     * Value not known
     */
    long UNKNOWN = Long.MIN_VALUE;

    /**
     * Return the maximum value for the {@link Quota}
     *
     * @return max
     */
    long getMax();

    /**
     * Return the currently used for the {@link Quota}
     *
     * @return used
     */
    long getUsed();

    /**
     *  Adds the value to the quota.
     */
    void addValueToQuota(long value);

    /**
     * Tells us if the quota is reached
     *
     * @return True if the user over uses the resource of this quota
     */
    boolean isOverQuota();

}