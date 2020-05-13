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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.core.Domain;
import org.junit.jupiter.api.Test;


public class MappingsImplTest {

    @Test
    public void fromRawStringShouldThrowWhenNull() {
        assertThatThrownBy(() -> MappingsImpl.fromRawString(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void fromRawStringShouldReturnEmptyCollectionWhenEmptyString() {
        MappingsImpl actual = MappingsImpl.fromRawString("");
        assertThat(actual.asStrings()).isEmpty();
    }
    
    @Test
    public void fromRawStringShouldReturnSingletonCollectionWhenSingleElementString() {
        MappingsImpl actual = MappingsImpl.fromRawString("value");
        assertThat(actual).containsOnly(Mapping.address("value"));
    }

    @Test
    public void fromRawStringShouldReturnCollectionWhenSeveralElementsString() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1;value2");
        assertThat(actual).containsOnly(Mapping.address("value1"), Mapping.address("value2"));
    }
    
    @Test
    public void fromRawStringShouldReturnSingleElementCollectionWhenTrailingDelimiterString() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1;");
        assertThat(actual).containsOnly(Mapping.address("value1"));
    }

    @Test
    public void fromRawStringShouldReturnSingleElementCollectionWhenHeadingDelimiterString() {
        MappingsImpl actual = MappingsImpl.fromRawString(";value1");
        assertThat(actual).containsOnly(Mapping.address("value1"));
    }
    

    @Test
    public void fromRawStringShouldTrimValues() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1 ; value2  ");
        assertThat(actual).containsOnly(Mapping.address("value1"), Mapping.address("value2"));
    }
    
