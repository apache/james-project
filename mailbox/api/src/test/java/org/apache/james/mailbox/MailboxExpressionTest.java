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

package org.apache.james.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.junit.Test;

public class MailboxExpressionTest {

    private static final String PART = "mailbox";

    private static final String SECOND_PART = "sub";

    private static final String BASE = "BASE";
    private static final MailboxPath BASE_PATH = new MailboxPath(null, null, BASE);


    private MailboxQuery create(String expression) {
        return new MailboxQuery(BASE_PATH, expression, '.');
    }

    @Test
    public void testIsWild() throws Exception {
        assertTrue(create("*").isWild());
        assertTrue(create("%").isWild());
        assertTrue(create("One*").isWild());
        assertTrue(create("*One").isWild());
        assertTrue(create("A*A").isWild());
        assertTrue(create("One%").isWild());
        assertTrue(create("%One").isWild());
        assertTrue(create("A%A").isWild());
        assertFalse(create("").isWild());
        assertFalse(create(null).isWild());
        assertFalse(create("ONE").isWild());
    }

    @Test
    public void combinedNameWithNoExpressionShouldReturnBase() throws Exception {
        MailboxQuery expression = new MailboxQuery(BASE_PATH, "", '.');
        assertThat(expression.getCombinedName()).isEqualTo(BASE);
    }

    @Test
    public void combinedNameOnNullShouldBeEmpty() throws Exception {
        MailboxQuery expression = new MailboxQuery(new MailboxPath(null, null, null), null, '.');
        assertThat(expression.getCombinedName()).isEmpty();
    }

    @Test
    public void combinedNameShouldContainBaseAndPart() throws Exception {
        MailboxQuery expression = new MailboxQuery(BASE_PATH, PART, '.');
        assertThat(expression.getCombinedName()).isEqualTo(BASE + "." + PART);
    }

    @Test
    public void combinedNameOnPartStartingWithDelimiterShouldIgnoreDelimiter() throws Exception {
        MailboxQuery expression = new MailboxQuery(BASE_PATH, "." + PART, '.');
        assertThat(expression.getCombinedName()).isEqualTo(BASE + "." + PART);
    }

    @Test
    public void combinedNameOnBaseEndingWithDelimiterShouldIgnoreDelimiter() throws Exception {
        MailboxQuery expression = new MailboxQuery(new MailboxPath(null, null, BASE + '.'), PART, '.');
        assertThat(expression.getCombinedName()).isEqualTo(BASE + "." + PART);
    }

    @Test
    public void combinedNameShouldIgnoreAllDelimiters()
            throws Exception {
        MailboxQuery expression = new MailboxQuery(new MailboxPath(null, null, BASE + '.'), '.' + PART, '.');
        assertThat(expression.getCombinedName()).isEqualTo(BASE + "." + PART);
    }

    @Test
    public void testSimpleExpression() throws Exception {
        MailboxQuery expression = create(PART);
        assertTrue(expression.isExpressionMatch(PART));
        assertFalse(expression.isExpressionMatch('.' + PART));
        assertFalse(expression.isExpressionMatch(PART + '.'));
        assertFalse(expression.isExpressionMatch(SECOND_PART));
    }

    @Test
    public void testEmptyExpression() throws Exception {
        MailboxQuery expression = create("");
        assertTrue(expression.isExpressionMatch(""));
        assertFalse(expression.isExpressionMatch("whatever"));
        assertFalse(expression.isExpressionMatch(BASE + '.' + "whatever"));
        assertFalse(expression.isExpressionMatch(BASE + "whatever"));
    }

    @Test
    public void testOnlyLocalWildcard() throws Exception {
        MailboxQuery expression = create("%");
        assertTrue(expression.isExpressionMatch(""));
        assertTrue(expression.isExpressionMatch(SECOND_PART));
        assertTrue(expression.isExpressionMatch(PART));
        assertTrue(expression.isExpressionMatch(PART + SECOND_PART));
        assertFalse(expression.isExpressionMatch(PART + '.' + SECOND_PART));
    }

