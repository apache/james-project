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

package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.jmap.utils.HtmlTextExtractor;
import org.junit.Before;
import org.junit.Test;

public class MessagePreviewGeneratorTest {
    
    private MessagePreviewGenerator testee;
    private HtmlTextExtractor htmlTextExtractor;
    
    @Before
    public void setUp() {
        htmlTextExtractor = mock(HtmlTextExtractor.class);
        testee = new MessagePreviewGenerator(htmlTextExtractor);
    }

    @Test
    public void forHTMLBodyShouldReturnTruncatedStringWithoutHtmlTagWhenStringContainTagsAndIsLongerThan256Characters() {
        //Given
        String body = "This is a <b>HTML</b> mail containing <u>underlined part</u>, <i>italic part</i> and <u><i>underlined AND italic part</i></u>9999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "000000000011111111112222222222333333333344444444445555555";
        String bodyWithoutTags = "This is a HTML mail containing underlined part, italic part and underlined AND italic part9999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "000000000011111111112222222222333333333344444444445555555";
        String expected = "This is a HTML mail containing underlined part, italic part and underlined AND italic part9999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "00000000001111111111222222222233333333334444444444555...";
        //When
        when(htmlTextExtractor.toPlainText(body))
            .thenReturn(bodyWithoutTags);
        String actual = testee.forHTMLBody(Optional.of(body));
        //Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void forHTMLBodyShouldReturnStringContainingEmptyWhenEmptyString() {
        //Given
        String body = "" ;
        String expected = "(Empty)" ;
        //When
        when(htmlTextExtractor.toPlainText(body))
            .thenReturn(expected);
        String actual = testee.forHTMLBody(Optional.of(body));
        //Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void forTextBodyShouldReturnTruncatedStringWhenStringContainTagsAndIsLongerThan256Characters() {
        //Given
        String body = "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "This is a <b>HTML</b> mail containing <u>underlined part</u>, <i>italic part</i>88888888889999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "000000000011111111112222222222333333333344444444445555555";
        String expected = "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "This is a <b>HTML</b> mail containing <u>underlined part</u>, <i>italic part</i>88888888889999999999"
                + "00000000001111111111222222222233333333334444444444555...";
        //When
        String actual = testee.forTextBody(Optional.of(body));
        //Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void forTextBodyShouldReturnStringContainingEmptyWhenEmptyString() {
        //Given
        String expected = "(Empty)" ;
        //When
        String actual = testee.forTextBody(Optional.empty());
        //Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void asTextShouldReturnStringWithoutHtmlTag() {
        //Given
        String body = "This is a <b>HTML</b> mail";
        String expected = "This is a HTML mail";
        //When
        when(htmlTextExtractor.toPlainText(body))
            .thenReturn(expected);
        String actual = testee.asText(body);
        //Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test(expected = IllegalArgumentException.class)
    public void asTextShouldThrowWhenNullString () {
        testee.asText(null);
    }

    @Test
    public void asTextShouldReturnEmptyStringWhenEmptyString() {
        //Given
        String body = "";
        String expected = "";
        //When
        when(htmlTextExtractor.toPlainText(body))
            .thenReturn(expected);
        String actual = testee.asText(body);
        //Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void abbreviateShouldNotTruncateAbodyWith256Length() {
        //Given
        String body256 = "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "00000000001111111111222222222233333333334444444444555555";
        //When
        String actual = testee.abbreviate(body256);
        //Then
        assertThat(body256.length()).isEqualTo(256);
        assertThat(actual).isEqualTo(body256);
    }

    @Test
    public void abbreviateShouldTruncateAbodyWith257Length() {
        //Given
        String body257 = "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "000000000011111111112222222222333333333344444444445555555";
        String expected = "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "00000000001111111111222222222233333333334444444444555...";
        //When
        String actual = testee.abbreviate(body257);
        //Then
        assertThat(body257.length()).isEqualTo(257);
        assertThat(expected.length()).isEqualTo(256);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void abbreviateShouldReturnNullStringWhenNullString() {
        //Given
        String body = null;
        String expected = null;
        //When
        String actual = testee.abbreviate(body);
        //Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void abbreviateShouldReturnEmptyStringWhenEmptyString() {
        //Given
        String body = "";
        String expected = "";
        //When
        String actual = testee.abbreviate(body);
        //Then
        assertThat(actual).isEqualTo(expected);
    }

}
