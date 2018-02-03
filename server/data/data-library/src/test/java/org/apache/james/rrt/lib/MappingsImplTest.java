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

import java.util.Optional;

import org.junit.Test;


public class MappingsImplTest {

    @Test(expected = NullPointerException.class)
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
        assertThat(actual).containsExactly(MappingImpl.address("value"));
    }

    @Test
    public void fromRawStringShouldReturnCollectionWhenSeveralElementsString() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1;value2");
        assertThat(actual).containsExactly(MappingImpl.address("value1"), MappingImpl.address("value2"));
    }
    
    @Test
    public void fromRawStringShouldReturnSingleElementCollectionWhenTrailingDelimiterString() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1;");
        assertThat(actual).containsExactly(MappingImpl.address("value1"));
    }

    @Test
    public void fromRawStringShouldReturnSingleElementCollectionWhenHeadingDelimiterString() {
        MappingsImpl actual = MappingsImpl.fromRawString(";value1");
        assertThat(actual).containsExactly(MappingImpl.address("value1"));
    }
    

    @Test
    public void fromRawStringShouldTrimValues() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1 ; value2  ");
        assertThat(actual).containsExactly(MappingImpl.address("value1"), MappingImpl.address("value2"));
    }
    
    @Test
    public void fromRawStringShouldNotSkipEmptyValue() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1; ;value2");
        assertThat(actual).containsExactly(MappingImpl.address("value1"), MappingImpl.address(""), MappingImpl.address("value2"));
    }
    
    @Test
    public void fromRawStringShouldReturnCollectionWhenValueContainsCommaSeperatedValues() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1,value2");
        assertThat(actual).containsExactly(MappingImpl.address("value1"),MappingImpl.address("value2"));
    }

    @Test
    public void fromRawStringShouldReturnCollectionWhenValueContainsColonSeperatedValues() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1:value2");
        assertThat(actual).containsExactly(MappingImpl.address("value1"),MappingImpl.address("value2"));
    }

    @Test
    public void fromRawStringShouldUseCommaDelimiterBeforeSemicolonWhenValueContainsBoth() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1;value1,value2");
        assertThat(actual).containsExactly(MappingImpl.address("value1;value1"),MappingImpl.address("value2"));
    }

    @Test
    public void fromRawStringShouldUseSemicolonDelimiterBeforeColonWhenValueContainsBoth() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1:value1;value2");
        assertThat(actual).containsExactly(MappingImpl.address("value1:value1"),MappingImpl.address("value2"));
    }
    
    @Test
    public void fromRawStringShouldNotUseColonDelimiterWhenValueStartsWithError() {
        MappingsImpl actual = MappingsImpl.fromRawString("error:test");
        assertThat(actual).containsExactly(MappingImpl.error("test"));
    }
    

    @Test
    public void fromRawStringShouldNotUseColonDelimiterWhenValueStartsWithDomain() {
        MappingsImpl actual = MappingsImpl.fromRawString("domain:test");
        assertThat(actual).containsExactly(MappingImpl.domain("test"));
    }
    

    @Test
    public void fromRawStringShouldNotUseColonDelimiterWhenValueStartsWithRegex() {
        MappingsImpl actual = MappingsImpl.fromRawString("regex:test");
        assertThat(actual).containsExactly(MappingImpl.regex("test"));
    }

    @Test
    public void serializeShouldReturnEmptyStringWhenEmpty() {
        assertThat(MappingsImpl.empty()).isEmpty();
    }

    @Test
    public void serializeShouldReturnSimpleValueWhenSingleElement() {
        String actual = MappingsImpl.builder().add("value").build().serialize();
        assertThat(actual).isEqualTo("value");
    }

    @Test
    public void collectionToMappingShouldReturnSeparatedValuesWhenSeveralElementsCollection() {
        String actual = MappingsImpl.builder().add("value1").add("value2").build().serialize();
        assertThat(actual).isEqualTo("value1;value2");
    }

    @Test
    public void containsShouldReturnTrueWhenMatchingMapping() {
        MappingsImpl mappings = MappingsImpl.builder().add(MappingImpl.regex("toto")).build();
        assertThat(mappings.contains(Mapping.Type.Regex)).isTrue();
    }
    
    @Test
    public void containsShouldReturnFalseWhenNoMatchingMapping() {
        MappingsImpl mappings = MappingsImpl.builder().add(MappingImpl.regex("toto")).build();
        assertThat(mappings.contains(Mapping.Type.Error)).isFalse();
    }

    
    @Test(expected = NullPointerException.class)
    public void containsShouldThrowWhenNull() {
        MappingsImpl mappings = MappingsImpl.builder().add(MappingImpl.regex("toto")).build();
        assertThat(mappings.contains((Mapping.Type)null));
    }
    
    @Test
    public void selectShouldReturnMatchingElementsInOrderWhenMatchingMapping() {
        MappingsImpl mappings = MappingsImpl.builder()
                .add(MappingImpl.regex("toto"))
                .add(MappingImpl.address("toto")) 
                .add(MappingImpl.domain("domain"))
                .add(MappingImpl.regex("tata"))
                .build();
        MappingsImpl expected = MappingsImpl.builder()
                .add(MappingImpl.regex("toto"))
                .add(MappingImpl.regex("tata"))
                .build();
        assertThat(mappings.select(Mapping.Type.Regex)).isEqualTo(expected);
    }
    
    @Test
    public void selectShouldReturnEmptyCollectionWhenNoMatchingMapping() {
        MappingsImpl mappings = MappingsImpl.builder()
                .add(MappingImpl.regex("toto"))
                .add(MappingImpl.address("toto")) 
                .add(MappingImpl.address("tata"))
                .build();
        assertThat(mappings.select(Mapping.Type.Domain)).isEqualTo(MappingsImpl.empty());
    }

    
    @Test(expected = NullPointerException.class)
    public void selectShouldThrowWhenNull() {
        MappingsImpl mappings = MappingsImpl.builder().add(MappingImpl.regex("toto")).build();
        assertThat(mappings.select((Mapping.Type)null));
    }

    @Test
    public void excludeShouldReturnNonMatchingElementsInOrderWhenNonMatchingMapping() {
        MappingsImpl mappings = MappingsImpl.builder()
                .add(MappingImpl.regex("toto"))
                .add(MappingImpl.address("toto")) 
                .add(MappingImpl.domain("domain"))
                .add(MappingImpl.regex("tata"))
                .build();
        MappingsImpl expected = MappingsImpl.builder()
                .add(MappingImpl.address("toto")) 
                .add(MappingImpl.domain("domain"))
                .build();
        assertThat(mappings.exclude(Mapping.Type.Regex)).isEqualTo(expected);
    }
    
    @Test
    public void excludeShouldReturnEmptyCollectionWhenOnlyMatchingMapping() {
        MappingsImpl mappings = MappingsImpl.builder()
                .add(MappingImpl.address("toto")) 
                .add(MappingImpl.address("tata"))
                .build();
        assertThat(mappings.exclude(Mapping.Type.Address)).isEqualTo(MappingsImpl.empty());
    }

    
    @Test(expected = NullPointerException.class)
    public void excludeShouldThrowWhenNull() {
        MappingsImpl mappings = MappingsImpl.builder().add(MappingImpl.regex("toto")).build();
        assertThat(mappings.exclude((Mapping.Type)null));
    }

    @Test
    public void toOptionalShouldBePresentWhenContainingData() {
        MappingsImpl mappings = MappingsImpl.builder().add("toto").build();

        Optional<Mappings> optional = mappings.toOptional();
        assertThat(optional.isPresent()).isTrue();
    }

    @Test
    public void toOptionalShouldBeAbsentWhenNoData() {
        MappingsImpl mappings = MappingsImpl.empty();

        Optional<Mappings> optional = mappings.toOptional();
        assertThat(optional.isPresent()).isFalse();
    }

    @Test(expected = IllegalStateException.class)
    public void unionShouldThrowWhenMappingsNull() {
        MappingsImpl.empty().union(null);
    }

    @Test
    public void unionShouldReturnEmptyWhenBothEmpty() {
        Mappings mappings = MappingsImpl.empty().union(MappingsImpl.empty());
        assertThat(mappings).isEmpty();
    }

    @Test
    public void unionShouldReturnMergedWhenBothContainsData() {
        Mappings mappings = MappingsImpl.fromRawString("toto").union(MappingsImpl.fromRawString("tata"));
        assertThat(mappings).containsExactly(MappingImpl.address("toto"),MappingImpl.address("tata"));
    }
}
