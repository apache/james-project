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

package org.apache.james.jmap.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.james.jmap.utils.DependencyGraph.CycleDetectedException;
import org.junit.Test;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class DependencyGraphTest {

    @Test
    public void getBuildChainShouldReturnOrderedMailbox() {
        // Given
        Commit a = new Commit("A");
        Commit b = new Commit("B", a);
        Commit c = new Commit("C", b);

        DependencyGraph<Commit> graph = new DependencyGraph<>(Commit::getParent);
        Stream.of(b, a, c)
            .forEach(graph::registerItem);

        // When
        Stream<Commit> orderedMailboxes = graph.getBuildChain();

        // Then
        assertThat(orderedMailboxes).extracting(Commit::getMessage).containsExactly("A", "B", "C");
    }

    @Test
    public void getBuildChainWithEmptyGraphShouldReturnEmpty() {
        DependencyGraph<Commit> graph = new DependencyGraph<>(m -> null);
        assertThat(graph.getBuildChain()).isEmpty();
    }

    @Test
    public void getBuildChainOnIsolatedVerticesShouldReturnSameOrder() {
        DependencyGraph<Commit> graph = new DependencyGraph<>(m -> Optional.empty());
        ImmutableList<Commit> isolatedMailboxes = ImmutableList.of(new Commit("A"), new Commit("B"), new Commit("C"));
        isolatedMailboxes.forEach(graph::registerItem);

        List<Commit> orderedResultList = graph.getBuildChain().collect(Collectors.toList());

        assertThat(orderedResultList).isEqualTo(isolatedMailboxes);
    }

    @Test
    public void getBuildChainOnTwoIsolatedTreesShouldWork() {
        // a-b  d-e
        //  \c   \f

        //Given
        Commit a = new Commit("A");
        Commit b = new Commit("B", a);
        Commit c = new Commit("C", b);
        Commit d = new Commit("D");
        Commit e = new Commit("E", d);
        Commit f = new Commit("F", d);
        DependencyGraph<Commit> testee = new DependencyGraph<>(Commit::getParent);
        Stream.of(b, a, e, d, f, c)
            .forEach(testee::registerItem);

        //When
        Stream<Commit> actual = testee.getBuildChain();

        //Then
        assertThat(actual).extracting(Commit::getMessage).containsExactly("A", "D", "B", "E", "F", "C");
    }

    @Test
    public void getBuildChainOnComplexTreeShouldWork() {
        //Given
        Commit a = new Commit("A");
        Commit b = new Commit("B", a);
        Commit c = new Commit("C", a);
        Commit d = new Commit("D", b);
        Commit e = new Commit("E", b);
        Commit f = new Commit("F", c);
        Commit g = new Commit("G", e);
        DependencyGraph<Commit> testee = new DependencyGraph<>(Commit::getParent);
        Stream.of(b, a, e, g, d, f, c)
            .forEach(testee::registerItem);

        //When
        Stream<Commit> actual = testee.getBuildChain();

        //Then
        assertThat(actual).extracting(Commit::getMessage).containsExactly("A", "B", "C", "E", "D", "F", "G");
    }
    
    @Test(expected=CycleDetectedException.class)
    public void getBuildChainOnTreeWithLoopShouldFail() {
        //Given
        Commit a = new Commit("A");
        Commit b = new Commit("B", a);
        a.setParent(b);
        DependencyGraph<Commit> testee = new DependencyGraph<>(Commit::getParent);
        Stream.of(a, b)
            .forEach(testee::registerItem);

        //When
        testee.getBuildChain();
    }
    
    @Test(expected=CycleDetectedException.class)
    public void getBuildChainOnTreeWithComplexLoopShouldFail() {
        // a - b
        // c - d - e - f
        // |___________|
        //Given
        Commit a = new Commit("A");
        Commit b = new Commit("B", a);
        
        Commit c = new Commit("C");
        Commit d = new Commit("D", c);
        Commit e = new Commit("E", d);
        Commit f = new Commit("F", e);
        c.setParent(f);
        DependencyGraph<Commit> testee = new DependencyGraph<>(Commit::getParent);
        Stream.of(a, b, c, d, e, f)
            .forEach(testee::registerItem);

        //When
        testee.getBuildChain();
    }


    private static class Commit {
        private final String message;
        private Optional<Commit> parent;

        @VisibleForTesting
        Commit(String message) {
            this(message, null);
        }

        @VisibleForTesting
        Commit(String message, Commit parent) {
            Preconditions.checkArgument(message != null);
            this.message = message;
            this.parent = Optional.ofNullable(parent);
        }

        public Optional<Commit> getParent() {
            return parent;
        }
        
        public void setParent(Commit parent) {
            this.parent = Optional.of(parent);
        }

        public String getMessage() {
            return message;
        }
    }
}
