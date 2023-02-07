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

package org.apache.james.jmap.draft.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Strings;

public class JsoupHtmlTextExtractorTest {

    private JsoupHtmlTextExtractor textExtractor;

    @Before
    public void setUp() {
        textExtractor = new JsoupHtmlTextExtractor();
    }

    @Test
    public void toPlainTextShouldNotModifyPlainText() {
        String textWithoutHtml = "text without html";
        assertThat(textExtractor.toPlainText(textWithoutHtml)).isEqualTo(textWithoutHtml);
    }

    @Test
    public void toPlainTextShouldRemoveSimpleHtmlTag() {
        String html = "This is an <b>HTML</b> text !";
        String expectedPlainText = "This is an HTML text !";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void toPlainTextShouldReplaceSkipLine() {
        String html = "<p>This is an<br/>HTML text !</p>";
        String expectedPlainText = "This is an\nHTML text !\n\n";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void toPlainTextShouldSkipLinesBetweenParagraph() {
        String html = "<p>para1</p><p>para2</p>";
        String expectedPlainText = "para1\n\npara2\n\n";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void deeplyNestedHtmlShouldNotThrowStackOverflow() {
        final int count = 2048;
        String html = Strings.repeat("<div>", count) +  "<p>para1</p><p>para2</p>" + Strings.repeat("</div>", count);
        String expectedPlainText = "para1\n\npara2\n\n";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void toPlainTextShouldConciderUpperCaseLabelsAsLowerCase() {
        String html = "<P>para1</P><p>para2</p>";
        String expectedPlainText = "para1\n\npara2\n\n";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void toPlainTextShouldHandleListsWell() {
        String html = "<ul>Here is my awesome list:" +
            "  <li>JMAP</li>" +
            "  <li>IMAP</li>" +
            "</ul>" +
            "<p>Followed with some text</p>" +
            "<p>And some other text</p>";
        String expectedPlainText = "Here is my awesome list:  \n" +
            " - JMAP  \n" +
            " - IMAP\n" +
            "\n" +
            "Followed with some text\n" +
            "\n" +
            "And some other text\n" +
            "\n";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void toPlainTextShouldHandleOrderedListsWell() {
        String html = "<ol>Here is my awesome list:" +
            "  <li>JMAP</li>" +
            "  <li>IMAP</li>" +
            "</ol>" +
            "<p>Followed with some text</p>" +
            "<p>And some other text</p>";
        String expectedPlainText = "Here is my awesome list:  \n" +
            " - JMAP  \n" +
            " - IMAP\n" +
            "\n" +
            "Followed with some text\n" +
            "\n" +
            "And some other text\n" +
            "\n";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void tableShouldBeWellHandled() {
        String html = " <table style=\"width:100%\">\n" +
            "  <tr>\n" +
            "    <th>Firstname</th>\n" +
            "    <th>Lastname</th>\n" +
            "    <th>Age</th>\n" +
            "  </tr>\n" +
            "  <tr>\n" +
            "    <td>Jill</td>\n" +
            "    <td>Smith</td>\n" +
            "    <td>50</td>\n" +
            "  </tr>\n" +
            "  <tr>\n" +
            "    <td>Eve</td>\n" +
            "    <td>Jackson</td>\n" +
            "    <td>94</td>\n" +
            "  </tr>\n" +
            "</table> ";
        String expectedPlainText = "\n" +
            "  \n" +
            "    Firstname\n" +
            "    Lastname\n" +
            "    Age\n" +
            "  \n" +
            "  \n" +
            "    Jill\n" +
            "    Smith\n" +
            "    50\n" +
            "  \n" +
            "  \n" +
            "    Eve\n" +
            "    Jackson\n" +
            "    94\n" +
            "  \n" +
            " ";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void imgShouldBeWellHandled() {
        String html = "<img src=\"whitePoney.png\" alt=\"My wonderfull white poney picture\"/>";
        String expectedPlainText = "[My wonderfull white poney picture]";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void imgShouldBeWellInsertedInText() {
        String html = "Text <img src=\"whitePoney.png\" alt=\"My wonderfull white poney picture\"/> text";
        String expectedPlainText = "Text [My wonderfull white poney picture] text";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void imgShouldNotBeDisplayedOnEmptyAlt() {
        String html = "Text <img src=\"whitePoney.png\" alt=\"\"/> text";
        String expectedPlainText = "Text  text";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void imgShouldNotBeDisplayedOnWhiteSpaceAlt() {
        String html = "Text <img src=\"whitePoney.png\" alt=\" \"/> text";
        String expectedPlainText = "Text  text";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void imgShouldNotBeDisplayedOnTabSpaceAlt() {
        String html = "Text <img src=\"whitePoney.png\" alt=\"\t\"/> text";
        String expectedPlainText = "Text  text";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void imgShouldNotBeDisplayedOnLineBreakSpaceAlt() {
        String html = "Text <img src=\"whitePoney.png\" alt=\"\n\"/> text";
        String expectedPlainText = "Text  text";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void imgShouldNotBeDisplayedOnMissingAlt() {
        String html = "Text <img src=\"whitePoney.png\"/> text";
        String expectedPlainText = "Text  text";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void nestedListsShouldBeWellHandled() {
        String html = " <ul>" +
            "  <li>Coffee</li>" +
            "  <li>Tea" +
            "    <ul>" +
            "      <li>Black tea</li>" +
            "      <li>Green tea</li>" +
            "    </ul>" +
            "  </li>" +
            "  <li>Milk</li>" +
            "</ul>";
        String expectedPlainText = "  \n" +
            " - Coffee  \n" +
            " - Tea          \n" +
            "  - Black tea      \n" +
            "  - Green tea        \n" +
            " - Milk\n" +
            "\n";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void nonClosedHtmlShouldBeTranslated() {
        String html = "This is an <b>HTML text !";
        String expectedPlainText = "This is an HTML text !";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void brokenHtmlShouldBeTranslatedUntilTheBrokenBalise() {
        String html = "This is an <b>HTML</b missing missing missing !";
        String expectedPlainText = "This is an HTML";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

    @Test
    public void toPlainTextShouldWorkWithMoreComplexHTML() throws Exception {
        String html = IOUtils.toString(ClassLoader.getSystemResource("example.html"), StandardCharsets.UTF_8);
        String expectedPlainText = "\n" +
            "    Why a new Logo?\n" +
            "\n" +
            "\n" +
            "    We are happy with our current logo, but for the\n" +
            "        upcoming James Server 3.0 release, we would like to\n" +
            "        give our community the opportunity to create a new image for James.\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "    Don't be shy, take your inkscape and gimp, and send us on\n" +
            "        the James Server User mailing list\n" +
            "        your creations. We will publish them on this page.\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "    We need an horizontal logo (100p height) to be show displayed on the upper\n" +
            "        left corner of this page, an avatar (48x48p) to be used on a Twitter stream for example.\n" +
            "        The used fonts should be redistributable (or commonly available on Windows and Linux).\n" +
            "        The chosen logo should be delivered in SVG format.\n" +
            "        We also like the Apache feather.\n" +
            "\n" +
            "\n" +
            "\n";
        assertThat(textExtractor.toPlainText(html)).isEqualTo(expectedPlainText);
    }

}
