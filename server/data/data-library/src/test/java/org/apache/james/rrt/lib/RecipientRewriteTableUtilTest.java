package org.apache.james.rrt.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class RecipientRewriteTableUtilTest {

    @Test(expected=NullPointerException.class)
    public void collectionToMappingShouldThrowWhenNull() {
        RecipientRewriteTableUtil.CollectionToMapping(null);
    }
    
    @Test
    public void collectionToMappingShouldReturnEmptyStringWhenEmptyCollection() {
        String actual = RecipientRewriteTableUtil.CollectionToMapping(ImmutableList.<String>of());
        assertThat(actual).isEmpty();
    }

    @Test
    public void collectionToMappingShouldReturnSimpleValueWhenSingleElementCollection() {
        String actual = RecipientRewriteTableUtil.CollectionToMapping(ImmutableList.of("value"));
        assertThat(actual).isEqualTo("value");
    }

    @Test
    public void collectionToMappingShouldReturnSeparatedValuesWhenSeveralElementsCollection() {
        String actual = RecipientRewriteTableUtil.CollectionToMapping(ImmutableList.of("value1", "value2"));
        assertThat(actual).isEqualTo("value1;value2");
    }
    
    @Test(expected=NullPointerException.class)
    public void mappingToCollectionShouldThrowWhenNull() {
        RecipientRewriteTableUtil.mappingToCollection(null);
    }

    @Test
    public void mappingToCollectionShouldReturnEmptyCollectionWhenEmptyString() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection("");
        assertThat(actual).isEmpty();
    }
    
    @Test
    public void mappingToCollectionShouldReturnSingletonCollectionWhenSingleElementString() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection("value");
        assertThat(actual).containsExactly("value");
    }

    @Test
    public void mappingToCollectionShouldReturnCollectionWhenSeveralElementsString() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection("value1;value2");
        assertThat(actual).containsExactly("value1", "value2");
    }
    
    @Test
    public void mappingToCollectionShouldReturnSingleElementCollectionWhenTrailingDelimiterString() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection("value1;");
        assertThat(actual).containsExactly("value1");
    }

    @Test
    public void mappingToCollectionShouldReturnSingleElementCollectionWhenHeadingDelimiterString() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection(";value1");
        assertThat(actual).containsExactly("value1");
    }
    

    @Test
    public void mappingToCollectionShouldTrimValues() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection("value1 ; value2  ");
        assertThat(actual).containsExactly("value1", "value2");
    }
    
    @Test
    public void mappingToCollectionShouldNotSkipEmptyValue() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection("value1; ;value2");
        assertThat(actual).containsExactly("value1", "", "value2");
    }
    
    @Test
    public void mappingToCollectionShouldReturnCollectionWhenValueContainsCommaSeperatedValues() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection("value1,value2");
        assertThat(actual).containsExactly("value1","value2");
    }

    @Test
    public void mappingToCollectionShouldReturnCollectionWhenValueContainsColonSeperatedValues() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection("value1:value2");
        assertThat(actual).containsExactly("value1","value2");
    }

    @Test
    public void mappingToCollectionShouldUseCommaDelimiterBeforeSemicolonWhenValueContainsBoth() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection("value1;value1,value2");
        assertThat(actual).containsExactly("value1;value1","value2");
    }

    @Test
    public void mappingToCollectionShouldUseSemicolonDelimiterBeforeColonWhenValueContainsBoth() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection("value1:value1;value2");
        assertThat(actual).containsExactly("value1:value1","value2");
    }
    
    @Test
    public void mappingToCollectionShouldNotUseColonDelimiterWhenValueStartsWithError() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection("error:test");
        assertThat(actual).containsExactly("error:test");
    }
    

    @Test
    public void mappingToCollectionShouldNotUseColonDelimiterWhenValueStartsWithDomain() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection("domain:test");
        assertThat(actual).containsExactly("domain:test");
    }
    

    @Test
    public void mappingToCollectionShouldNotUseColonDelimiterWhenValueStartsWithRegex() {
        List<String> actual = RecipientRewriteTableUtil.mappingToCollection("regex:test");
        assertThat(actual).containsExactly("regex:test");
    }

}
