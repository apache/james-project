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

package org.apache.james.ai.classic;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * <p>
 * Determines probability that text contains Spam.
 * </p>
 * 
 * <p>
 * Based upon Paul Grahams' <a href="http://www.paulgraham.com/spam.html">A Plan
 * for Spam</a>. Extended to Paul Grahams' <a
 * href="http://paulgraham.com/better.html">Better Bayesian Filtering</a>.
 * </p>
 * 
 * <p>
 * Sample method usage:
 * </p>
 * 
 * <p>
 * Use: void addHam(Reader) and void addSpam(Reader)
 * 
 * methods to build up the Maps of ham & spam tokens/occurrences. Both addHam
 * and addSpam assume they're reading one message at a time, if you feed more
 * than one message per call, be sure to adjust the appropriate message counter:
 * hamMessageCount or spamMessageCount.
 * 
 * Then...
 * </p>
 * 
 * <p>
 * Use: void buildCorpus()
 * 
 * to build the final token/probabilities Map.
 * 
 * Use your own methods for persistent storage of either the individual ham/spam
 * corpus & message counts, and/or the final corpus.
 * 
 * Then you can...
 * </p>
 * 
 * <p>
 * Use: double computeSpamProbability(Reader)
 * 
 * to determine the probability that a particular text contains spam. A returned
 * result of 0.9 or above is an indicator that the text was spam.
 * </p>
 * 
 * <p>
 * If you use persistent storage, use: void setCorpus(Map)
 * 
 * before calling computeSpamProbability.
 * </p>
 * 
 * @since 2.3.0
 */

public class BayesianAnalyzer {

    /**
     * Number of "interesting" tokens to use to compute overall spamminess
     * probability.
     */
    private final static int MAX_INTERESTING_TOKENS = 15;

    /**
     * Minimum probability distance from 0.5 to consider a token "interesting"
     * to use to compute overall spamminess probability.
     */
    private final static double INTERESTINGNESS_THRESHOLD = 0.46;

    /**
     * Default token probability to use when a token has not been encountered
     * before.
     */
    final static double DEFAULT_TOKEN_PROBABILITY = 0.4;

    /** Map of ham tokens and their occurrences. */
    private Map<String, Integer> hamTokenCounts = new HashMap<>();

    /** Map of spam tokens and their occurrences. */
    private Map<String, Integer> spamTokenCounts = new HashMap<>();

    /** Number of ham messages analyzed. */
    private int hamMessageCount = 0;

    /** Number of spam messages analyzed. */
    private int spamMessageCount = 0;

    /** Final token/probability corpus. */
    private Map<String, Double> corpus = new HashMap<>();

    /**
     * Basic class constructor.
     */
    public BayesianAnalyzer() {
    }

    /**
     * Public setter for the hamTokenCounts Map.
     * 
     * @param hamTokenCounts
     *            The new ham Token counts Map.
     */
    public void setHamTokenCounts(Map<String, Integer> hamTokenCounts) {
        this.hamTokenCounts = hamTokenCounts;
    }

    /**
     * Public getter for the hamTokenCounts Map.
     */
    public Map<String, Integer> getHamTokenCounts() {
        return this.hamTokenCounts;
    }

    /**
     * Public setter for the spamTokenCounts Map.
     * 
     * @param spamTokenCounts
     *            The new spam Token counts Map.
     */
    public void setSpamTokenCounts(Map<String, Integer> spamTokenCounts) {
        this.spamTokenCounts = spamTokenCounts;
    }

    /**
     * Public getter for the spamTokenCounts Map.
     */
    public Map<String, Integer> getSpamTokenCounts() {
        return this.spamTokenCounts;
    }

    /**
     * Public setter for spamMessageCount.
     * 
     * @param spamMessageCount
     *            The new spam message count.
     */
    public void setSpamMessageCount(int spamMessageCount) {
        this.spamMessageCount = spamMessageCount;
    }

    /**
     * Public getter for spamMessageCount.
     */
    public int getSpamMessageCount() {
        return this.spamMessageCount;
    }

    /**
     * Public setter for hamMessageCount.
     * 
     * @param hamMessageCount
     *            The new ham message count.
     */
    public void setHamMessageCount(int hamMessageCount) {
        this.hamMessageCount = hamMessageCount;
    }

    /**
     * Public getter for hamMessageCount.
     */
    public int getHamMessageCount() {
        return this.hamMessageCount;
    }

    /**
     * Clears all analysis repositories and counters.
     */
    public void clear() {
        corpus.clear();

        tokenCountsClear();

        hamMessageCount = 0;
        spamMessageCount = 0;
    }

    /**
     * Clears token counters.
     */
    public void tokenCountsClear() {
        hamTokenCounts.clear();
        spamTokenCounts.clear();
    }

