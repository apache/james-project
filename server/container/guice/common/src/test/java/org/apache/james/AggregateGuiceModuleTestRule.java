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

package org.apache.james;

import java.util.List;

import org.junit.rules.RuleChain;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import com.google.inject.util.Modules;


public class AggregateGuiceModuleTestRule implements GuiceModuleTestRule {

    public static AggregateGuiceModuleTestRule of(GuiceModuleTestRule... subrule) {
        return new AggregateGuiceModuleTestRule(ImmutableList.copyOf(subrule));
    }

    private final List<GuiceModuleTestRule> subrule;
    private final RuleChain chain;

    private AggregateGuiceModuleTestRule(List<GuiceModuleTestRule> subrule) {
        this.subrule = subrule;
        this.chain = subrule
                .stream()
                .reduce(RuleChain.emptyRuleChain(),
                        RuleChain::around,
                        RuleChain::around);
    }

    public AggregateGuiceModuleTestRule aggregate(GuiceModuleTestRule... subrule) {
        List<GuiceModuleTestRule> guiceModules = ImmutableList.<GuiceModuleTestRule>builder()
                .addAll(this.subrule)
                .add(subrule)
                .build();

        return new AggregateGuiceModuleTestRule(guiceModules);
    }

    @Override
    public Module getModule() {
        List<Module> modules = subrule
                    .stream()
                    .map(GuiceModuleTestRule::getModule)
                    .collect(Guavate.toImmutableList());

        return Modules.combine(modules);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return chain.apply(base, description);
    }

    @Override
    public void await() {
        subrule
            .parallelStream()
            .forEach(GuiceModuleTestRule::await);
    }
}
