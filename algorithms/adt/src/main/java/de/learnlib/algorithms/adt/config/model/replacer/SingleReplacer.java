/* Copyright (C) 2017 TU Dortmund
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
package de.learnlib.algorithms.adt.config.model.replacer;

import de.learnlib.algorithms.adt.adt.ADT;
import de.learnlib.algorithms.adt.adt.ADTNode;
import de.learnlib.algorithms.adt.api.SubtreeReplacer;
import de.learnlib.algorithms.adt.config.model.ADSCalculator;
import de.learnlib.algorithms.adt.model.ReplacementResult;
import de.learnlib.algorithms.adt.util.ADTUtil;
import net.automatalib.automata.transout.MealyMachine;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author frohme
 */
@ParametersAreNonnullByDefault
public class SingleReplacer implements SubtreeReplacer {

	private final ADSCalculator adsCalculator;

	public SingleReplacer(final ADSCalculator adsProvider) {
		this.adsCalculator = adsProvider;
	}

	@Override
	public <S, I, O> Set<ReplacementResult<S, I, O>> computeReplacements(final MealyMachine<S, I, ?, O> hypothesis,
																		 final Alphabet<I> inputs,
																		 final ADT<S, I, O> adt) {

		final Set<ADTNode<S, I, O>> candidates = ADTUtil.collectADSNodes(adt.getRoot());
		candidates.remove(adt.getRoot());
		final Map<ADTNode<S, I, O>, Double> candidatesScore =
				candidates.stream().collect(Collectors.toMap(Function.identity(), node -> {
					final int resets = 1 + ADTUtil.collectResetNodes(node).size();
					final int finals = ADTUtil.collectLeaves(node).size();

					return resets / (double) finals;
				}));

		final List<ADTNode<S, I, O>> sortedCandidates = new ArrayList<>(candidates);
		Collections.sort(sortedCandidates, Comparator.comparingDouble(candidatesScore::get));

		for (final ADTNode<S, I, O> node : sortedCandidates) {
			final Set<S> targetStates =
					ADTUtil.collectLeaves(node).stream().map(ADTNode::getHypothesisState).collect(Collectors.toSet());

			// check if we can extendLeaf the parent ADS
			extensionCase:
			{
				final ADTNode<S, I, O> parentReset = node.getParent();

				assert ADTUtil.isResetNode(parentReset) : new IllegalStateException("should not happen");

				final Word<I> incomingTraceInput = ADTUtil.buildTraceForNode(parentReset).getFirst();

				Map<S, S> currentToInitialMapping =
						targetStates.stream().collect(Collectors.toMap(Function.identity(), Function.identity()));
				for (final I i : incomingTraceInput) {

					final Map<S, S> nextMapping = new HashMap<>();

					for (final S s : currentToInitialMapping.keySet()) {
						final S successor = hypothesis.getSuccessor(s, i);

						// converging states
						if (nextMapping.containsKey(successor)) {
							break extensionCase;
						}

						nextMapping.put(successor, currentToInitialMapping.get(s));
					}

					currentToInitialMapping = nextMapping;
				}

				final Optional<ADTNode<S, I, O>> potentialExtension =
						adsCalculator.compute(hypothesis, inputs, currentToInitialMapping.keySet());

				if (potentialExtension.isPresent()) {

					final ADTNode<S, I, O> extension = potentialExtension.get();

					for (final ADTNode<S, I, O> finalNode : ADTUtil.collectLeaves(extension)) {
						finalNode.setHypothesisState(currentToInitialMapping.get(finalNode.getHypothesisState()));
					}

					return Collections.singleton(new ReplacementResult<>(parentReset, potentialExtension.get()));
				}
			}

			// if we cannot save any resets, don't bother with replacement
			if (ADTUtil.collectResetNodes(node).isEmpty()) {
				continue;
			}

			final Optional<ADTNode<S, I, O>> potentialADS = adsCalculator.compute(hypothesis, inputs, targetStates);

			if (potentialADS.isPresent()) {
				return Collections.singleton(new ReplacementResult<>(node, potentialADS.get()));
			}
		}

		return Collections.emptySet();
	}
}
