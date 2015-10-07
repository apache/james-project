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

package org.apache.mailet.base;

import org.apache.mailet.base.test.FakeMailetConfig;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class MailetUtilTest {

    private static final String A_PARAMETER = "aParameter";

    FakeMailetConfig config;

    @Before
    public void setUp() throws Exception {
        config = new FakeMailetConfig();
    }

    @Test
    public void testGetInitParameterParameterIsTrue() {
        assertTrue(getParameterValued("true", true));
        assertTrue(getParameterValued("true", false));
        assertTrue(getParameterValued("TRUE", true));
        assertTrue(getParameterValued("TRUE", false));
        assertTrue(getParameterValued("trUE", true));
        assertTrue(getParameterValued("trUE", false));
    }

    @Test
    public void testGetInitParameterParameterIsFalse() {
        assertFalse(getParameterValued("false", true));
        assertFalse(getParameterValued("false", false));
        assertFalse(getParameterValued("FALSE", true));
        assertFalse(getParameterValued("FALSE", false));
        assertFalse(getParameterValued("fALSe", true));
        assertFalse(getParameterValued("fALSe", false));
    }

    @Test
    public void testGetInitParameterParameterDefaultsToTrue() {
        assertTrue(getParameterValued("fals", true));
        assertTrue(getParameterValued("TRU", true));
        assertTrue(getParameterValued("FALSEest", true));
        assertTrue(getParameterValued("", true));
        assertTrue(getParameterValued("gubbins", true));
    }

    @Test
    public void testGetInitParameterParameterDefaultsToFalse() {
        assertFalse(getParameterValued("fals", false));
        assertFalse(getParameterValued("TRU", false));
        assertFalse(getParameterValued("FALSEest", false));
        assertFalse(getParameterValued("", false));
        assertFalse(getParameterValued("gubbins", false));
    }

    private boolean getParameterValued(String value, boolean defaultValue) {
        config.clear();
        config.setProperty(A_PARAMETER, value);
        return MailetUtil.getInitParameter(config, A_PARAMETER, defaultValue);
    }
}
