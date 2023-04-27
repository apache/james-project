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

package org.apache.james.imap.decode.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.request.DayMonthYear;
import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.api.message.request.SearchResultOption;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.imap.decode.parser.SearchCommandParser.Context;
import org.apache.james.mailbox.MessageUid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchCommandParserSearchKeyTest {

    private static final DayMonthYear DATE = new DayMonthYear(1, 1, 2000);

    SearchCommandParser parser;
    ImapCommand command;

    @BeforeEach
    void setUp() {
        parser = new SearchCommandParser(mock(StatusResponseFactory.class));
        command = ImapCommand.anyStateCommand("Command");
    }

    @Test
    void testShouldParseAll() throws Exception {
        SearchKey key = SearchKey.buildAll();
        checkValid("ALL\r\n", key);
        checkValid("all\r\n", key);
        checkValid("alL\r\n", key);
        checkInvalid("al\r\n");
        checkInvalid("alm\r\n");
        checkInvalid("alm\r\n");
    }

    @Test
    void testShouldParseAnswered() throws Exception {
        SearchKey key = SearchKey.buildAnswered();
        checkValid("ANSWERED\r\n", key);
        checkValid("answered\r\n", key);
        checkValid("aNSWEred\r\n", key);
        checkInvalid("a\r\n");
        checkInvalid("an\r\n");
        checkInvalid("ans\r\n");
        checkInvalid("answ\r\n");
        checkInvalid("answe\r\n");
        checkInvalid("answer\r\n");
        checkInvalid("answere\r\n");
    }

    @Test
    void testShouldParseYounger() throws Exception {
        SearchKey key = SearchKey.buildYounger(123);
        checkValid("younger 123\r\n", key);
        checkValid("YOUNGER 123\r\n", key);
        checkValid("yOunGeR  123\r\n", key);
        checkInvalid("y\r\n");
        checkInvalid("yo\r\n");
        checkInvalid("you\r\n");
        checkInvalid("youn\r\n");
        checkInvalid("young\r\n");
        checkInvalid("younge\r\n");
        checkInvalid("younger\r\n");
        checkInvalid("younger \r\n");
    }

    @Test
    void testShouldParseOlder() throws Exception {
        SearchKey key = SearchKey.buildOlder(123);
        checkValid("older 123\r\n", key);
        checkValid("OLDER 123\r\n", key);
        checkValid("OlDeR  123\r\n", key);
    }

    @Test
    void testShouldParseBcc() throws Exception {
        SearchKey key = SearchKey.buildBcc("Somebody");
        checkValid("BCC Somebody\r\n", key);
        checkValid("BCC \"Somebody\"\r\n", key);
        checkValid("bcc Somebody\r\n", key);
        checkValid("bcc \"Somebody\"\r\n", key);
        checkValid("Bcc Somebody\r\n", key);
        checkValid("Bcc \"Somebody\"\r\n", key);
        checkInvalid("b\r\n");
        checkInvalid("bc\r\n");
        checkInvalid("bg\r\n");
        checkInvalid("bccc\r\n");
    }

    @Test
    void testShouldParseOn() throws Exception {
        SearchKey key = SearchKey.buildOn(DATE);
        checkValid("ON 1-Jan-2000\r\n", key);
        checkValid("on 1-Jan-2000\r\n", key);
        checkValid("oN 1-Jan-2000\r\n", key);
        checkInvalid("o\r\n");
        checkInvalid("om\r\n");
        checkInvalid("oni\r\n");
        checkInvalid("on \r\n");
        checkInvalid("on 1\r\n");
        checkInvalid("on 1-\r\n");
        checkInvalid("on 1-J\r\n");
        checkInvalid("on 1-Ja\r\n");
        checkInvalid("on 1-Jan\r\n");
        checkInvalid("on 1-Jan-\r\n");
    }

    @Test
    void testShouldParseSentBefore() throws Exception {
        SearchKey key = SearchKey.buildSentBefore(DATE);
        checkValid("SENTBEFORE 1-Jan-2000\r\n", key);
        checkValid("sentbefore 1-Jan-2000\r\n", key);
        checkValid("SentBefore 1-Jan-2000\r\n", key);
        checkInvalid("s\r\n");
        checkInvalid("se\r\n");
        checkInvalid("sent\r\n");
        checkInvalid("seny\r\n");
        checkInvalid("sentb \r\n");
        checkInvalid("sentbe \r\n");
        checkInvalid("sentbef \r\n");
        checkInvalid("sentbefo \r\n");
        checkInvalid("sentbefor \r\n");
        checkInvalid("sentbefore \r\n");
        checkInvalid("sentbefore 1\r\n");
        checkInvalid("sentbefore 1-\r\n");
        checkInvalid("sentbefore 1-J\r\n");
        checkInvalid("sentbefore 1-Ja\r\n");
        checkInvalid("sentbefore 1-Jan\r\n");
        checkInvalid("sentbefore 1-Jan-\r\n");
    }

    @Test
    void testShouldParseSentOn() throws Exception {
        SearchKey key = SearchKey.buildSentOn(DATE);
        checkValid("SENTON 1-Jan-2000\r\n", key);
        checkValid("senton 1-Jan-2000\r\n", key);
        checkValid("SentOn 1-Jan-2000\r\n", key);
        checkInvalid("s\r\n");
        checkInvalid("se\r\n");
        checkInvalid("sent\r\n");
        checkInvalid("sento \r\n");
        checkInvalid("senton \r\n");
        checkInvalid("senton 1\r\n");
        checkInvalid("senton 1-\r\n");
        checkInvalid("senton 1-J\r\n");
        checkInvalid("senton 1-Ja\r\n");
        checkInvalid("senton 1-Jan\r\n");
        checkInvalid("senton 1-Jan-\r\n");
    }

    @Test
    void testShouldParseSentSince() throws Exception {
        SearchKey key = SearchKey.buildSentSince(DATE);
        checkValid("SENTSINCE 1-Jan-2000\r\n", key);
        checkValid("sentsince 1-Jan-2000\r\n", key);
        checkValid("SentSince 1-Jan-2000\r\n", key);
        checkInvalid("s\r\n");
        checkInvalid("se\r\n");
        checkInvalid("sent\r\n");
        checkInvalid("sents \r\n");
        checkInvalid("sentsi \r\n");
        checkInvalid("sentsin \r\n");
        checkInvalid("sentsinc \r\n");
        checkInvalid("sentsince \r\n");
        checkInvalid("sentsince 1\r\n");
        checkInvalid("sentsince 1-\r\n");
        checkInvalid("sentsince 1-J\r\n");
        checkInvalid("sentsince 1-Ja\r\n");
        checkInvalid("sentsince 1-Jan\r\n");
        checkInvalid("sentsince 1-Jan-\r\n");
    }

    @Test
    void testShouldParseSince() throws Exception {
        SearchKey key = SearchKey.buildSince(DATE);
        checkValid("SINCE 1-Jan-2000\r\n", key);
        checkValid("since 1-Jan-2000\r\n", key);
        checkValid("Since 1-Jan-2000\r\n", key);
        checkInvalid("s \r\n");
        checkInvalid("si \r\n");
        checkInvalid("sin \r\n");
        checkInvalid("sinc \r\n");
        checkInvalid("since \r\n");
        checkInvalid("since 1\r\n");
        checkInvalid("since 1-\r\n");
        checkInvalid("since 1-J\r\n");
        checkInvalid("since 1-Ja\r\n");
        checkInvalid("since 1-Jan\r\n");
        checkInvalid("since 1-Jan-\r\n");
    }

    @Test
    void testShouldParseBefore() throws Exception {
        SearchKey key = SearchKey.buildBefore(DATE);
        checkValid("BEFORE 1-Jan-2000\r\n", key);
        checkValid("before 1-Jan-2000\r\n", key);
        checkValid("BEforE 1-Jan-2000\r\n", key);
        checkInvalid("b\r\n");
        checkInvalid("B\r\n");
        checkInvalid("BE\r\n");
        checkInvalid("BEf\r\n");
        checkInvalid("BEfo\r\n");
        checkInvalid("BEfor\r\n");
        checkInvalid("BEforE\r\n");
        checkInvalid("BEforEi\r\n");
        checkInvalid("BEforE \r\n");
        checkInvalid("BEforE 1\r\n");
        checkInvalid("BEforE 1-\r\n");
        checkInvalid("BEforE 1-J\r\n");
        checkInvalid("BEforE 1-Ja\r\n");
        checkInvalid("BEforE 1-Jan\r\n");
        checkInvalid("BEforE 1-Jan-\r\n");
    }

    @Test
    void testShouldParseBody() throws Exception {
        SearchKey key = SearchKey.buildBody("Text");
        checkValid("BODY Text\r\n", key);
        checkValid("BODY \"Text\"\r\n", key);
        checkValid("body Text\r\n", key);
        checkValid("body \"Text\"\r\n", key);
        checkValid("BodY Text\r\n", key);
        checkValid("BodY \"Text\"\r\n", key);
        checkInvalid("b\r\n");
        checkInvalid("Bo\r\n");
        checkInvalid("Bod\r\n");
        checkInvalid("Bodd\r\n");
        checkInvalid("Bodym\r\n");
    }

    @Test
    void testShouldParseTo() throws Exception {
        SearchKey key = SearchKey.buildTo("AnAddress");
        checkValid("TO AnAddress\r\n", key);
        checkValid("TO \"AnAddress\"\r\n", key);
        checkValid("to AnAddress\r\n", key);
        checkValid("to \"AnAddress\"\r\n", key);
        checkValid("To AnAddress\r\n", key);
        checkValid("To \"AnAddress\"\r\n", key);
        checkInvalid("t\r\n");
        checkInvalid("to\r\n");
        checkInvalid("too\r\n");
        checkInvalid("to \r\n");
    }

    @Test
    void testShouldParseText() throws Exception {
        SearchKey key = SearchKey.buildText("SomeText");
        checkValid("TEXT SomeText\r\n", key);
        checkValid("TEXT \"SomeText\"\r\n", key);
        checkValid("text SomeText\r\n", key);
        checkValid("text \"SomeText\"\r\n", key);
        checkValid("Text SomeText\r\n", key);
        checkValid("Text \"SomeText\"\r\n", key);
        checkInvalid("t\r\n");
        checkInvalid("te\r\n");
        checkInvalid("tex\r\n");
        checkInvalid("text\r\n");
        checkInvalid("text \r\n");
    }

    @Test
    void testShouldParseSubject() throws Exception {
        SearchKey key = SearchKey.buildSubject("ASubject");
        checkValid("SUBJECT ASubject\r\n", key);
        checkValid("SUBJECT \"ASubject\"\r\n", key);
        checkValid("subject ASubject\r\n", key);
        checkValid("subject \"ASubject\"\r\n", key);
        checkValid("Subject ASubject\r\n", key);
        checkValid("Subject \"ASubject\"\r\n", key);
        checkInvalid("s\r\n");
        checkInvalid("su\r\n");
        checkInvalid("sub\r\n");
        checkInvalid("subj\r\n");
        checkInvalid("subje\r\n");
        checkInvalid("subjec\r\n");
        checkInvalid("subject\r\n");
        checkInvalid("subject \r\n");
    }

    @Test
    void testShouldParseCc() throws Exception {
        SearchKey key = SearchKey.buildCc("SomeText");
        checkValid("CC SomeText\r\n", key);
        checkValid("CC \"SomeText\"\r\n", key);
        checkValid("cc SomeText\r\n", key);
        checkValid("cc \"SomeText\"\r\n", key);
        checkValid("Cc SomeText\r\n", key);
        checkValid("Cc \"SomeText\"\r\n", key);
        checkInvalid("c\r\n");
        checkInvalid("cd\r\n");
        checkInvalid("ccc\r\n");
    }

    @Test
    void testShouldParseFrom() throws Exception {
        SearchKey key = SearchKey.buildFrom("Someone");
        checkValid("FROM Someone\r\n", key);
        checkValid("FROM \"Someone\"\r\n", key);
        checkValid("from Someone\r\n", key);
        checkValid("from \"Someone\"\r\n", key);
        checkValid("FRom Someone\r\n", key);
        checkValid("FRom \"Someone\"\r\n", key);
        checkInvalid("f\r\n");
        checkInvalid("fr\r\n");
        checkInvalid("ftom\r\n");
        checkInvalid("froml\r\n");
    }

    @Test
    void testShouldParseKeyword() throws Exception {
        SearchKey key = SearchKey.buildKeyword("AFlag");
        checkValid("KEYWORD AFlag\r\n", key);
        checkInvalid("KEYWORD \"AFlag\"\r\n");
        checkValid("keyword AFlag\r\n", key);
        checkInvalid("keyword \"AFlag\"\r\n");
        checkValid("KEYword AFlag\r\n", key);
        checkInvalid("KEYword \"AFlag\"\r\n");
        checkInvalid("k\r\n");
        checkInvalid("ke\r\n");
        checkInvalid("key\r\n");
        checkInvalid("keyw\r\n");
        checkInvalid("keywo\r\n");
        checkInvalid("keywor\r\n");
        checkInvalid("keywordi\r\n");
        checkInvalid("keyword \r\n");
    }

    @Test
    void testShouldParseUnKeyword() throws Exception {
        SearchKey key = SearchKey.buildUnkeyword("AFlag");
        checkValid("UNKEYWORD AFlag\r\n", key);
        checkInvalid("UNKEYWORD \"AFlag\"\r\n");
        checkValid("unkeyword AFlag\r\n", key);
        checkInvalid("unkeyword \"AFlag\"\r\n");
        checkValid("UnKEYword AFlag\r\n", key);
        checkInvalid("UnKEYword \"AFlag\"\r\n");
        checkInvalid("u\r\n");
        checkInvalid("un\r\n");
        checkInvalid("unk\r\n");
        checkInvalid("unke\r\n");
        checkInvalid("unkey\r\n");
        checkInvalid("unkeyw\r\n");
        checkInvalid("unkeywo\r\n");
        checkInvalid("unkeywor\r\n");
        checkInvalid("unkeywordi\r\n");
        checkInvalid("unkeyword \r\n");
    }

    @Test
    void testShouldParseHeader() throws Exception {
        SearchKey key = SearchKey.buildHeader("Field", "Value");
        checkValid("HEADER Field Value\r\n", key);
        checkValid("HEADER \"Field\" \"Value\"\r\n", key);
        checkValid("header Field Value\r\n", key);
        checkValid("header \"Field\" \"Value\"\r\n", key);
        checkValid("HEAder Field Value\r\n", key);
        checkValid("HEAder \"Field\" \"Value\"\r\n", key);
        checkInvalid("h\r\n");
        checkInvalid("he\r\n");
        checkInvalid("hea\r\n");
        checkInvalid("head\r\n");
        checkInvalid("heade\r\n");
        checkInvalid("header\r\n");
        checkInvalid("header field\r\n");
        checkInvalid("header field \r\n");
    }

   
    private void checkValid(String input, SearchKey key) throws Exception {
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayOutputStream());

        assertThat(parser.searchKey(null, reader, new Context(), false)).isEqualTo(key);
    }

    @Test
    void testShouldParseDeleted() throws Exception {
        SearchKey key = SearchKey.buildDeleted();
        checkValid("DELETED\r\n", key);
        checkValid("deleted\r\n", key);
        checkValid("deLEteD\r\n", key);
        checkInvalid("d\r\n");
        checkInvalid("de\r\n");
        checkInvalid("del\r\n");
        checkInvalid("dele\r\n");
        checkInvalid("delet\r\n");
        checkInvalid("delete\r\n");
    }

    @Test
    void testEShouldBeInvalid() {
        checkInvalid("e\r\n");
        checkInvalid("ee\r\n");
    }

    @Test
    void testGShouldBeInvalid() {
        checkInvalid("g\r\n");
        checkInvalid("G\r\n");
    }

    @Test
    void testIShouldBeInvalid() {
        checkInvalid("i\r\n");
        checkInvalid("I\r\n");
    }

    @Test
    void testJShouldBeInvalid() {
        checkInvalid("j\r\n");
        checkInvalid("J\r\n");
    }

    @Test
    void testMShouldBeInvalid() {
        checkInvalid("m\r\n");
        checkInvalid("M\r\n");
    }
    
    @Test
    void testPShouldBeInvalid() {
        checkInvalid("p\r\n");
        checkInvalid("Pp\r\n");
    }

    @Test
    void testQShouldBeInvalid() {
        checkInvalid("q\r\n");
        checkInvalid("Qq\r\n");
    }

    @Test
    void testWShouldBeInvalid() {
        checkInvalid("w\r\n");
        checkInvalid("ww\r\n");
    }

    @Test
    void testVShouldBeInvalid() {
        SearchKey key = SearchKey.buildDeleted();
        checkInvalid("v\r\n");
        checkInvalid("vv\r\n");
    }

    @Test
    void testXShouldBeInvalid() {
        SearchKey key = SearchKey.buildDeleted();
        checkInvalid("x\r\n");
        checkInvalid("xx\r\n");
    }

    @Test
    void testYShouldBeInvalid() {
        SearchKey key = SearchKey.buildDeleted();
        checkInvalid("y\r\n");
        checkInvalid("yy\r\n");
    }

    @Test
    void testZShouldBeInvalid() {
        SearchKey key = SearchKey.buildDeleted();
        checkInvalid("z\r\n");
        checkInvalid("zz\r\n");
    }

    @Test
    void testShouldParseRecent() throws Exception {
        SearchKey key = SearchKey.buildRecent();
        checkValid("RECENT\r\n", key);
        checkValid("recent\r\n", key);
        checkValid("reCENt\r\n", key);
        checkInvalid("r\r\n");
        checkInvalid("re\r\n");
        checkInvalid("rec\r\n");
        checkInvalid("rece\r\n");
        checkInvalid("recen\r\n");
    }

    @Test
    void testShouldParseDraft() throws Exception {
        SearchKey key = SearchKey.buildDraft();
        checkValid("DRAFT\r\n", key);
        checkValid("draft\r\n", key);
        checkValid("DRaft\r\n", key);
        checkInvalid("D\r\n");
        checkInvalid("DR\r\n");
        checkInvalid("DRA\r\n");
        checkInvalid("DRAF\r\n");
    }

    @Test
    void testShouldParseUnanswered() throws Exception {
        SearchKey key = SearchKey.buildUnanswered();
        checkValid("UNANSWERED\r\n", key);
        checkValid("unanswered\r\n", key);
        checkValid("UnAnswered\r\n", key);
        checkInvalid("u\r\n");
        checkInvalid("un\r\n");
        checkInvalid("una\r\n");
        checkInvalid("unan\r\n");
        checkInvalid("unans\r\n");
        checkInvalid("unansw\r\n");
        checkInvalid("unanswe\r\n");
        checkInvalid("unanswer\r\n");
        checkInvalid("unanswere\r\n");
    }

    @Test
    void testShouldParseUndeleted() throws Exception {
        SearchKey key = SearchKey.buildUndeleted();
        checkValid("UNDELETED\r\n", key);
        checkValid("undeleted\r\n", key);
        checkValid("UnDeleted\r\n", key);
        checkInvalid("u\r\n");
        checkInvalid("un\r\n");
        checkInvalid("und\r\n");
        checkInvalid("unde\r\n");
        checkInvalid("undel\r\n");
        checkInvalid("undele\r\n");
        checkInvalid("undelet\r\n");
        checkInvalid("undelete\r\n");
    }

    @Test
    void testShouldParseUnseen() throws Exception {
        SearchKey key = SearchKey.buildUnseen();
        checkValid("UNSEEN\r\n", key);
        checkValid("unseen\r\n", key);
        checkValid("UnSeen\r\n", key);
        checkInvalid("u\r\n");
        checkInvalid("un\r\n");
        checkInvalid("uns\r\n");
        checkInvalid("unse\r\n");
        checkInvalid("unsee\r\n");
    }

    @Test
    void testShouldParseUndraft() throws Exception {
        SearchKey key = SearchKey.buildUndraft();
        checkValid("UNDRAFT\r\n", key);
        checkValid("undraft\r\n", key);
        checkValid("UnDraft\r\n", key);
        checkInvalid("u\r\n");
        checkInvalid("un\r\n");
        checkInvalid("und\r\n");
        checkInvalid("undr\r\n");
        checkInvalid("undra\r\n");
        checkInvalid("undraf\r\n");
    }

    @Test
    void testShouldParseUnflagged() throws Exception {
        SearchKey key = SearchKey.buildUnflagged();
        checkValid("UNFLAGGED\r\n", key);
        checkValid("unflagged\r\n", key);
        checkValid("UnFlagged\r\n", key);
        checkInvalid("u\r\n");
        checkInvalid("un\r\n");
        checkInvalid("unf\r\n");
        checkInvalid("unfl\r\n");
        checkInvalid("unfla\r\n");
        checkInvalid("unflag\r\n");
        checkInvalid("unflagg\r\n");
        checkInvalid("unflagge\r\n");
    }

    @Test
    void testShouldParseSeen() throws Exception {
        SearchKey key = SearchKey.buildSeen();
        checkValid("SEEN\r\n", key);
        checkValid("seen\r\n", key);
        checkValid("SEen\r\n", key);
        checkInvalid("s\r\n");
        checkInvalid("se\r\n");
        checkInvalid("see\r\n");
    }

    @Test
    void testShouldParseNew() throws Exception {
        SearchKey key = SearchKey.buildNew();
        checkValid("NEW\r\n", key);
        checkValid("new\r\n", key);
        checkValid("NeW\r\n", key);
        checkInvalid("n\r\n");
        checkInvalid("ne\r\n");
        checkInvalid("nwe\r\n");
    }

    @Test
    void testShouldParseOld() throws Exception {
        SearchKey key = SearchKey.buildOld();
        checkValid("OLD\r\n", key);
        checkValid("old\r\n", key);
        checkValid("oLd\r\n", key);
        checkInvalid("o\r\n");
        checkInvalid("ol\r\n");
        checkInvalid("olr\r\n");
    }

    @Test
    void testShouldParseFlagged() throws Exception {
        SearchKey key = SearchKey.buildFlagged();
        checkValid("FLAGGED\r\n", key);
        checkValid("flagged\r\n", key);
        checkValid("FLAGged\r\n", key);
        checkInvalid("F\r\n");
        checkInvalid("FL\r\n");
        checkInvalid("FLA\r\n");
        checkInvalid("FLAG\r\n");
        checkInvalid("FLAGG\r\n");
        checkInvalid("FLAGGE\r\n");
        checkInvalid("FLoas\r\n");
    }

    @Test
    void testShouldParseSmaller() throws Exception {
        SearchKey key = SearchKey.buildSmaller(1729);
        checkValid("SMALLER 1729\r\n", key);
        checkValid("smaller 1729\r\n", key);
        checkValid("SMaller 1729\r\n", key);
        checkInvalid("s\r\n");
        checkInvalid("sm\r\n");
        checkInvalid("sma\r\n");
        checkInvalid("smal\r\n");
        checkInvalid("small\r\n");
        checkInvalid("smalle\r\n");
        checkInvalid("smaller \r\n");
        checkInvalid("smaller peach\r\n");
    }

    @Test
    void testShouldParseLarger() throws Exception {
        SearchKey key = SearchKey.buildLarger(1234);
        checkValid("LARGER 1234\r\n", key);
        checkValid("lArGEr 1234\r\n", key);
        checkValid("larger 1234\r\n", key);
        checkInvalid("l\r\n");
        checkInvalid("la\r\n");
        checkInvalid("lar\r\n");
        checkInvalid("larg\r\n");
        checkInvalid("large\r\n");
        checkInvalid("larger\r\n");
        checkInvalid("larger \r\n");
        checkInvalid("larger peach\r\n");
    }

    @Test
    void testShouldParseUid() throws Exception {
        UidRange[] range = { new UidRange(MessageUid.of(1)) };
        SearchKey key = SearchKey.buildUidSet(range);
        checkValid("UID 1\r\n", key);
        checkValid("Uid 1\r\n", key);
        checkValid("uid 1\r\n", key);
        checkInvalid("u\r\n");
        checkInvalid("ui\r\n");
        checkInvalid("uid\r\n");
        checkInvalid("uid \r\n");
    }

    @Test
    void testShouldParseNot() throws Exception {
        SearchKey notdKey = SearchKey.buildSeen();
        SearchKey key = SearchKey.buildNot(notdKey);
        checkValid("NOT SEEN\r\n", key);
        checkValid("Not seen\r\n", key);
        checkValid("not Seen\r\n", key);
        checkInvalid("n\r\n");
        checkInvalid("no\r\n");
        checkInvalid("not\r\n");
        checkInvalid("not \r\n");
    }

    @Test
    void testSearchOptionWhenEmpty() throws Exception {
        assertThat(parseOptions("()"))
            .containsOnly(SearchResultOption.ALL);
    }

    @Test
    void testSearchOptionShouldThrowWhenMBAD() {
        assertThatThrownBy(() -> parseOptions("(MBAD)"))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void testSearchOptionShouldThrowWhenBAD() {
        assertThatThrownBy(() -> parseOptions("(BAD)"))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void savedWithInvalidSuffixShouldFail() {
        checkInvalid("savedy\r\n");
    }

    @Test
    void testShouldParseOr() throws Exception {
        SearchKey oneKey = SearchKey.buildSeen();
        SearchKey twoKey = SearchKey.buildDraft();
        SearchKey key = SearchKey.buildOr(oneKey, twoKey);
        checkValid("OR SEEN DRAFT\r\n", key);
        checkValid("oR seen draft\r\n", key);
        checkValid("or Seen drAFT\r\n", key);
        checkInvalid("o\r\n");
        checkInvalid("or\r\n");
        checkInvalid("or \r\n");
        checkInvalid("or seen\r\n");
        checkInvalid("or seen \r\n");
    }

    @Test
    void testShouldParseSequenceSet() throws Exception {
        checkSequenceSet(1);
        checkSequenceSet(2);
        checkSequenceSet(3);
        checkSequenceSet(4);
        checkSequenceSet(5);
        checkSequenceSet(6);
        checkSequenceSet(7);
        checkSequenceSet(8);
        checkSequenceSet(9);
        checkSequenceSet(10);
        checkSequenceSet(121);
        checkSequenceSet(11354);
        checkSequenceSet(145644656);
        checkSequenceSet(1456452213);
    }

    private void checkSequenceSet(int number) throws Exception {
        IdRange[] range = { new IdRange(number) };
        SearchKey key = SearchKey.buildSequenceSet(range);
        checkValid(number + "\r\n", key);
    }

    private void checkInvalid(String input) {
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayOutputStream());

        try {
            parser.searchKey(null, reader, new Context(), false);
            fail("Expected protocol exception to be throw since input is invalid");
        } catch (DecodingException e) {
            // expected
        }
    }

    private List<SearchResultOption> parseOptions(String input) throws DecodingException {
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayOutputStream());

        return parser.parseOptions(reader);
    }
}