    @Test
    public void fromRawStringShouldNotSkipEmptyValue() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1; ;value2");
        assertThat(actual).containsOnly(Mapping.address("value1"), Mapping.address(""), Mapping.address("value2"));
    }
    
    @Test
    public void fromRawStringShouldReturnCollectionWhenValueContainsCommaSeperatedValues() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1,value2");
        assertThat(actual).containsOnly(Mapping.address("value1"),Mapping.address("value2"));
    }

    @Test
    public void fromRawStringShouldReturnCollectionWhenValueContainsColonSeperatedValues() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1:value2");
        assertThat(actual).containsOnly(Mapping.address("value1"),Mapping.address("value2"));
    }

    @Test
    public void fromRawStringShouldUseCommaDelimiterBeforeSemicolonWhenValueContainsBoth() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1;value1,value2");
        assertThat(actual).containsOnly(Mapping.address("value1;value1"),Mapping.address("value2"));
    }

    @Test
    public void fromRawStringShouldUseSemicolonDelimiterBeforeColonWhenValueContainsBoth() {
        MappingsImpl actual = MappingsImpl.fromRawString("value1:value1;value2");
        assertThat(actual).containsOnly(Mapping.address("value1:value1"),Mapping.address("value2"));
    }
    
    @Test
    public void fromRawStringShouldNotUseColonDelimiterWhenValueStartsWithError() {
        MappingsImpl actual = MappingsImpl.fromRawString("error:test");
        assertThat(actual).containsOnly(Mapping.error("test"));
    }

    @Test
    public void fromRawStringShouldNotUseColonDelimiterWhenValueStartsWithDomain() {
        MappingsImpl actual = MappingsImpl.fromRawString("domain:test");
        assertThat(actual).containsOnly(Mapping.domain(Domain.of("test")));
    }

    @Test
    public void fromRawStringShouldNotUseColonDelimiterWhenValueStartsWithDomainAlias() {
        MappingsImpl actual = MappingsImpl.fromRawString("domainAlias:test");
        assertThat(actual).containsOnly(Mapping.domainAlias(Domain.of("test")));
    }

    @Test
    public void fromRawStringShouldNotUseColonDelimiterWhenValueStartsWithRegex() {
        MappingsImpl actual = MappingsImpl.fromRawString("regex:test");
        assertThat(actual).containsOnly(Mapping.regex("test"));
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
        MappingsImpl mappings = MappingsImpl.builder().add(Mapping.regex("toto")).build();
        assertThat(mappings.contains(Mapping.Type.Regex)).isTrue();
    }
    
    @Test
    public void containsShouldReturnFalseWhenNoMatchingMapping() {
        MappingsImpl mappings = MappingsImpl.builder().add(Mapping.regex("toto")).build();
        assertThat(mappings.contains(Mapping.Type.Error)).isFalse();
    }

    
    @Test
    public void containsShouldThrowWhenNull() {
        MappingsImpl mappings = MappingsImpl.builder().add(Mapping.regex("toto")).build();
        assertThatThrownBy(() -> mappings.contains((Mapping.Type)null))
            .isInstanceOf(NullPointerException.class);
    }
    
    @Test
    public void selectShouldReturnMatchingElementsInOrderWhenMatchingMapping() {
        MappingsImpl mappings = MappingsImpl.builder()
            .add(Mapping.regex("toto"))
            .add(Mapping.address("toto"))
            .add(Mapping.domain(Domain.of("domain")))
            .add(Mapping.regex("tata"))
            .build();
        MappingsImpl expected = MappingsImpl.builder()
                .add(Mapping.regex("toto"))
                .add(Mapping.regex("tata"))
                .build();
        assertThat(mappings.select(Mapping.Type.Regex)).isEqualTo(expected);
    }
    
    @Test
    public void selectShouldReturnEmptyCollectionWhenNoMatchingMapping() {
        MappingsImpl mappings = MappingsImpl.builder()
                .add(Mapping.regex("toto"))
                .add(Mapping.address("toto"))
                .add(Mapping.address("tata"))
                .build();
        assertThat(mappings.select(Mapping.Type.Domain)).isEqualTo(MappingsImpl.empty());
    }

    
    @Test
    public void selectShouldThrowWhenNull() {
        MappingsImpl mappings = MappingsImpl.builder().add(Mapping.regex("toto")).build();
        assertThatThrownBy(() -> mappings.select((Mapping.Type)null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void excludeShouldReturnNonMatchingElementsInOrderWhenNonMatchingMapping() {
        MappingsImpl mappings = MappingsImpl.builder()
            .add(Mapping.regex("toto"))
            .add(Mapping.address("toto"))
            .add(Mapping.domain(Domain.of("domain")))
            .add(Mapping.regex("tata"))
            .build();
        MappingsImpl expected = MappingsImpl.builder()
            .add(Mapping.address("toto"))
            .add(Mapping.domain(Domain.of("domain")))
            .build();
        assertThat(mappings.exclude(Mapping.Type.Regex)).isEqualTo(expected);
    }
    
    @Test
    public void excludeShouldReturnEmptyCollectionWhenOnlyMatchingMapping() {
        MappingsImpl mappings = MappingsImpl.builder()
                .add(Mapping.address("toto"))
                .add(Mapping.address("tata"))
                .build();
        assertThat(mappings.exclude(Mapping.Type.Address)).isEqualTo(MappingsImpl.empty());
    }

    
    @Test
    public void excludeShouldThrowWhenNull() {
        MappingsImpl mappings = MappingsImpl.builder().add(Mapping.regex("toto")).build();
        assertThatThrownBy(() -> mappings.exclude((Mapping.Type)null))
            .isInstanceOf(NullPointerException.class);
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

    @Test
    public void unionShouldThrowWhenMappingsNull() {
        assertThatThrownBy(() -> MappingsImpl.empty().union(null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void unionShouldReturnEmptyWhenBothEmpty() {
        Mappings mappings = MappingsImpl.empty().union(MappingsImpl.empty());
        assertThat(mappings).isEmpty();
    }

    @Test
    public void unionShouldReturnMergedWhenBothContainsData() {
        Mappings mappings = MappingsImpl.fromRawString("toto").union(MappingsImpl.fromRawString("tata"));
        assertThat(mappings).containsOnly(Mapping.address("toto"),Mapping.address("tata"));
    }

    @Test
    public void mergeShouldThrowWhenLeftIsNull() {
        MappingsImpl.Builder left = null;
        assertThatThrownBy(() -> MappingsImpl.Builder.merge(left, MappingsImpl.builder()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void mergeShouldThrowWhenRightIsNull() {
        MappingsImpl.Builder right = null;
        assertThatThrownBy(() -> MappingsImpl.Builder.merge(MappingsImpl.builder(), right))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void mergeShouldReturnEmptyWhenBothAreEmpty() {
        MappingsImpl.Builder empty = MappingsImpl.builder();
        MappingsImpl mappingsImpl = MappingsImpl.Builder
                .merge(empty, empty)
                .build();
        assertThat(mappingsImpl.isEmpty()).isTrue();
    }

    @Test
    public void mergeShouldReturnLeftWhenRightIsEmpty() {
        Mapping expectedMapping = Mapping.address("toto");
        MappingsImpl.Builder left = MappingsImpl.builder().add(expectedMapping);
        MappingsImpl.Builder empty = MappingsImpl.builder();
        MappingsImpl mappingsImpl = MappingsImpl.Builder
                .merge(left, empty)
                .build();
        assertThat(mappingsImpl).containsOnly(expectedMapping);
    }

    @Test
    public void mergeShouldReturnRightWhenLeftIsEmpty() {
        Mapping expectedMapping = Mapping.address("toto");
        MappingsImpl.Builder right = MappingsImpl.builder().add(expectedMapping);
        MappingsImpl.Builder empty = MappingsImpl.builder();
        MappingsImpl mappingsImpl = MappingsImpl.Builder
                .merge(empty, right)
                .build();
        assertThat(mappingsImpl).containsOnly(expectedMapping);
    }

    @Test
    public void mergeShouldReturnBothWhenBothAreNotEmpty() {
        Mapping leftMapping = Mapping.address("toto");
        MappingsImpl.Builder left = MappingsImpl.builder().add(leftMapping);
        Mapping rightMapping = Mapping.address("titi");
        MappingsImpl.Builder right = MappingsImpl.builder().add(rightMapping);
        MappingsImpl mappingsImpl = MappingsImpl.Builder
                .merge(left, right)
                .build();
        assertThat(mappingsImpl).containsOnly(leftMapping, rightMapping);
    }
    
    @Test
    public void builderShouldPutDomainAliasFirstWhenVariousMappings() {
        Mapping addressMapping = Mapping.address("aaa");
        Mapping errorMapping = Mapping.error("error");
        Mapping domainMapping = Mapping.domain(Domain.of("domain"));
        Mapping domain2Mapping = Mapping.domain(Domain.of("domain2"));
        MappingsImpl mappingsImpl = MappingsImpl.builder()
                .add(domainMapping)
                .add(addressMapping)
                .add(errorMapping)
                .add(domain2Mapping)
                .build();
        assertThat(mappingsImpl).containsExactly(domainMapping, domain2Mapping, addressMapping, errorMapping);
    }
    
    @Test
    public void builderShouldPutDomainMappingFirstThenForwardWhenVariousMappings() {
        Mapping regexMapping = Mapping.regex("regex");
        Mapping forwardMapping = Mapping.forward("forward");
        Mapping domainMapping = Mapping.domain(Domain.of("domain"));
        MappingsImpl mappingsImpl = MappingsImpl.builder()
                .add(regexMapping)
                .add(forwardMapping)
                .add(domainMapping)
                .build();
        assertThat(mappingsImpl).containsExactly(domainMapping, forwardMapping, regexMapping);
    }

    @Test
    public void builderShouldPutDomainAliasFirstThenForwardWhenVariousMappings() {
        Mapping regexMapping = Mapping.regex("regex");
        Mapping forwardMapping = Mapping.forward("forward");
        Mapping domainAlias = Mapping.domainAlias(Domain.of("domain"));
        MappingsImpl mappingsImpl = MappingsImpl.builder()
                .add(regexMapping)
                .add(forwardMapping)
                .add(domainAlias)
                .build();
        assertThat(mappingsImpl).containsExactly(domainAlias, forwardMapping, regexMapping);
    }

    @Test
    public void builderShouldPutGroupsBetweenDomainAndForward() {
        Mapping regexMapping = Mapping.regex("regex");
        Mapping forwardMapping = Mapping.forward("forward");
        Mapping domainMapping = Mapping.domain(Domain.of("domain"));
        Mapping groupMapping = Mapping.group("group");
        MappingsImpl mappingsImpl = MappingsImpl.builder()
                .add(regexMapping)
                .add(forwardMapping)
                .add(domainMapping)
                .add(groupMapping)
                .build();
        assertThat(mappingsImpl).containsExactly(domainMapping, groupMapping, forwardMapping, regexMapping);
    }
}
