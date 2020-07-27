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

package org.apache.james.metrics.api;

public interface Metric {

    void increment();

    void decrement();

    void add(int value);

    void remove(int value);

    /**
     * @return A long guaranteed to be positive.
     *
     * Implementation might be doing strict validation (throwing on negative value) or lenient (log and sanitize to 0)
     */
    long getCount();

    /**
     * Moving average.
     *
     * Period of the moving average is implementation defined.
     *
     * Default to count (naive implementation with period starting at boot time)
     */
    default double movingAverage() {
        return Long.valueOf(getCount()).doubleValue();
    }
}
