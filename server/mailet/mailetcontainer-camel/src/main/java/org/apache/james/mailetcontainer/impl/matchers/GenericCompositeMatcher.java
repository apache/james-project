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

package org.apache.james.mailetcontainer.impl.matchers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.mailet.Matcher;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.collect.ImmutableList;

/**
 * Abstract base class for CompositeMatchers. This class handles the child
 * collection of Matchers associated with the CompositeMatcher.
 */
public abstract class GenericCompositeMatcher extends GenericMatcher implements CompositeMatcher {
    /**
     * This lets the SpoolManager configuration code build up the composition
     * (which might be composed of other composites).
     * 
     * @param matcher
     *            Matcher child of the CompositeMatcher.
     */
    public void add(Matcher matcher) {
        matchers.add(matcher);
    }

    /**
     * @return Immutable collection for the child matchers
     */
    public List<Matcher> getMatchers() {
        return ImmutableList.copyOf(matchers);
    }

    // the collection used to store the child-matchers
    private final Collection<Matcher> matchers = new ArrayList<>();

}
