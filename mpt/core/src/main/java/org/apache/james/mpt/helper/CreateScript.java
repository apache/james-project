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

package org.apache.james.mpt.helper;

public class CreateScript {

    public static final String RE = "Re:";

    public static final String HEADER = "Delivered-To";

    public static final String ANOTHER_HEADER = "Received";

    public static final String COMMON_LETTER = "o";

    public static final String COMMON_WORD = "the";

    public static final String UNCOMMON_WORD = "thy";

    public static final String UNCOMMON_PHRASE = "\"nothing worthy prove\"";

    public static final String ANOTHER_NAME = "Robert";

    public static final String NAME = "tim";

    public static final String DOMAIN = "example.org";

    public static final String ANOTHER_DOMAIN = "apache.org";

    public static void main(String[] args) throws Exception {
        ScriptBuilder builder = ScriptBuilder.open("localhost", 143);
        expunge(builder);
    }


    public static void expunge(ScriptBuilder builder) throws Exception {
        try {
            setup(builder);
            builder.append();
            builder.setFile("wild-example.mail");
            builder.append();
            builder.setFile("multipart-alt.mail");
            builder.append();
            builder.setFile("multipart-mixed-complex.mail");
            builder.append();
            builder.setFile("rfc822-hello-world.mail");
            builder.append();
            builder.setFile("wild-mixed-alt.mail");
            builder.append();
            builder.setFile("wild-mixed.mail");
            builder.append();
            builder.setFile("rfc822-resent.mail");
            builder.append();
            builder.setFile("rfc822-trace.mail");
            builder.append();
            builder.setFile("wild-alt-reply4.mail");
            builder.append();
            builder.select();
            builder.flagDeleted(4);
            builder.flagDeleted(6);
            builder.flagDeleted(7);
            builder.expunge();
            builder.select();

        } finally {
            builder.quit();
        }
    }
    
    public static void rfcFetch(ScriptBuilder builder) throws Exception {
        try {
            setup(builder);
            builder.append();
            builder.setFile("wild-example.mail");
            builder.append();
            builder.setFile("multipart-alt.mail");
            builder.append();
            builder.setFile("multipart-mixed-complex.mail");
            builder.append();
            builder.setFile("rfc822-hello-world.mail");
            builder.append();
            builder.setFile("wild-mixed-alt.mail");
            builder.append();
            builder.setFile("wild-mixed.mail");
            builder.append();
            builder.setFile("rfc822-resent.mail");
            builder.append();
            builder.setFile("rfc822-trace.mail");
            builder.append();
            builder.setFile("wild-alt-reply4.mail");
            builder.append();
            builder.resetFetch().setRfc822Size(true);
            builder.fetchAllMessages();
            builder.resetFetch().setRfc(true);
            builder.fetchAllMessages();
            builder.resetFetch().setRfcHeaders(true);
            builder.fetchAllMessages();
            builder.resetFetch().setRfcText(true);
            builder.fetchAllMessages();
        } finally {
            builder.quit();
        }
    }