    /**
     * Public setter for corpus.
     * 
     * @param corpus
     *            The new corpus.
     */
    public void setCorpus(Map<String, Double> corpus) {
        this.corpus = corpus;
    }

    /**
     * Public getter for corpus.
     */
    public Map<String, Double> getCorpus() {
        return this.corpus;
    }

    /**
     * Builds the corpus from the existing ham & spam counts.
     */
    public void buildCorpus() {
        // Combine the known ham & spam tokens.
        Set<String> set = new HashSet<>(hamTokenCounts.size() + spamTokenCounts.size());
        set.addAll(hamTokenCounts.keySet());
        set.addAll(spamTokenCounts.keySet());
        Map<String, Double> tempCorpus = new HashMap<>(set.size());

        // Iterate through all the tokens and compute their new
        // individual probabilities.
        for (String token : set) {
            tempCorpus.put(token, computeProbability(token));
        }
        setCorpus(tempCorpus);
    }

    /**
     * Adds a message to the ham list.
     * 
     * @param stream
     *            A reader stream on the ham message to analyze
     * @throws IOException
     *             If any error occurs
     */
    public void addHam(Reader stream) throws java.io.IOException {
        addTokenOccurrences(stream, hamTokenCounts);
        hamMessageCount++;
    }

    /**
     * Adds a message to the spam list.
     * 
     * @param stream
     *            A reader stream on the spam message to analyze
     * @throws IOException
     *             If any error occurs
     */
    public void addSpam(Reader stream) throws java.io.IOException {
        addTokenOccurrences(stream, spamTokenCounts);
        spamMessageCount++;
    }

    /**
     * Computes the probability that the stream contains SPAM.
     * 
     * @param stream
     *            The text to be analyzed for Spamminess.
     * @return A 0.0 - 1.0 probability
     * @throws IOException
     *             If any error occurs
     */
    public double computeSpamProbability(Reader stream) throws java.io.IOException {
        // Build a set of the tokens in the Stream.
        Set<String> tokens = parse(stream);

        // Get the corpus to use in this run
        // A new corpus may be being built in the meantime
        Map<String, Double> workCorpus = getCorpus();

        // Assign their probabilities from the Corpus (using an additional
        // calculation to determine spamminess).
        SortedSet<TokenProbabilityStrength> tokenProbabilityStrengths = getTokenProbabilityStrengths(tokens, workCorpus);

        // Compute and return the overall probability that the
        // stream is SPAM.
        return computeOverallProbability(tokenProbabilityStrengths, workCorpus);
    }

    /**
     * Parses a stream into tokens, and updates the target Map with the
     * token/counts.
     * 
     * @param stream
     * @param target
     */
    private void addTokenOccurrences(Reader stream, Map<String, Integer> target) throws java.io.IOException {
        new TokenCounter(target).count(stream);
    }

    /**
     * Parses a stream into tokens, and returns a Set of the unique tokens
     * encountered.
     * 
     * @param stream
     * @return Set
     */
    private Set<String> parse(Reader stream) throws java.io.IOException {
        Set<String> tokens = new HashSet<>();
        new TokenCollector(tokens).collect(stream);
        // Return the unique set of tokens encountered.
        return tokens;
    }

    /**
     * Compute the probability that "token" is SPAM.
     * 
     * @param token
     * @return The probability that the token occurs within spam.
     */
    private double computeProbability(String token) {
        double hamFactor = 0;
        double spamFactor = 0;

        boolean foundInHam = false;
        boolean foundInSpam = false;

        double minThreshold = 0.01;
        double maxThreshold = 0.99;

        if (hamTokenCounts.containsKey(token)) {
            foundInHam = true;
        }

        if (spamTokenCounts.containsKey(token)) {
            foundInSpam = true;
        }

        if (foundInHam) {
            hamFactor = 2 * hamTokenCounts.get(token).doubleValue();
            if (!foundInSpam) {
                minThreshold = (hamFactor > 20) ? 0.0001 : 0.0002;
            }
        }

        if (foundInSpam) {
            spamFactor = spamTokenCounts.get(token).doubleValue();
            if (!foundInHam) {
                maxThreshold = (spamFactor > 10) ? 0.9999 : 0.9998;
            }
        }

        if ((hamFactor + spamFactor) < 5) {
            // This token hasn't been seen enough.
            return 0.4;
        }

        double spamFreq = Math.min(1.0, spamFactor / spamMessageCount);
        double hamFreq = Math.min(1.0, hamFactor / hamMessageCount);

        return Math.max(minThreshold, Math.min(maxThreshold, (spamFreq / (hamFreq + spamFreq))));
    }

