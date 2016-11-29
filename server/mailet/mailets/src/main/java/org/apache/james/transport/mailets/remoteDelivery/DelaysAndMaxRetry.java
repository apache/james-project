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

package org.apache.james.transport.mailets.remoteDelivery;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import javax.mail.MessagingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

public class DelaysAndMaxRetry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelaysAndMaxRetry.class);

    public static DelaysAndMaxRetry from(int intendedMaxRetries, String delaysAsString) throws MessagingException {
        // Create list of Delay Times.
        ArrayList<Delay> delayTimesList = createDelayList(delaysAsString);

        // Check consistency of 'maxRetries' with delayTimesList attempts.
        int totalAttempts = calcTotalAttempts(delayTimesList);

        // If inconsistency found, fix it.
        if (totalAttempts > intendedMaxRetries) {
            LOGGER.warn("Total number of delayTime attempts exceeds maxRetries specified. " + " Increasing maxRetries from " + intendedMaxRetries + " to " + totalAttempts);
            return new DelaysAndMaxRetry(totalAttempts, delayTimesList);
        } else {
            int extra = intendedMaxRetries - totalAttempts;
            if (extra != 0) {
                LOGGER.warn("maxRetries is larger than total number of attempts specified.  " + "Increasing last delayTime with " + extra + " attempts ");

                // Add extra attempts to the last delayTime.
                if (delayTimesList.size() != 0) {
                    // Get the last delayTime.
                    Delay delay = delayTimesList.get(delayTimesList.size() - 1);

                    // Increase no. of attempts.
                    delay.setAttempts(delay.getAttempts() + extra);
                    LOGGER.warn("Delay of " + delay.getDelayTime() + " msecs is now attempted: " + delay.getAttempts() + " times");
                } else {
                    throw new MessagingException("No delaytimes, cannot continue");
                }
            }
            return new DelaysAndMaxRetry(intendedMaxRetries, delayTimesList);
        }
    }

    private static ArrayList<Delay> createDelayList(String delaysAsString) {
        ArrayList<Delay> delayTimesList = new ArrayList<Delay>();
        try {
            if (delaysAsString != null) {

                // Split on commas
                StringTokenizer st = new StringTokenizer(delaysAsString, ",");
                while (st.hasMoreTokens()) {
                    String delayTime = st.nextToken();
                    delayTimesList.add(new Delay(delayTime));
                }
            } else {
                // Use default delayTime.
                delayTimesList.add(new Delay());
            }
        } catch (Exception e) {
            LOGGER.warn("Invalid delayTime setting: " + delaysAsString);
        }
        return delayTimesList;
    }

    /**
     * Calculates Total no. of attempts for the specified delayList.
     *
     * @param delayList list of 'Delay' objects
     * @return total no. of retry attempts
     */
    private static int calcTotalAttempts(List<Delay> delayList) {
        int sum = 0;
        for (Delay delay : delayList) {
            sum += delay.getAttempts();
        }
        return sum;
    }

    private final int maxRetries;
    private final List<Delay> delays;

    @VisibleForTesting
    DelaysAndMaxRetry(int maxRetries, List<Delay> delays) {
        this.maxRetries = maxRetries;
        this.delays = ImmutableList.copyOf(delays);
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * <p>
     * This method expands an ArrayList containing Delay objects into an array
     * holding the only delaytime in the order.
     * </p>
     * <p/>
     * So if the list has 2 Delay objects the first having attempts=2 and
     * delaytime 4000 the second having attempts=1 and delaytime=300000 will be
     * expanded into this array:
     * <p/>
     * <pre>
     * long[0] = 4000
     * long[1] = 4000
     * long[2] = 300000
     * </pre>
     *
     * @param list the list to expand
     * @return the expanded list
     */
    public long[] getExpendedDelays() {
        long[] delaysAsLong = new long[calcTotalAttempts(delays)];
        Iterator<Delay> i = delays.iterator();
        int idx = 0;
        while (i.hasNext()) {
            Delay delay = i.next();
            for (int j = 0; j < delay.getAttempts(); j++) {
                delaysAsLong[idx++] = delay.getDelayTime();
            }
        }
        return delaysAsLong;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DelaysAndMaxRetry) {
            DelaysAndMaxRetry that = (DelaysAndMaxRetry) o;
            return Objects.equal(this.maxRetries, that.maxRetries)
                && Objects.equal(this.delays, that.delays);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(maxRetries, delays);
    }
}
