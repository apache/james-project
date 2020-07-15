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

package org.apache.james.utils;

import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.util.Pair;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;

public class DiscreteDistribution<T> {

    public static class DistributionEntry<V> {
        private final V value;
        private final double associatedProbability;

        public DistributionEntry(V value, double associatedProbability) {
            Preconditions.checkArgument(value != null);
            Preconditions.checkArgument(associatedProbability >= 0, "The occurence count needs to be positive");
            this.value = value;
            this.associatedProbability = associatedProbability;
        }

        public V getValue() {
            return value;
        }

        public double getAssociatedProbability() {
            return associatedProbability;
        }

        public Pair<V, Double> toPair() {
            return new Pair<>(value, associatedProbability);
        }
    }

    public static <T> DiscreteDistribution<T> create(List<DistributionEntry<T>> distribution) {
        double totalOccurrenceCount = distribution.stream()
            .mapToDouble(DistributionEntry::getAssociatedProbability)
            .sum();

        Preconditions.checkArgument(totalOccurrenceCount > 0, "You need to have some entries with non-zero occurrence count in your distribution");
        return new DiscreteDistribution<>(distribution);
    }

    private final EnumeratedDistribution<T> enumeratedDistribution;

    private DiscreteDistribution(List<DistributionEntry<T>> distribution) {
        enumeratedDistribution = new EnumeratedDistribution<>(new MersenneTwister(), distribution.stream()
            .map(DistributionEntry::toPair)
            .collect(Guavate.toImmutableList()));
    }

    public Stream<T> generateRandomStream() {
        return Stream.iterate(this, i -> i)
            .map(DiscreteDistribution::sample);
    }

    public T sample() {
        return enumeratedDistribution.sample();
    }

}