    @Test
    public void testOnlyFreeWildcard() throws Exception {
        MailboxQuery expression = create("*");
        assertTrue(expression.isExpressionMatch(""));
        assertTrue(expression.isExpressionMatch(SECOND_PART));
        assertTrue(expression.isExpressionMatch(PART));
        assertTrue(expression.isExpressionMatch(PART + SECOND_PART));
        assertTrue(expression.isExpressionMatch(PART + '.' + SECOND_PART));
        assertTrue(expression.isExpressionMatch(PART + '.' + SECOND_PART));
    }

    @Test
    public void testEndsWithLocalWildcard() throws Exception {
        MailboxQuery expression = create(PART + '%');
        assertFalse(expression.isExpressionMatch(""));
        assertFalse(expression.isExpressionMatch(SECOND_PART));
        assertTrue(expression.isExpressionMatch(PART));
        assertTrue(expression.isExpressionMatch(PART + SECOND_PART));
        assertFalse(expression.isExpressionMatch(PART + '.' + SECOND_PART));
        assertFalse(expression.isExpressionMatch(PART + '.' + SECOND_PART));
    }

    @Test
    public void testStartsWithLocalWildcard() throws Exception {
        MailboxQuery expression = create('%' + PART);
        assertFalse(expression.isExpressionMatch(""));
        assertFalse(expression.isExpressionMatch(SECOND_PART));
        assertTrue(expression.isExpressionMatch(PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + PART));
        assertFalse(expression.isExpressionMatch(SECOND_PART + '.' + PART));
        assertFalse(expression.isExpressionMatch(SECOND_PART + '.' + PART + '.' + SECOND_PART));
        assertFalse(expression.isExpressionMatch(SECOND_PART));
    }

    @Test
    public void testContainsLocalWildcard() throws Exception {
        MailboxQuery expression = create(SECOND_PART + '%' + PART);
        assertFalse(expression.isExpressionMatch(""));
        assertFalse(expression.isExpressionMatch(SECOND_PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + PART));
        assertFalse(expression.isExpressionMatch(SECOND_PART + '.' + PART));
        assertFalse(expression.isExpressionMatch(PART));
        assertFalse(expression.isExpressionMatch(SECOND_PART + "w.hat.eve.r" + PART));
    }

    @Test
    public void testEndsWithFreeWildcard() throws Exception {
        MailboxQuery expression = create(PART + '*');
        assertFalse(expression.isExpressionMatch(""));
        assertFalse(expression.isExpressionMatch(SECOND_PART));
        assertTrue(expression.isExpressionMatch(PART));
        assertTrue(expression.isExpressionMatch(PART + SECOND_PART));
        assertTrue(expression.isExpressionMatch(PART + '.' + SECOND_PART));
        assertTrue(expression.isExpressionMatch(PART + '.' + SECOND_PART));
    }

    @Test
    public void testStartsWithFreeWildcard() throws Exception {
        MailboxQuery expression = create('*' + PART);
        assertFalse(expression.isExpressionMatch(""));
        assertFalse(expression.isExpressionMatch(SECOND_PART));
        assertTrue(expression.isExpressionMatch(PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + '.' + PART));
        assertFalse(expression.isExpressionMatch(SECOND_PART));
    }

    @Test
    public void testContainsFreeWildcard() throws Exception {
        MailboxQuery expression = create(SECOND_PART + '*' + PART);
        assertFalse(expression.isExpressionMatch(""));
        assertFalse(expression.isExpressionMatch(SECOND_PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + '.' + PART));
        assertFalse(expression.isExpressionMatch(PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + "w.hat.eve.r" + PART));
    }

    @Test
    public void testDoubleFreeWildcard() throws Exception {
        MailboxQuery expression = create(SECOND_PART + "**" + PART);
        assertFalse(expression.isExpressionMatch(""));
        assertFalse(expression.isExpressionMatch(SECOND_PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + '.' + PART));
        assertFalse(expression.isExpressionMatch(PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + "w.hat.eve.r" + PART));
    }

    @Test
    public void testFreeLocalWildcard() throws Exception {
        MailboxQuery expression = create(SECOND_PART + "*%" + PART);
        assertFalse(expression.isExpressionMatch(""));
        assertFalse(expression.isExpressionMatch(SECOND_PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + '.' + PART));
        assertFalse(expression.isExpressionMatch(PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + "w.hat.eve.r" + PART));
    }

