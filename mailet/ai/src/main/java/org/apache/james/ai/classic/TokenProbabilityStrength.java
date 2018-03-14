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

package org.apache.james.ai.classic;

/**
 * Inner class for managing Token Probability Strengths during the
 * computeSpamProbability phase.
 * 
 * By probability <i>strength</i> we mean the absolute distance of a
 * probability from the middle value 0.5.
 * 
 * It implements Comparable so that it's sorting is automatic.
 */
class TokenProbabilityStrength implements Comparable<TokenProbabilityStrength> {
    /**
     * Message token.
     */
    String token = null;

    /**
     * Token's computed probability strength.
     */
    double strength = Math.abs(0.5 - BayesianAnalyzer.DEFAULT_TOKEN_PROBABILITY);

    /**
     * Force the natural sort order for this object to be high-to-low.
     * 
     * @param anotherTokenProbabilityStrength
     *            A TokenProbabilityStrength instance to compare this
     *            instance with.
     * 
     * @return The result of the comparison (before, equal, after).
     */
    @Override
    public final int compareTo(TokenProbabilityStrength anotherTokenProbabilityStrength) {
        int result = (int) ((anotherTokenProbabilityStrength.strength - strength) * 1000000);
        if (result == 0) {
            return this.token.compareTo(anotherTokenProbabilityStrength.token);
        } else {
            return result;
        }
    }

    /**
     * Simple toString () implementation mostly for debugging purposes.
     * 
     * @return String representation of this object.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder(30);

        sb.append(token).append("=").append(strength);

        return sb.toString();
    }
}