package org.apache.james.rrt.lib;

import static org.assertj.core.api.Assertions.assertThat;

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
    
}