    /**
     * Returns a SortedSet of TokenProbabilityStrength built from the Corpus and
     * the tokens passed in the "tokens" Set. The ordering is from the highest
     * strength to the lowest strength.
     * 
     * @param tokens
     * @param workCorpus
     * @return SortedSet of TokenProbabilityStrength objects.
     */
    private SortedSet<TokenProbabilityStrength> getTokenProbabilityStrengths(Set<String> tokens, Map<String, Double> workCorpus) {
        // Convert to a SortedSet of token probability strengths.
        SortedSet<TokenProbabilityStrength> tokenProbabilityStrengths = new TreeSet<>();

        for (String token : tokens) {
            TokenProbabilityStrength tps = new TokenProbabilityStrength();

            tps.token = token;

            if (workCorpus.containsKey(tps.token)) {
                tps.strength = Math.abs(0.5 - workCorpus.get(tps.token));
            } else {
                // This token has never been seen before,
                // we'll give it initially the default probability.
                Double corpusProbability = DEFAULT_TOKEN_PROBABILITY;
                tps.strength = Math.abs(0.5 - DEFAULT_TOKEN_PROBABILITY);
                boolean isTokenDegeneratedFound = false;

                Collection<String> degeneratedTokens = buildDegenerated(tps.token);
                Iterator<String> iDegenerated = degeneratedTokens.iterator();
                String tokenDegenerated;
                double strengthDegenerated;
                while (iDegenerated.hasNext()) {
                    tokenDegenerated = iDegenerated.next();
                    if (workCorpus.containsKey(tokenDegenerated)) {
                        Double probabilityTemp = workCorpus.get(tokenDegenerated);
                        strengthDegenerated = Math.abs(0.5 - probabilityTemp);
                        if (strengthDegenerated > tps.strength) {
                            isTokenDegeneratedFound = true;
                            tps.strength = strengthDegenerated;
                            corpusProbability = probabilityTemp;
                        }
                    }
                }
                // to reduce memory usage, put in the corpus only if the
                // probability is different from (stronger than) the default
                if (isTokenDegeneratedFound) {
                    synchronized (workCorpus) {
                        workCorpus.put(tps.token, corpusProbability);
                    }
                }
            }

            tokenProbabilityStrengths.add(tps);
        }

        return tokenProbabilityStrengths;
    }

    private Collection<String> buildDegenerated(String fullToken) {
        ArrayList<String> tokens = new ArrayList<>();
        String header;
        String token;
        String tokenLower;

        // look for a header string termination
        int headerEnd = fullToken.indexOf(':');
        if (headerEnd >= 0) {
            header = fullToken.substring(0, headerEnd);
            token = fullToken.substring(headerEnd);
        } else {
            header = "";
            token = fullToken;
        }

        // prepare a version of the token containing all lower case (for
        // performance reasons)
        tokenLower = token.toLowerCase(Locale.US);

        int end = token.length();
        do {
            if (!token.substring(0, end).equals(tokenLower.substring(0, end))) {
                tokens.add(header + tokenLower.substring(0, end));
                if (header.length() > 0) {
                    tokens.add(tokenLower.substring(0, end));
                }
            }
            if (end > 1 && token.charAt(0) >= 'A' && token.charAt(0) <= 'Z') {
                tokens.add(header + token.charAt(0) + tokenLower.substring(1, end));
                if (header.length() > 0) {
                    tokens.add(token.charAt(0) + tokenLower.substring(1, end));
                }
            }

            if (token.charAt(end - 1) != '!') {
                break;
            }

            end--;

            tokens.add(header + token.substring(0, end));
            if (header.length() > 0) {
                tokens.add(token.substring(0, end));
            }
        } while (end > 0);

        return tokens;
    }

    /**
     * Compute the spamminess probability of the interesting tokens in the
     * tokenProbabilities SortedSet.
     * 
     * @param tokenProbabilityStrengths
     * @param workCorpus
     * @return Computed spamminess.
     */
    private double computeOverallProbability(SortedSet<TokenProbabilityStrength> tokenProbabilityStrengths, Map<String, Double> workCorpus) {
        double p = 1.0;
        double np = 1.0;
        double tempStrength = 0.5;
        int count = MAX_INTERESTING_TOKENS;
        Iterator<TokenProbabilityStrength> iterator = tokenProbabilityStrengths.iterator();
        while ((iterator.hasNext()) && (count-- > 0 || tempStrength >= INTERESTINGNESS_THRESHOLD)) {
            TokenProbabilityStrength tps = iterator.next();
            tempStrength = tps.strength;

            // System.out.println(tps);

            double theDoubleValue = DEFAULT_TOKEN_PROBABILITY; // initialize it
                                                               // to the default
            Double theDoubleObject = workCorpus.get(tps.token);
            // if either the original token or a degeneration was found use the
            // double value, otherwise use the default
            if (theDoubleObject != null) {
                theDoubleValue = theDoubleObject;
            }
            p *= theDoubleValue;
            np *= (1.0 - theDoubleValue);
            // System.out.println("Token " + tps + ", p=" + theDoubleValue +
            // ", overall p=" + p / (p + np));
        }

        return (p / (p + np));
    }
}
