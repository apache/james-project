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

package org.apache.james.transport.mailets;

import org.apache.james.transport.mailets.UnwrapText;
import org.junit.Assert;
import org.junit.Test;

public class UnwrapTextTest {

    /*
     * Test method for 'it.voidlabs.elysium.mailgateway.transport.mailets.UnwrapText.unwrap(String)'
     */
    @Test
    public void testUnwrap() {
        String input, output;

        Assert.assertEquals("", UnwrapText.unwrap(""));
        Assert.assertEquals("a", UnwrapText.unwrap("a"));

        input =
                "Prova per vedere se effettivamente il testo viene wrappato\r\n" +
                        "come dovrebbe.\r\n" +
                        "Chiss\u00E0 se funziona davvero\r\n" +
                        "o se va solo come gli pare";

        output =
                "Prova per vedere se effettivamente il testo viene wrappato come dovrebbe.\r\n" +
                        "Chiss\u00E0 se funziona davvero\r\n" +
                        "o se va solo come gli pare";

        Assert.assertEquals(output, UnwrapText.unwrap(input));

        input =
                "> Prova per vedere se effettivamente il testo viene wrappato\r\n" +
                        "> come dovrebbe.\r\n" +
                        "> Chiss\u00E0 se funziona davvero\r\n" +
                        "> o se va solo come gli pare";

        output =
                "> Prova per vedere se effettivamente il testo viene wrappato come dovrebbe.\r\n" +
                        "> Chiss\u00E0 se funziona davvero\r\n" +
                        "> o se va solo come gli pare";

        Assert.assertEquals(output, UnwrapText.unwrap(input));

        input =
                "> Prova per vedere se effettivamente il testo viene wrappato\r\n" +
                        "> come dovrebbe.\r\n" +
                        "> Chiss\u00E0 se funziona davvero\r\n" +
                        "> o se va solo come gli pare\r\n" +
                        "> Prova per vedere se effettivamente il testo viene wrappato\r\n" +
                        "> come dovrebbe.\r\n";

        output =
                "> Prova per vedere se effettivamente il testo viene wrappato come dovrebbe.\r\n" +
                        "> Chiss\u00E0 se funziona davvero\r\n" +
                        "> o se va solo come gli pare\r\n" +
                        "> Prova per vedere se effettivamente il testo viene wrappato come dovrebbe.\r\n";

        Assert.assertEquals(output, UnwrapText.unwrap(input));

        input =
                "> volevo chiedervi un piccolo aiutino. Una signora con un circolare ha un\r\n" +
                        "> dente che si \u00E8 scementato. Il cemento usato per la cementazione \u00E8\r\n" +
                        "> l'harvard.  Il problema \u00E8 che non riesco a decementarlo. Avete qualche\r\n" +
                        "> trucco da  suggerirmi per rimuovere il ponte? Il ponte \u00E8 in ceramica, per\r\n" +
                        "> cui  l'utilizzo degli ultrasuoni puo' essere rischioso (?).\r\n";

        output =
                "> volevo chiedervi un piccolo aiutino. Una signora con un circolare ha un " +
                        "dente che si \u00E8 scementato. Il cemento usato per la cementazione \u00E8 " +
                        "l'harvard.  Il problema \u00E8 che non riesco a decementarlo. Avete qualche " +
                        "trucco da  suggerirmi per rimuovere il ponte? Il ponte \u00E8 in ceramica, per " +
                        "cui  l'utilizzo degli ultrasuoni puo' essere rischioso (?).\r\n";

        Assert.assertEquals(output, UnwrapText.unwrap(input));

        input =
                "> Prova per vedere se effettivamente il testo viene wrappato\r\n" +
                        "> com dovrebbe.\r\n" +
                        "> Prova per vedere se effettivamente il testo viene wrappato\r\n" +
                        "come\r\n" +
                        "> dovrebbe.\r\n";

        output =
                "> Prova per vedere se effettivamente il testo viene wrappato com dovrebbe.\r\n" +
                        "> Prova per vedere se effettivamente il testo viene wrappato come dovrebbe.\r\n";

        Assert.assertEquals(output, UnwrapText.unwrap(input));

        input =
                "> Prova per vedere se effettivamente il testo viene wrappato\r\n" +
                        "> com dovrebbe.\r\n" +
                        "> Prova per vedere se effettivamente il testo viene wrappato\r\n" +
                        "come\r\n" +
                        ">> dovrebbe.\r\n";

        output =
                "> Prova per vedere se effettivamente il testo viene wrappato com dovrebbe.\r\n" +
                        "> Prova per vedere se effettivamente il testo viene wrappato come\r\n" +
                        ">> dovrebbe.\r\n";

        Assert.assertEquals(output, UnwrapText.unwrap(input));

        input =
                "> Prova per vedere se effettivamente il testo viene wrappato\r\n" +
                        "> com dovrebbe.\r\n" +
                        "> Prova per vedere se effettivamente il testo viene wrappato\r\n" +
                        "come dovrebbe.\r\n";

        output =
                "> Prova per vedere se effettivamente il testo viene wrappato com dovrebbe.\r\n" +
                        "> Prova per vedere se effettivamente il testo viene wrappato\r\n" +
                        "come dovrebbe.\r\n";

        Assert.assertEquals(output, UnwrapText.unwrap(input));

        input =
                "...pero' devo dire che ai congressi vedo moltissimi casi di carico\r\n" +
                        "immediato, \r\n" +
                        "spesso circolari superiore e inferiore, con estrazione di denti\r\n" +
                        "\"parodontosici\" che io terrei li per altri 15 anni...\r\n" +
                        "Non voglio polemizzare ne tantomento accusare nessuno, ma credo che spesso a\r\n" +
                        "accada quello che Alessio suggerisce...\r\n";

        output =
                "...pero' devo dire che ai congressi vedo moltissimi casi di carico immediato, \r\n" +
                        "spesso circolari superiore e inferiore, con estrazione di denti\r\n" +
                        "\"parodontosici\" che io terrei li per altri 15 anni...\r\n" +
                        "Non voglio polemizzare ne tantomento accusare nessuno, ma credo che spesso a accada quello che Alessio suggerisce...\r\n";

        Assert.assertEquals(output, UnwrapText.unwrap(input));

        input =
                "> mi trovo in difficolta,ho eseguito un lavoro di protesizzazione in\r\n" +
                        "porcellana\r\n" +
                        "> su 24 25 26 premetto che i denti sottostanti erano pieni di otturazioni in\r\n" +
                        "> amalgama ,la giovane paziente ,protesta perche sul 24 c'\u00E8 un leggero\r\n" +
                        "deficit\r\n" +
                        "> di chiusura,esteticamente visibile ma sicuramente la sua reazione \u00E8\r\n" +
                        "> sproporzionata,ha un atteggiamento rivendicativo come se l'avessi\r\n" +
                        "> triffata,rifiuta un allungamenti con compositi ceramici .io sono convinto\r\n" +
                        "che\r\n" +
                        "> a tirar giu il lavoro anche con tutte le cautele del caso rischio la\r\n" +
                        "rottura\r\n";

        output =
                "> mi trovo in difficolta,ho eseguito un lavoro di protesizzazione in " +
                        "porcellana " +
                        "su 24 25 26 premetto che i denti sottostanti erano pieni di otturazioni in " +
                        "amalgama ,la giovane paziente ,protesta perche sul 24 c'\u00E8 un leggero " +
                        "deficit " +
                        "di chiusura,esteticamente visibile ma sicuramente la sua reazione \u00E8 " +
                        "sproporzionata,ha un atteggiamento rivendicativo come se l'avessi " +
                        "triffata,rifiuta un allungamenti con compositi ceramici .io sono convinto " +
                        "che " +
                        "a tirar giu il lavoro anche con tutte le cautele del caso rischio la " +
                        "rottura\r\n";

        Assert.assertEquals(output, UnwrapText.unwrap(input, 79));

        // Controllo spazi
        input =
                "Prova per vedere se effettivamente il testo viene wrappato\r\n" +
                        "come dovrebbe. \r\n" +
                        "Chiss\u00E0 se funziona davvero \r\n" +
                        "o se va solo come gli pare \r\n" +
                        "> Prova per vedere se effettivamente il testo viene wrappato \r\n" +
                        "come \r\n" +
                        "> dovrebbe. \r\n";

        output =
                "Prova per vedere se effettivamente il testo viene wrappato come dovrebbe. \r\n" +
                        "Chiss\u00E0 se funziona davvero \r\n" +
                        "o se va solo come gli pare \r\n" +
                        "> Prova per vedere se effettivamente il testo viene wrappato come dovrebbe. \r\n";

        Assert.assertEquals(output, UnwrapText.unwrap(input, 79));
    }

}
