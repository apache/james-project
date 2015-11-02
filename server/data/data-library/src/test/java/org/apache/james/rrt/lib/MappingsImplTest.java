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

package org.apache.james.rrt.lib;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;


public class MappingsImplTest {

    @Test(expected=NullPointerException.class)
    public void fromRawStringShouldThrowWhenNull() {
        MappingsImpl.fromRawString(null);
    }

    @Test
    public void fromRawStringShouldReturnEmptyCollectionWhenEmptyString() {
        MappingsImpl actual = MappingsImpl.fromRawString("");
        assertThat(actual.asStrings()).isEmpty();
    }
    
    @Test
    public void fromRawStringShouldReturnSingletonCollectionWhenSingleElementString() {
        MappingsImpl actual = MappingsImpl.fromRawString("value");
        assertThat(actual.asStrings()).containsExactly("value");
    }

    @Test
    public void fromRawStringShouldReturnCollectionWhenSeveralElementsString() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1;value2");
        assertThat(actual.asStrings()).containsExactly("value1", "value2");
    }
    
    @Test
    public void fromRawStringShouldReturnSingleElementCollectionWhenTrailingDelimiterString() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1;");
        assertThat(actual.asStrings()).containsExactly("value1");
    }

    @Test
    public void fromRawStringShouldReturnSingleElementCollectionWhenHeadingDelimiterString() {
        MappingsImpl actual = MappingsImpl.fromRawString(";value1");
        assertThat(actual.asStrings()).containsExactly("value1");
    }
    

    @Test
    public void fromRawStringShouldTrimValues() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1 ; value2  ");
        assertThat(actual.asStrings()).containsExactly("value1", "value2");
    }
    
    @Test
    public void fromRawStringShouldNotSkipEmptyValue() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1; ;value2");
        assertThat(actual.asStrings()).containsExactly("value1", "", "value2");
    }
    
    @Test
    public void fromRawStringShouldReturnCollectionWhenValueContainsCommaSeperatedValues() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1,value2");
        assertThat(actual.asStrings()).containsExactly("value1","value2");
    }

    @Test
    public void fromRawStringShouldReturnCollectionWhenValueContainsColonSeperatedValues() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1:value2");
        assertThat(actual.asStrings()).containsExactly("value1","value2");
    }

    @Test
    public void fromRawStringShouldUseCommaDelimiterBeforeSemicolonWhenValueContainsBoth() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1;value1,value2");
        assertThat(actual.asStrings()).containsExactly("value1;value1","value2");
    }

    @Test
    public void fromRawStringShouldUseSemicolonDelimiterBeforeColonWhenValueContainsBoth() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1:value1;value2");
        assertThat(actual.asStrings()).containsExactly("value1:value1","value2");
    }
    
    @Test
    public void fromRawStringShouldNotUseColonDelimiterWhenValueStartsWithError() {
        MappingsImpl actual = MappingsImpl.fromRawString("error:test");
        assertThat(actual.asStrings()).containsExactly("error:test");
    }
    

    @Test
    public void fromRawStringShouldNotUseColonDelimiterWhenValueStartsWithDomain() {
        MappingsImpl actual = MappingsImpl.fromRawString("domain:test");
        assertThat(actual.asStrings()).containsExactly("domain:test");
    }
    

    @Test
    public void fromRawStringShouldNotUseColonDelimiterWhenValueStartsWithRegex() {
        MappingsImpl actual = MappingsImpl.fromRawString("regex:test");
        assertThat(actual.asStrings()).containsExactly("regex:test");
    }

    
}