    public static void bodyStructureEmbedded(ScriptBuilder builder)
            throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.setFile("multipart-mixed-complex.mail");
        builder.append();
        builder.fetchSection("");
        builder.fetchSection("TEXT");
        builder.fetchSection("HEADER");
        builder.fetchSection("1");
        builder.fetchSection("2");
        builder.fetchSection("3");
        builder.fetchSection("3.HEADER");
        builder.fetchSection("3.TEXT");
        builder.fetchSection("3.1");
        builder.fetchSection("3.2");
        builder.fetchSection("4");
        builder.fetchSection("4.1");
        builder.fetchSection("4.1.MIME");
        builder.fetchSection("4.2");
        builder.fetchSection("4.2.HEADER");
        builder.fetchSection("4.2.TEXT");
        builder.fetchSection("4.2.1");
        builder.fetchSection("4.2.2");
        builder.fetchSection("4.2.2.1");
        builder.fetchSection("4.2.2.2");
        builder.resetFetch().setBodyFetch(true).setBodyStructureFetch(true);
        builder.fetchAllMessages();
        builder.quit();
    }

    public static void bodyStructureComplex(ScriptBuilder builder)
            throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.setFile("wild-alt-reply3.mail");
        builder.append();
        builder.setFile("wild-alt-reply4.mail");
        builder.append();
        builder.setFile("multipart-mixed.mail");
        builder.append();
        builder.setFile("wild-mixed-alt.mail");
        builder.append();
        builder.setFile("wild-mixed.mail");
        builder.append();
        builder.setFile("mime-plain-text.mail");
        builder.append();
        builder.fetchAllMessages();
        for (int i = 1; i < 7; i++) {
            builder.setMessageNumber(i);
            builder.fetchSection("");
            builder.fetchSection("TEXT");
            builder.fetchSection("HEADER");
            builder.fetchSection("1");
            builder.fetchSection("2");
            builder.fetchSection("3");
            builder.fetchSection("3.HEADER");
            builder.fetchSection("3.TEXT");
            builder.fetchSection("3.1");
            builder.fetchSection("3.2");
            builder.fetchSection("4");
            builder.fetchSection("4.1");
            builder.fetchSection("4.1.MIME");
            builder.fetchSection("4.2");
            builder.fetchSection("4.2.HEADER");
            builder.fetchSection("4.2.TEXT");
            builder.fetchSection("4.2.1");
            builder.fetchSection("4.2.2");
            builder.fetchSection("4.2.2.1");
            builder.fetchSection("4.2.2.2");
        }
        builder.resetFetch().setBodyFetch(true).setBodyStructureFetch(true);
        builder.fetchAllMessages();
        builder.quit();
    }

    public static void bodyStructureSimple(ScriptBuilder builder)
            throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.setFile("rfc822-multiple-addresses.mail");
        builder.append();
        builder.setFile("wild-example.mail");
        builder.append();
        builder.setFile("mime-plain-text.mail");
        builder.append();
        builder.fetchAllMessages();
        builder.fetchSection("");
        builder.fetchSection("TEXT");
        builder.fetchSection("HEADER");
        builder.fetchSection("1");
        builder.fetchSection("2");
        builder.fetchSection("3");
        builder.fetchSection("3.HEADER");
        builder.fetchSection("3.TEXT");
        builder.fetchSection("3.1");
        builder.fetchSection("3.2");
        builder.fetchSection("4");
        builder.fetchSection("4.1");
        builder.fetchSection("4.1.MIME");
        builder.fetchSection("4.2");
        builder.fetchSection("4.2.HEADER");
        builder.fetchSection("4.2.TEXT");
        builder.fetchSection("4.2.1");
        builder.fetchSection("4.2.2");
        builder.fetchSection("4.2.2.1");
        builder.fetchSection("4.2.2.2");
        builder.resetFetch().setBodyFetch(true).setBodyStructureFetch(true);
        builder.fetchAllMessages();
        builder.quit();
    }

    public static void bodyStructureMultipart(ScriptBuilder builder)
            throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.setFile("multipart-binary.mail");
        builder.append();
        builder.setFile("multipart-alt-translation.mail");
        builder.append();
        builder.fetchAllMessages();
        builder.fetchSection("");
        builder.fetchSection("TEXT");
        builder.fetchSection("HEADER");
        builder.fetchSection("1");
        builder.fetchSection("2");
        builder.fetchSection("3");
        builder.fetchSection("3.HEADER");
        builder.fetchSection("3.TEXT");
        builder.fetchSection("3.1");
        builder.fetchSection("3.2");
        builder.fetchSection("4");
        builder.fetchSection("4.1");
        builder.fetchSection("4.1.MIME");
        builder.fetchSection("4.2");
        builder.fetchSection("4.2.HEADER");
        builder.fetchSection("4.2.TEXT");
        builder.fetchSection("4.2.1");
        builder.fetchSection("4.2.2");
        builder.fetchSection("4.2.2.1");
        builder.fetchSection("4.2.2.2");
        builder.resetFetch().setBodyFetch(true).setBodyStructureFetch(true);
        builder.fetchAllMessages();
        builder.quit();
    }

    public static void renameSelected(ScriptBuilder builder) throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.append();
        builder.setFile("rfc822-hello-world.mail");
        builder.append();
        builder.setFile("rfc822-sender.mail");
        builder.append();
        builder.setFile("rfc822.mail");
        builder.append();
        builder.setFile("rfc822-multiple-addresses.mail");
        builder.append();
        builder.select();
        builder.getFetch().setFlagsFetch(true).bodyPeekHeaders(
                ScriptBuilder.Fetch.SELECT_HEADERS).setUid(true);
        builder.fetchAllMessages();
        builder.list();
        builder.rename("anothermailbox");
        builder.list();
        builder.fetchAllMessages();
        builder.store(builder.flags().add().flagged().range(1, 2));
        builder.store(builder.flags().add().answered().range(1, 3));
        builder.fetchAllMessages();
        builder.select();
        builder.setMailbox("anothermailbox");
        builder.select();
        builder.fetchAllMessages();
        builder.quit();
    }

    public static void renameHierarchy(ScriptBuilder builder) throws Exception {
        builder.login();
        builder.setMailbox("one").create();
        builder.setMailbox("one.two").create();
        builder.setMailbox("one.two.three").create();
        builder.setMailbox("one.two.three.four").create();
        builder.list();
        builder.rename("one.two", "alpha.beta");
        builder.list();
        builder.rename("alpha.beta.three.four", "alpha.beta.gamma.delta");
        builder.list();
        builder.rename("alpha.beta.three", "aleph");
        builder.list();
        builder.rename("aleph", "alpha.beta.gamma.delta.epsilon");
        builder.list();
        builder.rename("alpha.beta.gamma", "one");
        builder.list();
        builder.setMailbox("one").delete();
        builder.setMailbox("alpha").delete();
        builder.setMailbox("aleph");
        builder.list();
        builder.quit();
    }

    public static void rename(ScriptBuilder builder) throws Exception {
        setupSearch(builder, true);
        builder.select();
        String originalMailbox = builder.getMailbox();
        builder.getFetch().setFlagsFetch(true).bodyPeekHeaders(
                ScriptBuilder.Fetch.SELECT_HEADERS).setUid(true);
        builder.fetchAllMessages();
        builder.setMailbox("other").create().select().append();
        builder.setMailbox("base").create().select();
        builder.rename(originalMailbox, "moved").setMailbox("moved").select()
                .fetchAllMessages();
        builder.setMailbox(originalMailbox).select();
        builder.rename("other", "base");
        builder.setMailbox(originalMailbox).select();
        builder.setMailbox("moved").select();
        builder.setMailbox("other").select();
        builder.setMailbox("base").select();
        builder.setMailbox("BOGUS").select();
        builder.setMailbox("WHATEVER").select();
        builder.rename("other", originalMailbox);
        builder.setMailbox(originalMailbox).select();
        builder.setMailbox("moved").select();
        builder.setMailbox("other").select();
        builder.setMailbox("base").select();
        builder.setMailbox("BOGUS").select();
        builder.setMailbox("WHATEVER").select();
        builder.rename("BOGUS", "WHATEVER");
        builder.rename(originalMailbox, "INBOX");
        builder.rename(originalMailbox, "inbox");
        builder.rename(originalMailbox, "Inbox");
        builder.setMailbox(originalMailbox).select();
        builder.setMailbox("moved").select();
        builder.setMailbox("other").select();
        builder.setMailbox("base").select();
        builder.setMailbox("BOGUS").select();
        builder.setMailbox("WHATEVER").select();
        builder.setMailbox("BOGUS").delete();
        builder.setMailbox("WHATEVER").delete();
        builder.setMailbox(originalMailbox).delete();
        builder.setMailbox("base").delete();
        builder.setMailbox("other").delete();
        builder.setMailbox("moved").delete();
        builder.quit();
    }

    public static void mimePartialFetch(ScriptBuilder builder) throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.setFile("multipart-mixed-complex.mail");
        builder.append();
        builder.select();
        builder.partial(0, 0).fetchSection("4.1.MIME");
        builder.partial(0, 15).fetchSection("4.1.MIME");
        builder.partial(0, 31).fetchSection("4.1.MIME");
        builder.partial(0, 63).fetchSection("4.1.MIME");
        builder.partial(0, 127).fetchSection("4.1.MIME");
        builder.partial(0, 1023).fetchSection("4.1.MIME");
        builder.partial(0, 2047).fetchSection("4.1.MIME");
        builder.partial(17, 0).fetchSection("4.1.MIME");
        builder.partial(17, 16).fetchSection("4.1.MIME");
        builder.partial(17, 32).fetchSection("4.1.MIME");
        builder.partial(17, 64).fetchSection("4.1.MIME");
        builder.partial(17, 128).fetchSection("4.1.MIME");
        builder.partial(17, 1024).fetchSection("4.1.MIME");
        builder.partial(17, 2048).fetchSection("4.1.MIME");
        builder.partial(10000, 0).fetchSection("4.1.MIME");
        builder.partial(10000, 16).fetchSection("4.1.MIME");
        builder.partial(10000, 32).fetchSection("4.1.MIME");
        builder.partial(10000, 64).fetchSection("4.1.MIME");
        builder.partial(10000, 128).fetchSection("4.1.MIME");
        builder.partial(10000, 1024).fetchSection("4.1.MIME");
        builder.partial(10000, 2048).fetchSection("4.1.MIME");
        builder.quit();
    }

    public static void headerPartialFetch(ScriptBuilder builder)
            throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.setFile("multipart-mixed-complex.mail");
        builder.append();
        builder.select();
        builder.partial(0, 0).fetchSection("HEADER");
        builder.partial(0, 16).fetchSection("HEADER");
        builder.partial(0, 32).fetchSection("HEADER");
        builder.partial(0, 64).fetchSection("HEADER");
        builder.partial(0, 128).fetchSection("HEADER");
        builder.partial(0, 1024).fetchSection("HEADER");
        builder.partial(0, 2048).fetchSection("HEADER");
        builder.partial(7, 0).fetchSection("HEADER");
        builder.partial(7, 16).fetchSection("HEADER");
        builder.partial(7, 32).fetchSection("HEADER");
        builder.partial(7, 64).fetchSection("HEADER");
        builder.partial(7, 128).fetchSection("HEADER");
        builder.partial(7, 1024).fetchSection("HEADER");
        builder.partial(7, 2048).fetchSection("HEADER");
        builder.partial(10000, 0).fetchSection("HEADER");
        builder.partial(10000, 16).fetchSection("HEADER");
        builder.partial(10000, 32).fetchSection("HEADER");
        builder.partial(10000, 64).fetchSection("HEADER");
        builder.partial(10000, 128).fetchSection("HEADER");
        builder.partial(10000, 1024).fetchSection("HEADER");
        builder.partial(10000, 2048).fetchSection("HEADER");
        builder.quit();
    }

    public static void textPartialFetch(ScriptBuilder builder) throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.setFile("multipart-mixed-complex.mail");
        builder.append();
        builder.select();
        builder.partial(0, 0).fetchSection("TEXT");
        builder.partial(0, 16).fetchSection("TEXT");
        builder.partial(0, 32).fetchSection("TEXT");
        builder.partial(0, 64).fetchSection("TEXT");
        builder.partial(0, 128).fetchSection("TEXT");
        builder.partial(0, 1024).fetchSection("TEXT");
        builder.partial(0, 2048).fetchSection("TEXT");
        builder.partial(7, 0).fetchSection("TEXT");
        builder.partial(7, 16).fetchSection("TEXT");
        builder.partial(7, 32).fetchSection("TEXT");
        builder.partial(7, 64).fetchSection("TEXT");
        builder.partial(7, 128).fetchSection("TEXT");
        builder.partial(7, 1024).fetchSection("TEXT");
        builder.partial(7, 2048).fetchSection("TEXT");
        builder.partial(10000, 0).fetchSection("TEXT");
        builder.partial(10000, 16).fetchSection("TEXT");
        builder.partial(10000, 32).fetchSection("TEXT");
        builder.partial(10000, 64).fetchSection("TEXT");
        builder.partial(10000, 128).fetchSection("TEXT");
        builder.partial(10000, 1024).fetchSection("TEXT");
        builder.partial(10000, 2048).fetchSection("TEXT");
        builder.quit();
    }

    public static void bodyPartialFetch(ScriptBuilder builder) throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.setFile("multipart-mixed.mail");
        builder.append();
        builder.select();
        builder.partial(0, 10).fetchSection("");
        builder.partial(0, 100).fetchSection("");
        builder.partial(0, 1000).fetchSection("");
        builder.partial(0, 10000).fetchSection("");
        builder.partial(0, 100000).fetchSection("");
        builder.partial(100, 10).fetchSection("");
        builder.partial(100, 100).fetchSection("");
        builder.partial(100, 1000).fetchSection("");
        builder.partial(100, 10000).fetchSection("");
        builder.partial(100, 100000).fetchSection("");
        builder.partial(10000, 10).fetchSection("");
        builder.partial(10000, 100).fetchSection("");
        builder.partial(10000, 1000).fetchSection("");
        builder.partial(10000, 10000).fetchSection("");
        builder.partial(10000, 100000).fetchSection("");
        builder.quit();
    }

    public static void searchCombinations(ScriptBuilder builder, boolean uids)
            throws Exception {
        setupSearch(builder, uids);
        builder.body(COMMON_LETTER).undraft().unflagged().answered().search();
        builder.to(COMMON_LETTER).draft().flagged().answered().search();
        builder.to(COMMON_LETTER).smaller(10000).all().draft().search();
        builder.bcc(COMMON_LETTER).larger(1000).search();
        builder.from(COMMON_LETTER).larger(1000).flagged().search();
        builder.from(COMMON_LETTER).to(COMMON_LETTER).answered().flagged()
                .all().body(COMMON_LETTER).sentbefore(2009, 1, 1).search();
        builder.or().openParen().from(COMMON_LETTER).to(COMMON_LETTER)
                .answered().flagged().all().body(COMMON_LETTER).sentbefore(
                        2009, 1, 1).closeParen().openParen().header(HEADER,
                        "\"\"").draft().closeParen().search();
        builder.or().openParen().cc(COMMON_LETTER).text(COMMON_LETTER).unseen()
                .larger(1000).all().body(COMMON_LETTER).senton(2008, 4, 8)
                .closeParen().openParen().header(HEADER, "\"\"").draft()
                .closeParen().search();
        builder.or().openParen().cc(COMMON_LETTER).to(COMMON_LETTER).draft()
                .unseen().all().text(COMMON_LETTER).sentsince(2000, 1, 1)
                .closeParen().openParen().header(HEADER, "\"\"").draft()
                .closeParen().search();
        builder.or().openParen().or().openParen().or().openParen().not().text(
                COMMON_LETTER).cc(COMMON_LETTER).unseen().flagged().all().body(
                COMMON_LETTER).not().senton(2008, 3, 1).closeParen()
                .openParen().header(HEADER, DOMAIN).flagged().closeParen()
                .closeParen().openParen().from(COMMON_LETTER).to(COMMON_LETTER)
                .answered().flagged().all().body(COMMON_LETTER).sentbefore(
                        2009, 1, 1).closeParen().closeParen().openParen()
                .answered().flagged().draft().closeParen().all().deleted()
                .search();
        builder.or().openParen().or().openParen().or().openParen().from(
                COMMON_LETTER).to(COMMON_LETTER).answered().flagged().all()
                .body(COMMON_LETTER).sentbefore(2009, 1, 1).closeParen()
                .openParen().header(HEADER, "\"\"").draft().closeParen()
                .closeParen().openParen().from(COMMON_LETTER).to(COMMON_LETTER)
                .answered().flagged().all().body(COMMON_LETTER).sentbefore(
                        2009, 1, 1).closeParen().closeParen().openParen()
                .answered().flagged().draft().closeParen().all().unanswered()
                .search();
        builder.quit();
    }

    public static void searchAtoms(ScriptBuilder builder, boolean uids)
            throws Exception {
        setupSearch(builder, uids);
        builder.all().search();
        builder.answered().search();
        builder.bcc(COMMON_LETTER).search();
        builder.bcc(NAME).search();
        builder.bcc(ANOTHER_NAME).search();
        builder.bcc(DOMAIN).search();
        builder.bcc(ANOTHER_DOMAIN).search();
        builder.body(COMMON_LETTER).search();
        builder.body(COMMON_WORD).search();
        builder.body(UNCOMMON_WORD).search();
        builder.body(UNCOMMON_PHRASE).search();
        builder.cc(COMMON_LETTER).search();
        builder.cc(NAME).search();
        builder.cc(ANOTHER_NAME).search();
        builder.cc(DOMAIN).search();
        builder.cc(ANOTHER_DOMAIN).search();
        builder.deleted().search();
        builder.draft().search();
        builder.flagged().search();
        builder.from(COMMON_LETTER).search();
        builder.from(NAME).search();
        builder.from(ANOTHER_NAME).search();
        builder.from(DOMAIN).search();
        builder.from(ANOTHER_DOMAIN).search();
        builder.header(HEADER, DOMAIN).search();
        builder.header(HEADER, COMMON_LETTER).search();
        builder.header(HEADER, ANOTHER_DOMAIN).search();
        builder.header(HEADER, "\"\"").search();
        builder.header(ANOTHER_HEADER, DOMAIN).search();
        builder.header(ANOTHER_HEADER, COMMON_LETTER).search();
        builder.header(ANOTHER_HEADER, ANOTHER_DOMAIN).search();
        builder.header(ANOTHER_HEADER, "\"\"").search();
        builder.larger(10).search();
        builder.larger(100).search();
        builder.larger(1000).search();
        builder.larger(10000).search();
        builder.larger(12500).search();
        builder.larger(15000).search();
        builder.larger(20000).search();
        builder.newOperator().search();
        builder.not().flagged().search();
        builder.msn(3, 5).search();
        builder.msnAndDown(10).search();
        builder.msnAndUp(17).search();
        builder.old().search();
        builder.or().answered().flagged().search();
        builder.recent().search();
        builder.seen().search();
        builder.sentbefore(2007, 10, 10).search();
        builder.sentbefore(2008, 1, 1).search();
        builder.sentbefore(2008, 2, 1).search();
        builder.sentbefore(2008, 2, 10).search();
        builder.sentbefore(2008, 2, 20).search();
        builder.sentbefore(2008, 2, 25).search();
        builder.sentbefore(2008, 3, 1).search();
        builder.sentbefore(2008, 3, 5).search();
        builder.sentbefore(2008, 3, 10).search();
        builder.sentbefore(2008, 4, 1).search();
        builder.senton(2007, 10, 10).search();
        builder.senton(2008, 1, 1).search();
        builder.senton(2008, 2, 1).search();
        builder.senton(2008, 2, 10).search();
        builder.senton(2008, 2, 20).search();
        builder.senton(2008, 2, 25).search();
        builder.senton(2008, 3, 1).search();
        builder.senton(2008, 3, 5).search();
        builder.senton(2008, 3, 10).search();
        builder.senton(2008, 4, 1).search();
        builder.sentsince(2007, 10, 10).search();
        builder.sentsince(2008, 1, 1).search();
        builder.sentsince(2008, 2, 1).search();
        builder.sentsince(2008, 2, 10).search();
        builder.sentsince(2008, 2, 20).search();
        builder.sentsince(2008, 2, 25).search();
        builder.sentsince(2008, 3, 1).search();
        builder.sentsince(2008, 3, 5).search();
        builder.sentsince(2008, 3, 10).search();
        builder.sentsince(2008, 4, 1).search();
        builder.smaller(10).search();
        builder.smaller(100).search();
        builder.smaller(1000).search();
        builder.smaller(10000).search();
        builder.smaller(12500).search();
        builder.smaller(15000).search();
        builder.smaller(20000).search();
        builder.subject(COMMON_LETTER).search();
        builder.subject(COMMON_WORD).search();
        builder.subject(UNCOMMON_PHRASE).search();
        builder.subject(UNCOMMON_WORD).search();
        builder.subject(RE).search();
        builder.text(COMMON_LETTER).search();
        builder.text(COMMON_WORD).search();
        builder.text(UNCOMMON_PHRASE).search();
        builder.text(UNCOMMON_WORD).search();
        builder.text(RE).search();
        builder.text(DOMAIN).search();
        builder.text(ANOTHER_DOMAIN).search();
        builder.text(ANOTHER_NAME).search();
        builder.text(NAME).search();
        builder.to(COMMON_LETTER).search();
        builder.to(NAME).search();
        builder.to(ANOTHER_NAME).search();
        builder.to(DOMAIN).search();
        builder.to(ANOTHER_DOMAIN).search();
        builder.uid().msn(1, 4).search();
        builder.unanswered().search();
        builder.undeleted().search();
        builder.undraft().search();
        builder.unflagged().search();
        builder.unseen().search();
        builder.quit();
    }

    private static void setupSearch(ScriptBuilder builder, boolean uids)
            throws Exception {
        builder.setUidSearch(uids);
        setup(builder);
        padUids(builder);
        loadLotsOfMail(builder);
        builder.store(builder.flags().add().flagged().range(1, 9));
        builder.store(builder.flags().add().answered().range(1, 4));
        builder.store(builder.flags().add().answered().range(10, 14));
        builder.store(builder.flags().add().seen().range(1, 2));
        builder.store(builder.flags().add().seen().range(5, 7));
        builder.store(builder.flags().add().seen().range(10, 12));
        builder.store(builder.flags().add().seen().range(15, 17));
        builder.store(builder.flags().add().draft().msn(1));
        builder.store(builder.flags().add().draft().msn(3));
        builder.store(builder.flags().add().draft().msn(5));
        builder.store(builder.flags().add().draft().msn(7));
        builder.store(builder.flags().add().draft().msn(9));
        builder.store(builder.flags().add().draft().msn(11));
        builder.store(builder.flags().add().draft().msn(13));
        builder.store(builder.flags().add().draft().msn(15));
        builder.store(builder.flags().add().draft().msn(17));
        builder.store(builder.flags().add().deleted().range(1, 3));
    }

    private static void setup(ScriptBuilder builder) throws Exception {
        builder.login();
        builder.create();
        builder.select();
    }

    private static void padUids(ScriptBuilder builder) throws Exception {
        builder.setFile("rfc822.mail");
        for (int i = 0; i < 20; i++) {
            builder.append();
            builder.flagDeleted().expunge();
        }
    }

    private static void loadLotsOfMail(ScriptBuilder builder) throws Exception {
        builder.append();
        builder.setFile("wild-example.mail");
        builder.append();
        builder.setFile("multipart-alt.mail");
        builder.append();
        builder.setFile("multipart-mixed.mail");
        builder.append();
        builder.setFile("multipart-mixed-complex.mail");
        builder.append();
        builder.setFile("rfc822-hello-world.mail");
        builder.append();
        builder.setFile("rfc822-sender.mail");
        builder.append();
        builder.setFile("rfc822.mail");
        builder.append();
        builder.setFile("rfc822-multiple-addresses.mail");
        builder.append();
        builder.setFile("wild-alt-reply.mail");
        builder.append();
        builder.setFile("wild-mixed-alt.mail");
        builder.append();
        builder.setFile("wild-mixed.mail");
        builder.append();
        builder.setFile("rfc822-reply.mail");
        builder.append();
        builder.setFile("rfc822-resent.mail");
        builder.append();
        builder.setFile("rfc822-trace.mail");
        builder.append();
        builder.setFile("rfc822-group-addresses.mail");
        builder.append();
        builder.setFile("wild-alt-another-reply.mail");
        builder.append();
        builder.setFile("wild-alt-reply3.mail");
        builder.append();
        builder.setFile("wild-alt-reply4.mail");
        builder.append();
    }

    public static void notHeaderFetches(ScriptBuilder builder) throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.append();
        builder.setFile("wild-example.mail");
        builder.append();
        builder.setFile("multipart-alt.mail");
        builder.append();
        builder.setFile("multipart-mixed.mail");
        builder.append();
        builder.setFile("multipart-mixed-complex.mail");
        builder.append();
        builder.setFile("rfc822-hello-world.mail");
        builder.append();
        builder.setFile("rfc822-sender.mail");
        builder.append();
        builder.setFile("rfc822.mail");
        builder.append();
        builder.setFile("rfc822-multiple-addresses.mail");
        builder.append();
        builder.select();
        builder.getFetch().bodyPeekCompleteMessage();
        builder.fetchAllMessages();
        builder.resetFetch();
        builder.getFetch().bodyPeekNotHeaders(
                ScriptBuilder.Fetch.SELECT_HEADERS);
        builder.fetchAllMessages();
        builder.select();
        builder.quit();
    }

    public static void simpleCombinedFetches(ScriptBuilder builder)
            throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.append();
        builder.setFile("wild-example.mail");
        builder.append();
        builder.setFile("multipart-alt.mail");
        builder.append();
        builder.setFile("multipart-mixed.mail");
        builder.append();
        builder.setFile("multipart-mixed-complex.mail");
        builder.append();
        builder.setFile("rfc822-hello-world.mail");
        builder.append();
        builder.setFile("rfc822-sender.mail");
        builder.append();
        builder.setFile("rfc822.mail");
        builder.append();
        builder.setFile("rfc822-multiple-addresses.mail");
        builder.append();
        builder.select();
        builder.getFetch().bodyPeekCompleteMessage();
        builder.fetchAllMessages();
        builder.resetFetch();
        builder.getFetch().bodyPeekHeaders(
                ScriptBuilder.Fetch.COMPREHENSIVE_HEADERS);
        builder.fetchAllMessages();
        builder.select();
        builder.quit();
    }

    public static void recent(ScriptBuilder builder) throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.append();
        builder.select();
        builder.fetchFlags();
        builder.fetchSection("");
        builder.fetchFlags();
        builder.quit();
    }

    public static void multipartMixedMessagesPeek(ScriptBuilder builder)
            throws Exception {
        builder.setPeek(true);
        multipartMixedMessages(builder);
    }

    public static void multipartMixedMessages(ScriptBuilder builder)
            throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.setFile("multipart-mixed-complex.mail");
        builder.append();
        builder.select();
        builder.fetchSection("");
        builder.fetchSection("TEXT");
        builder.fetchSection("HEADER");
        builder.fetchSection("1");
        builder.fetchSection("2");
        builder.fetchSection("3");
        builder.fetchSection("3.HEADER");
        builder.fetchSection("3.TEXT");
        builder.fetchSection("3.1");
        builder.fetchSection("3.2");
        builder.fetchSection("4");
        builder.fetchSection("4.1");
        builder.fetchSection("4.1.MIME");
        builder.fetchSection("4.2");
        builder.fetchSection("4.2.HEADER");
        builder.fetchSection("4.2.TEXT");
        builder.fetchSection("4.2.1");
        builder.fetchSection("4.2.2");
        builder.fetchSection("4.2.2.1");
        builder.fetchSection("4.2.2.2");
        builder.select();
        builder.quit();
    }

    public static void multipartAlternativePeek(ScriptBuilder builder)
            throws Exception {
        builder.setPeek(true);
        multipartAlternative(builder);
    }

    public static void multipartAlternative(ScriptBuilder builder)
            throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.setFile("multipart-alt.mail");
        builder.append();
        builder.select();
        builder.fetchSection("");
        builder.fetchSection("TEXT");
        builder.fetchSection("HEADER");
        builder.fetchSection("1");
        builder.fetchSection("2");
        builder.fetchSection("3");
        builder.select();
        builder.quit();
    }

    public static void multipartMixedPeek(ScriptBuilder builder)
            throws Exception {
        builder.setPeek(true);
        multipartMixed(builder);
    }

    public static void multipartMixed(ScriptBuilder builder) throws Exception {
        builder.login();
        builder.create();
        builder.select();
        builder.setFile("multipart-mixed.mail");
        builder.append();
        builder.select();
        builder.fetchSection("");
        builder.fetchSection("TEXT");
        builder.fetchSection("HEADER");
        builder.fetchSection("1");
        builder.fetchSection("2");
        builder.fetchSection("3");
        builder.fetchSection("4");
        builder.select();
        builder.quit();
    }

}
