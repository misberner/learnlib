/* Copyright (C) 2013-2018 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.learnlib.util;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import de.learnlib.api.algorithm.LearningAlgorithm;
import de.learnlib.api.logging.LearnLogger;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.filter.statistic.Counter;
import de.learnlib.util.statistics.SimpleProfiler;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.transout.MealyMachine;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * runs a learning experiment.
 *
 * @param <A>
 *
 * @author falkhowar
 */
@ParametersAreNonnullByDefault
public class Experiment<A> {

    private static final LearnLogger LOGGER = LearnLogger.getLogger(Experiment.class);
    private final ExperimentImpl<?, ?> impl;
    private boolean logModels;
    private boolean profile;
    private final Counter rounds = new Counter("rounds", "#");
    private A finalHypothesis;

    public <I, D> Experiment(LearningAlgorithm<? extends A, I, D> learningAlgorithm,
                             EquivalenceOracle<? super A, I, D> equivalenceAlgorithm,
                             Alphabet<I> inputs) {
        this.impl = new ExperimentImpl<>(learningAlgorithm, equivalenceAlgorithm, inputs);
    }

    /**
     *
     */
    @Nonnull
    public A run() {
        finalHypothesis = impl.run();
        return finalHypothesis;
    }

    @Nonnull
    public A getFinalHypothesis() {
        if (finalHypothesis == null) {
            throw new IllegalStateException("Experiment has not yet been run");
        }
        return finalHypothesis;
    }

    private void profileStart(String taskname) {
        if (profile) {
            SimpleProfiler.start(taskname);
        }
    }

    private void profileStop(String taskname) {
        if (profile) {
            SimpleProfiler.stop(taskname);
        }
    }

    /**
     * @param logModels
     *         the logModels to set
     */
    public void setLogModels(boolean logModels) {
        this.logModels = logModels;
    }

    /**
     * @param profile
     *         the profile to set
     */
    public void setProfile(boolean profile) {
        this.profile = profile;
    }

    /**
     * @return the rounds
     */
    @Nonnull
    public Counter getRounds() {
        return rounds;
    }

    public static class DFAExperiment<I> extends Experiment<DFA<?, I>> {

        public DFAExperiment(LearningAlgorithm<? extends DFA<?, I>, I, Boolean> learningAlgorithm,
                             EquivalenceOracle<? super DFA<?, I>, I, Boolean> equivalenceAlgorithm,
                             Alphabet<I> inputs) {
            super(learningAlgorithm, equivalenceAlgorithm, inputs);
        }
    }

    public static class MealyExperiment<I, O> extends Experiment<MealyMachine<?, I, ?, O>> {

        public MealyExperiment(LearningAlgorithm<? extends MealyMachine<?, I, ?, O>, I, Word<O>> learningAlgorithm,
                               EquivalenceOracle<? super MealyMachine<?, I, ?, O>, I, Word<O>> equivalenceAlgorithm,
                               Alphabet<I> inputs) {
            super(learningAlgorithm, equivalenceAlgorithm, inputs);
        }

    }

    private final class ExperimentImpl<I, D> {

        private final LearningAlgorithm<? extends A, I, D> learningAlgorithm;
        private final EquivalenceOracle<? super A, I, D> equivalenceAlgorithm;
        private final Alphabet<I> inputs;

        ExperimentImpl(LearningAlgorithm<? extends A, I, D> learningAlgorithm,
                       EquivalenceOracle<? super A, I, D> equivalenceAlgorithm,
                       Alphabet<I> inputs) {
            this.learningAlgorithm = learningAlgorithm;
            this.equivalenceAlgorithm = equivalenceAlgorithm;
            this.inputs = inputs;
        }

        public A run() {
            rounds.increment();
            LOGGER.logPhase("Starting round " + rounds.getCount());
            LOGGER.logPhase("Learning");
            profileStart("Learning");
            learningAlgorithm.startLearning();
            profileStop("Learning");

            boolean done = false;
            A hyp = null;
            while (!done) {
                hyp = learningAlgorithm.getHypothesisModel();
                if (logModels) {
                    LOGGER.logModel(hyp);
                }

                LOGGER.logPhase("Searching for counterexample");
                profileStart("Searching for counterexample");
                DefaultQuery<I, D> ce = equivalenceAlgorithm.findCounterExample(hyp, inputs);
                profileStop("Searching for counterexample");
                if (ce == null) {
                    done = true;
                    continue;
                }

                LOGGER.logCounterexample(ce.getInput().toString());

                // next round ...
                rounds.increment();
                LOGGER.logPhase("Starting round " + rounds.getCount());
                LOGGER.logPhase("Learning");
                profileStart("Learning");
                learningAlgorithm.refineHypothesis(ce);
                profileStop("Learning");
            }

            return hyp;
        }
    }
}