    @Test
    public void testLocalFreeWildcard() throws Exception {
        MailboxQuery expression = create(SECOND_PART + "%*" + PART);
        assertFalse(expression.isExpressionMatch(""));
        assertFalse(expression.isExpressionMatch(SECOND_PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + '.' + PART));
        assertFalse(expression.isExpressionMatch(PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + "w.hat.eve.r" + PART));
    }

    @Test
    public void testMultipleFreeWildcards() throws Exception {
        MailboxQuery expression = create(SECOND_PART + '*' + PART + '*'
                + SECOND_PART + "**");
        assertTrue(expression.isExpressionMatch(SECOND_PART + PART
                + SECOND_PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + '.' + PART + '.'
                + SECOND_PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + "tosh.bosh"
                + PART + "tosh.bosh" + SECOND_PART + "boshtosh"));
        assertFalse(expression.isExpressionMatch(SECOND_PART + '.'
                + PART.substring(1) + '.' + SECOND_PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + '.'
                + PART.substring(1) + '.' + SECOND_PART + PART + '.'
                + SECOND_PART + "toshbosh"));
        assertFalse(expression.isExpressionMatch(SECOND_PART + '.'
                + PART.substring(1) + '.' + SECOND_PART + PART + '.'
                + SECOND_PART.substring(1)));
        assertTrue(expression.isExpressionMatch(SECOND_PART + "tosh.bosh"
                + PART + "tosh.bosh" + PART + SECOND_PART + "boshtosh" + PART
                + SECOND_PART));
        assertFalse(expression.isExpressionMatch(SECOND_PART.substring(1)
                + "tosh.bosh" + PART + "tosh.bosh" + SECOND_PART
                + PART.substring(1) + SECOND_PART + "boshtosh" + PART
                + SECOND_PART.substring(1)));
    }

    @Test
    public void testSimpleMixedWildcards() throws Exception {
        MailboxQuery expression = create(SECOND_PART + '%' + PART + '*'
                + SECOND_PART);
        assertTrue(expression.isExpressionMatch(SECOND_PART + PART
                + SECOND_PART));
        assertFalse(expression.isExpressionMatch(SECOND_PART + '.' + PART
                + SECOND_PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + PART + '.'
                + SECOND_PART));
        
        // Disable this tests as these are wrong. See MAILBOX-65
        //assertTrue(expression.isExpressionMatch(SECOND_PART + PART
        //        + SECOND_PART + "Whatever"));
        //assertTrue(expression.isExpressionMatch(SECOND_PART + PART
        //        + SECOND_PART + ".Whatever."));
    }

    @Test
    public void testFreeLocalMixedWildcards() throws Exception {
        MailboxQuery expression = create(SECOND_PART + '*' + PART + '%'
                + SECOND_PART);
        assertTrue(expression.isExpressionMatch(SECOND_PART + PART
                + SECOND_PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + '.' + PART
                + SECOND_PART));
        assertFalse(expression.isExpressionMatch(SECOND_PART + PART + '.'
                + SECOND_PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + PART + "Whatever"
                + SECOND_PART));
        assertFalse(expression.isExpressionMatch(SECOND_PART + PART
                + SECOND_PART + ".Whatever."));
        assertTrue(expression.isExpressionMatch(SECOND_PART + '.' + PART
                + SECOND_PART));
        assertFalse(expression.isExpressionMatch(SECOND_PART + '.' + PART
                + SECOND_PART + '.' + SECOND_PART));
        assertTrue(expression.isExpressionMatch(SECOND_PART + '.' + PART + '.'
                + SECOND_PART + PART + SECOND_PART));
    }
    
    @Test
    public void testTwoLocalWildcardsShouldMatchMailboxs() throws Exception {
        MailboxQuery expression = create("%.%");
        assertFalse(expression.isExpressionMatch(PART));
        assertFalse(expression.isExpressionMatch(PART + '.' + SECOND_PART + '.' + SECOND_PART));
        assertTrue(expression.isExpressionMatch(PART + '.' + SECOND_PART));
    }
    
    @Test
    public void testMailbox65() throws Exception {
        MailboxQuery expression = create("*.test");
        assertTrue(expression.isExpressionMatch("blah.test"));
        assertFalse(expression.isExpressionMatch("blah.test.go"));

        assertFalse(expression.isExpressionMatch("blah.test3"));

    }
}
