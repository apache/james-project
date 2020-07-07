/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.user.ldap.retry;

import org.apache.james.user.ldap.retry.api.RetrySchedule;


/**
 * <code>DoublingRetrySchedule</code> is an implementation of <code>RetrySchedule</code> that
 * returns the lesser of an interval that is double the proceeding interval and the maximum interval.
 * 
 * <p>When the initial interval is 0, the next interval is 1.
 * 
 * <p>A multiplier can be specified to scale the results.
 */
/**
 * <code>DoublingRetrySchedule</code>
 */
public class DoublingRetrySchedule implements RetrySchedule {
    
    private long startInterval = 0;
    private long maxInterval = 0;
    private long multiplier = 1;

    /**
     * Creates a new instance of DoublingRetrySchedule.
     *
     */
    private DoublingRetrySchedule() {
    }
    
    /**
     * Creates a new instance of DoublingRetrySchedule.
     *
     * @param startInterval
     *      The interval for an index of 0
     * @param maxInterval
     *      The maximum interval for any index
     */
    public DoublingRetrySchedule(long startInterval, long maxInterval) {
        this(startInterval, maxInterval, 1);
    }
    
    /**
     * Creates a new instance of DoublingRetrySchedule.
     *
     * @param startInterval
     *      The interval for an index of 0
     * @param maxInterval
     *      The maximum interval for any index
     * @param multiplier
     *      The multiplier to apply to the result
     */
    public DoublingRetrySchedule(long startInterval, long maxInterval, int multiplier) {
        this();
        this.startInterval = Math.max(0, startInterval);
        this.maxInterval = Math.max(0, maxInterval);
        this.multiplier = Math.max(1, multiplier);
    }

    @Override
    public long getInterval(int index) {
        if (startInterval > 0) {
            return getInterval(index, startInterval);
        }
        return index == 0 ? 0 : getInterval(index - 1, 1);
    }
    
    private long getInterval(int index, long startInterval) {
        return multiplier * Math.min((long) (startInterval * Math.pow(2, index)), maxInterval);
    }    

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("DoublingRetrySchedule [startInterval=").append(startInterval).append(
                ", maxInterval=").append(maxInterval).append(", multiplier=").append(multiplier).append("]");
        return builder.toString();
    }

}
