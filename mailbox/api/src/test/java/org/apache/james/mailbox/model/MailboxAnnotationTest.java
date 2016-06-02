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

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.guava.api.Assertions.assertThat;

import org.junit.Test;

public class MailboxAnnotationTest {
    private static final String ASTERISK_CHARACTER = "*";

    private static final String PERCENT_CHARACTER = "%";

    private static final String ANY_KEY = "shared";
    private static final String ANNOTATION_KEY = "/private/comment"; 
    private static final String ANNOTATION_VALUE = "anyValue";

    @Test(expected = NullPointerException.class)
    public void newInstanceShouldThrowsExceptionWithNullKey() throws Exception {
        MailboxAnnotation.newInstance(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void newInstanceShouldThrowsExceptionWithNullValue() throws Exception {
        MailboxAnnotation.newInstance(ANY_KEY, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstanceShouldThrowsExceptionWithEmptyKey() throws Exception {
        MailboxAnnotation.newInstance("", ANNOTATION_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstanceShouldThrowsExceptionWithBlankKey() throws Exception {
        MailboxAnnotation.newInstance("   ", ANNOTATION_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstanceShouldThrowsExceptionWhenKeyDoesNotStartWithSlash() throws Exception {
        MailboxAnnotation.newInstance(ANY_KEY, ANNOTATION_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstanceShouldThrowsExceptionWhenKeyContainsAsterisk() throws Exception {
        MailboxAnnotation.newInstance(buildAnnotationKey(ASTERISK_CHARACTER), ANNOTATION_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstanceShouldThrowsExceptionWhenKeyContainsPercent() throws Exception {
        MailboxAnnotation.newInstance(buildAnnotationKey(PERCENT_CHARACTER), ANNOTATION_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validKeyShouldThrowsExceptionWhenKeyContainsTwoConsecutiveSlash() throws Exception {
        MailboxAnnotation.newInstance(buildAnnotationKey(MailboxAnnotation.TWO_SLASH_CHARACTER), ANNOTATION_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validKeyShouldThrowsExceptionWhenKeyEndsWithSlash() throws Exception {
        MailboxAnnotation.newInstance(buildAnnotationKey(MailboxAnnotation.SLASH_CHARACTER) + MailboxAnnotation.SLASH_CHARACTER, 
            ANNOTATION_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validKeyShouldThrowsExceptionWhenKeyContainsNonASCII() throws Exception {
        MailboxAnnotation.newInstance(buildAnnotationKey("┬á"), ANNOTATION_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validKeyShouldThrowsExceptionWhenKeyContainsTabCharacter() throws Exception {
        MailboxAnnotation.newInstance(buildAnnotationKey("\t"), ANNOTATION_VALUE);
    }

    @Test
    public void nilInstanceShouldReturnAbsentValue() throws Exception {
        MailboxAnnotation annotation = MailboxAnnotation.nil(ANNOTATION_KEY);

        assertThat(annotation.getValue()).isAbsent();
    }

    @Test
    public void isNilShouldReturnTrueForNilObject() throws Exception {
        MailboxAnnotation nilAnnotation = MailboxAnnotation.nil(ANNOTATION_KEY);
        assertThat(nilAnnotation.isNil()).isTrue();
    }

    @Test
    public void isNilShouldReturnFalseForNotNilObject() throws Exception {
        MailboxAnnotation nilAnnotation = MailboxAnnotation.newInstance(ANNOTATION_KEY, ANY_KEY);
        assertThat(nilAnnotation.isNil()).isFalse();
    }

    @Test(expected = NullPointerException.class)
    public void newInstanceMailboxAnnotationShouldThrowExceptionWithNullValue() throws Exception {
        MailboxAnnotation.newInstance(ANY_KEY, null);
    }

    @Test
    public void newInstanceMailboxAnnotationShouldCreateNewInstance() throws Exception {
        MailboxAnnotation annotation = MailboxAnnotation.newInstance(ANNOTATION_KEY, ANNOTATION_VALUE);

        assertThat(annotation.getKey()).contains(ANNOTATION_KEY);
        assertThat(annotation.getValue()).contains(ANNOTATION_VALUE);
    }

    private String buildAnnotationKey(String apartOfKey) {
        return MailboxAnnotation.SLASH_CHARACTER + ANY_KEY + apartOfKey + ANY_KEY;
    }

}
