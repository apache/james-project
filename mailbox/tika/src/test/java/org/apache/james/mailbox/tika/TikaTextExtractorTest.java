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

package org.apache.james.mailbox.tika;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.tika.TikaTextExtractor.ContentAndMetadataDeserializer;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.google.common.collect.ImmutableList;

public class TikaTextExtractorTest {

    private TextExtractor textExtractor;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @ClassRule
    public static TikaContainer tika = new TikaContainer();

    @Before
    public void setUp() throws Exception {
        textExtractor = new TikaTextExtractor(new NoopMetricFactory(), new TikaHttpClientImpl(TikaConfiguration.builder()
                .host(tika.getIp())
                .port(tika.getPort())
                .timeoutInMillis(tika.getTimeoutInMillis())
                .build()));
    }

    @Test
    public void textualContentShouldReturnNullWhenInputStreamIsEmpty() throws Exception {
        assertThat(textExtractor.extractContent(IOUtils.toInputStream("", StandardCharsets.UTF_8), "text/plain").getTextualContent())
            .isEmpty();
    }

    @Test
    public void textTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/Text.txt");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, "text/plain").getTextualContent())
            .contains("This is some awesome text text.\n\n\n");
    }

    @Test
    public void textMicrosoftWorldTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/writter.docx");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, "application/vnd.openxmlformats-officedocument.wordprocessingml.document").getTextualContent())
            .contains("This is an awesome document on libroffice writter !\n");
    }

    @Test
    public void textOdtTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/writter.odt");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, "application/vnd.oasis.opendocument.text").getTextualContent())
            .contains("This is an awesome document on libroffice writter !\n");
    }

    @Test
    public void documentWithBadDeclaredMetadataShouldBeWellHandled() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/fake.txt");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, "application/vnd.oasis.opendocument.text").getTextualContent())
            .contains("This is an awesome document on libroffice writter !\n");
    }
    
    @Test
    public void slidePowerPointTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/slides.pptx");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, "application/vnd.openxmlformats-officedocument.presentationml.presentation").getTextualContent())
            .contains("James is awesome\nIt manages attachments so well !\n\n\n");
    }

    @Test
    public void slideOdpTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/slides.odp");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, "application/vnd.oasis.opendocument.presentation").getTextualContent())
            .contains("James is awesome\n\nIt manages attachments so well !\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");
    }
    
    @Test
    public void pdfTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/PDF.pdf");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, "application/pdf").getTextualContent())
            .contains("This is an awesome document on libroffice writter !\n\n\n");
    }
    
    @Test
    public void odsTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/calc.ods");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, "application/vnd.oasis.opendocument.spreadsheet").getTextualContent())
            .contains("This is an aesome LibreOffice document !\n" +
                "\n" +
                "\n" +
                "???\n" +
                "Page \n" +
                "??? (???)\n" +
                "00/00/0000, 00:00:00\n" +
                "Page  / \n");
    }
    
    @Test
    public void excelTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/calc.xlsx");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").getTextualContent())
            .contains("Feuille1\n" +
                "\tThis is an aesome LibreOffice document !\n" +
                "\n" +
                "&A\t\n" +
                "\n" +
                "Page &P\t\n" +
                "\n" +
                "\n");
    }

    @Test
    public void deserializerShouldNotThrowWhenMoreThanOneNode() throws Exception {
        TikaTextExtractor textExtractor = new TikaTextExtractor(
            new NoopMetricFactory(), (inputStream, contentType) -> new ByteArrayInputStream(("[{\"X-TIKA:content\": \"This is an awesome LibreOffice document !\"}, " +
                "{\"Chroma BlackIsZero\": \"true\"}]").getBytes(StandardCharsets.UTF_8)));

        InputStream inputStream = null;
        textExtractor.extractContent(inputStream, "text/plain");
    }

    @Test
    public void deserializerShouldTakeFirstNodeWhenSeveral() throws Exception {
        String expectedExtractedContent = "content A";
        TikaTextExtractor textExtractor = new TikaTextExtractor(
            new NoopMetricFactory(), (inputStream, contentType) -> new ByteArrayInputStream(("[{\"X-TIKA:content\": \"" + expectedExtractedContent + "\"}, " +
                "{\"X-TIKA:content\": \"content B\"}]").getBytes(StandardCharsets.UTF_8)));

        InputStream inputStream = null;
        ParsedContent parsedContent = textExtractor.extractContent(inputStream, "text/plain");

        assertThat(parsedContent.getTextualContent()).contains(expectedExtractedContent);
    }

    @Test
    public void deserializerShouldThrowWhenNodeIsNotAnObject() throws Exception {
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("The element should be a Json object");

        TikaTextExtractor textExtractor = new TikaTextExtractor(
            new NoopMetricFactory(), (inputStream, contentType) -> new ByteArrayInputStream("[\"value1\"]".getBytes(StandardCharsets.UTF_8)));

        InputStream inputStream = null;
        textExtractor.extractContent(inputStream, "text/plain");
    }

    @Test
    public void asListOfStringShouldReturnASingletonWhenOneElement() {
        JsonNode jsonNode = mock(JsonNode.class);
        when(jsonNode.getNodeType())
            .thenReturn(JsonNodeType.STRING);
        String expectedContent = "text";
        when(jsonNode.asText())
            .thenReturn(expectedContent);
        
        ContentAndMetadataDeserializer deserializer = new TikaTextExtractor.ContentAndMetadataDeserializer();
        List<String> listOfString = deserializer.asListOfString(jsonNode);
        
        assertThat(listOfString).containsOnly(expectedContent);
    }

    @Test
    public void asListOfStringShouldReturnAListWhenMultipleElements() {
        JsonNode mainNode = mock(JsonNode.class);
        when(mainNode.getNodeType())
            .thenReturn(JsonNodeType.ARRAY);
        JsonNode firstNode = mock(JsonNode.class);
        when(firstNode.asText())
            .thenReturn("first");
        JsonNode secondNode = mock(JsonNode.class);
        when(secondNode.asText())
            .thenReturn("second");
        JsonNode thirdNode = mock(JsonNode.class);
        when(thirdNode.asText())
            .thenReturn("third");
        when(mainNode.elements())
            .thenReturn(ImmutableList.of(firstNode, secondNode, thirdNode).iterator());
        
        ContentAndMetadataDeserializer deserializer = new TikaTextExtractor.ContentAndMetadataDeserializer();
        List<String> listOfString = deserializer.asListOfString(mainNode);
        
        assertThat(listOfString).containsOnly("first", "second", "third");
    }
}
