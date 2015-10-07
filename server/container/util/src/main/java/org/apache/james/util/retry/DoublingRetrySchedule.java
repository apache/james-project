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

package org.apache.james.util.retry;

import org.apache.james.util.retry.api.RetrySchedule;


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
    
    private long _startInterval = 0;
    private long _maxInterval = 0;
    private long _multiplier = 1;

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
        _startInterval = Math.max(0, startInterval);
        _maxInterval = Math.max(0, maxInterval);
        _multiplier = Math.max(1, multiplier);
    }

    /**
     */
    @Override
    public long getInterval(int index) {
        if (_startInterval > 0)
        {
            return getInterval(index, _startInterval);
        }
        return index == 0 ? 0 : getInterval(index - 1, 1);
    }
    
    private long getInterval(int index, long startInterval) {
        return _multiplier * Math.min((long) (startInterval * Math.pow(2, index)), _maxInterval);
    }    

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("DoublingRetrySchedule [startInterval=").append(_startInterval).append(
                ", maxInterval=").append(_maxInterval).append(", multiplier=").append(_multiplier).append("]");
        return builder.toString();
    }

}
