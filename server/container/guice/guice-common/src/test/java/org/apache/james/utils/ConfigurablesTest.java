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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.junit.Before;
import org.junit.Test;

public class ConfigurablesTest {

    private Startables sut;

    @Before
    public void setup() {
        sut = new Startables();
    }

    @Test
    public void addShouldNotStoreTwoTimesWhenSameConfigurable() {
        sut.add(MyConfigurable.class);
        sut.add(MyConfigurable.class);

        assertThat(sut.get()).hasSize(1);
    }

    @Test
    public void configurablesShouldKeepTheAddedElementsOrder() {
        sut.add(MyConfigurable.class);
        sut.add(MyConfigurable2.class);

        assertThat(sut.get()).containsExactly(MyConfigurable.class, MyConfigurable2.class);
    }

    private static class MyConfigurable implements Configurable {

        @Override
        public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        }
    }

    private static class MyConfigurable2 implements Configurable {

        @Override
        public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        }
    }
}
