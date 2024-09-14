/*
 * Copyright (C) 2024 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-formula-analysis-sat4j.
 *
 * formula-analysis-sat4j is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * formula-analysis-sat4j is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with formula-analysis-sat4j. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-formula-analysis-sat4j> for further information.
 */
package de.featjar.analysis.sat4j.computation;

import de.featjar.analysis.RuntimeContradictionException;
import de.featjar.analysis.RuntimeTimeoutException;
import de.featjar.analysis.sat4j.solver.ISelectionStrategy;
import de.featjar.analysis.sat4j.solver.ModalImplicationGraph;
import de.featjar.analysis.sat4j.solver.ModalImplicationGraph.Visitor;
import de.featjar.analysis.sat4j.solver.SAT4JSolutionSolver;
import de.featjar.analysis.sat4j.solver.SAT4JSolver;
import de.featjar.analysis.sat4j.twise.SampleBitIndex;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.Dependency;
import de.featjar.base.computation.IComputation;
import de.featjar.base.computation.Progress;
import de.featjar.base.data.Result;
import de.featjar.formula.assignment.ABooleanAssignment;
import de.featjar.formula.assignment.ABooleanAssignmentList;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignmentList;
import de.featjar.formula.assignment.BooleanClause;
import de.featjar.formula.assignment.BooleanClauseList;
import de.featjar.formula.assignment.BooleanSolution;
import de.featjar.formula.assignment.BooleanSolutionList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * YASA sampling algorithm. Generates configurations for a given propositional
 * formula such that t-wise feature coverage is achieved.
 *
 * @author Sebastian Krieter
 */
public class YASA2 extends ASAT4JAnalysis<BooleanSolutionList> {

    public static final Dependency<ICombinationSpecification> LITERALS =
            Dependency.newDependency(ICombinationSpecification.class);
    public static final Dependency<Integer> T = Dependency.newDependency(Integer.class);
    public static final Dependency<Integer> CONFIGURATION_LIMIT = Dependency.newDependency(Integer.class);
    public static final Dependency<Integer> ITERATIONS = Dependency.newDependency(Integer.class);
    public static final Dependency<Integer> INTERNAL_SOLUTION_LIMIT = Dependency.newDependency(Integer.class);

    public static final Dependency<ModalImplicationGraph> MIG = Dependency.newDependency(ModalImplicationGraph.class);

    @SuppressWarnings("rawtypes")
    public static final Dependency<ABooleanAssignmentList> INITIAL_SAMPLE =
            Dependency.newDependency(ABooleanAssignmentList.class);

    public static final Dependency<Boolean> ALLOW_CHANGE_TO_INITIAL_SAMPLE = Dependency.newDependency(Boolean.class);
    public static final Dependency<Boolean> INITIAL_SAMPLE_COUNTS_TOWARDS_CONFIGURATION_LIMIT =
            Dependency.newDependency(Boolean.class);

    public YASA2(IComputation<BooleanClauseList> booleanClauseList) {
        super(
                booleanClauseList,
                Computations.of(new NoneCombinationSpecification()),
                Computations.of(2),
                Computations.of(Integer.MAX_VALUE),
                Computations.of(2),
                Computations.of(100_000),
                new MIGBuilder(booleanClauseList),
                Computations.of(new BooleanAssignmentList()),
                Computations.of(Boolean.TRUE),
                Computations.of(Boolean.TRUE));
    }

    protected YASA2(YASA2 other) {
        super(other);
    }

    /**
     * Converts a set of single literals into a grouped expression list.
     *
     * @param literalSet the literal set
     * @return a grouped expression list (can be used as an input for the
     *         configuration generator).
     */
    public static List<List<BooleanClause>> convertLiterals(BooleanAssignment literalSet) {
        final List<List<BooleanClause>> arrayList = new ArrayList<>(literalSet.size());
        for (final Integer literal : literalSet.get()) {
            final List<BooleanClause> clauseList = new ArrayList<>(1);
            clauseList.add(new BooleanClause(literal));
            arrayList.add(clauseList);
        }
        return arrayList;
    }

    private class PartialConfiguration extends BooleanSolution {
        private static final long serialVersionUID = 1464084516529934929L;

        private final int id;
        private final boolean allowChange;

        private Visitor visitor;
        private ArrayList<BooleanSolution> solverSolutions;

        public PartialConfiguration(PartialConfiguration config) {
            super(config);
            id = config.id;
            allowChange = config.allowChange;
            visitor = config.visitor.getVisitorProvider().new Visitor(config.visitor, elements);
            solverSolutions = config.solverSolutions != null ? new ArrayList<>(config.solverSolutions) : null;
        }

        public PartialConfiguration(int id, boolean allowChange, ModalImplicationGraph mig, int... newliterals) {
            super(new int[n], false);
            this.id = id;
            this.allowChange = allowChange;
            visitor = mig.getVisitor(this.elements);
            solverSolutions = new ArrayList<>();
            visitor.propagate(newliterals);
        }

        public void initSolutionList() {
            solutionLoop:
            for (BooleanSolution solution : internalSample) {
                final int[] solverSolutionLiterals = solution.get();
                forEach(null);
                int end = visitor.getAddedLiteralCount();
                int[] literals = visitor.getAddedLiterals();
                for (int j = 0; j < end; j++) {
                    final int l = literals[j];
                    if (solverSolutionLiterals[Math.abs(l) - 1] != l) {
                        continue solutionLoop;
                    }
                }
                solverSolutions.add(solution);
            }
        }

        public void updateSolutionList(int lastIndex) {
            if (!isComplete()) {
                forEach(lastIndex, newLiteral -> {
                    final int k = Math.abs(newLiteral) - 1;
                    for (int j = solverSolutions.size() - 1; j >= 0; j--) {
                        final int[] solverSolutionLiterals =
                                solverSolutions.get(j).get();
                        if (solverSolutionLiterals[k] != newLiteral) {
                            final int last = solverSolutions.size() - 1;
                            Collections.swap(solverSolutions, j, last);
                            solverSolutions.remove(last);
                        }
                    }
                });
            }
        }

        public void forEach(IntConsumer consumer) {
            forEach(0, consumer);
        }

        public void forEach(int start, IntConsumer consumer) {
            final int end = visitor.getAddedLiteralCount();
            final int[] literals = visitor.getAddedLiterals();
            for (int i = start; i < end; i++) {
                consumer.accept(literals[i]);
            }
        }

        public int setLiteral(int... literals) {
            final int oldModelCount = visitor.getAddedLiteralCount();
            visitor.propagate(literals);
            return oldModelCount;
        }

        public void clear() {
            solverSolutions = null;
        }

        public boolean isComplete() {
            return visitor.getAddedLiteralCount() == n;
        }

        public int countLiterals() {
            return visitor.getAddedLiteralCount();
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }
    }

    private int n, tmax, maxSampleSize, iterations, internalConfigurationLimit;
    private boolean allowChangeToInitialSample, initialSampleCountsTowardsConfigurationLimit;
    private ICombinationSpecification variables;

    private SAT4JSolutionSolver solver;
    private ModalImplicationGraph mig;
    private Random random;

    private ABooleanAssignmentList<?> initialSample;
    private ArrayDeque<BooleanSolution> internalSample;
    private List<PartialConfiguration> bestSample;
    private List<PartialConfiguration> currentSample;

    private ArrayList<PartialConfiguration> candidateConfiguration;
    private SampleBitIndex internalSampleIndex;
    private SampleBitIndex currentSampleIndex;
    private SampleBitIndex bestSampleIndex;
    private PartialConfiguration newConfiguration;
    private int internalSolutionIndex;
    private int curSolutionId;
    private boolean overLimit;

    @Override
    public Result<BooleanSolutionList> compute(List<Object> dependencyList, Progress progress) {
        tmax = T.get(dependencyList);
        if (tmax < 1) {
            throw new IllegalArgumentException("Value for t must be grater than 0. Value was " + tmax);
        }

        iterations = ITERATIONS.get(dependencyList);
        if (iterations == 0) {
            throw new IllegalArgumentException("Iterations must not equal 0.");
        }
        if (iterations < 0) {
            iterations = Integer.MAX_VALUE;
        }

        internalConfigurationLimit = INTERNAL_SOLUTION_LIMIT.get(dependencyList);
        if (internalConfigurationLimit < 0) {
            throw new IllegalArgumentException(
                    "Internal solution limit must be greater than 0. Value was " + internalConfigurationLimit);
        }

        maxSampleSize = CONFIGURATION_LIMIT.get(dependencyList);
        if (maxSampleSize < 0) {
            throw new IllegalArgumentException(
                    "Configuration limit must be greater than 0. Value was " + maxSampleSize);
        }

        initialSample = INITIAL_SAMPLE.get(dependencyList);

        random = new Random(RANDOM_SEED.get(dependencyList));

        allowChangeToInitialSample = ALLOW_CHANGE_TO_INITIAL_SAMPLE.get(dependencyList);
        initialSampleCountsTowardsConfigurationLimit =
                INITIAL_SAMPLE_COUNTS_TOWARDS_CONFIGURATION_LIMIT.get(dependencyList);

        internalSample = new ArrayDeque<>(internalConfigurationLimit);
        internalSolutionIndex = 0;
        solver = initializeSolver(dependencyList);
        solver.setSelectionStrategy(ISelectionStrategy.random(random));
        mig = MIG.get(dependencyList);
        n = mig.size();
        internalSampleIndex = new SampleBitIndex(n);

        if (initialSampleCountsTowardsConfigurationLimit) {
            maxSampleSize = Math.max(maxSampleSize, maxSampleSize + initialSample.size());
        }

        tmax = Math.min(tmax, Math.max(n, 1));
        variables = LITERALS.get(dependencyList);

        if (variables instanceof NoneCombinationSpecification) {
            variables = new SingleCombinationSpecification(
                    new BooleanAssignment(new BooleanAssignment(IntStream.range(-n, n + 1)
                                    .filter(i -> i != 0)
                                    .toArray())
                            .removeAllVariables(
                                    Arrays.stream(mig.getCore()).map(Math::abs).toArray())),
                    tmax);
        }

        progress.setTotalSteps(iterations * variables.getTotalSteps());

        buildCombinations(progress);

        if (!overLimit && iterations > 1) {
            rebuildCombinations(progress);
        }

        return finalizeResult();
    }

    @Override
    protected SAT4JSolver newSolver(BooleanClauseList clauseList) {
        return new SAT4JSolutionSolver(clauseList);
    }

    @Override
    public Result<BooleanSolutionList> getIntermediateResult() {
        return finalizeResult();
    }

    private Result<BooleanSolutionList> finalizeResult() {
        if (bestSample != null) {
            BooleanSolutionList result = new BooleanSolutionList(bestSample.size());
            for (int j = bestSample.size() - 1; j >= 0; j--) {
                result.add(autoComplete(bestSample.get(j)));
            }
            return Result.of(result);
        } else {
            return Result.empty();
        }
    }

    private void buildCombinations(Progress monitor) {
        initSample();

        initRun();

        variables.stream().forEach(combinationLiterals -> {
            checkCancel();
            monitor.incrementCurrentStep();

            if (currentSampleIndex.test(combinationLiterals)) {
                return;
            }
            if (isCombinationInvalidMIG(combinationLiterals)) {
                return;
            }

            try {
                if (internalSampleIndex.test(combinationLiterals)) {
                    if (tryCover(combinationLiterals)) {
                        return;
                    }
                } else {
                    if (isCombinationInvalidSAT(combinationLiterals)) {
                        return;
                    }
                }

                if (tryCoverWithSat(combinationLiterals)) {
                    return;
                }
                newConfiguration(combinationLiterals);
            } finally {
                candidateConfiguration.clear();
                newConfiguration = null;
            }
        });
        setBestSolutionList();
    }

    private void rebuildCombinations(Progress monitor) {
        if (iterations > 1) {
            bestSampleIndex = new SampleBitIndex(bestSample, n);
        }

        for (int j = 1; j < iterations; j++) {
            checkCancel();
            variables.shuffle(random);
            initSample();
            initRun();
            variables.stream().forEach(combinationLiterals -> {
                if (currentSampleIndex.test(combinationLiterals)) {
                    return;
                }
                if (!bestSampleIndex.test(combinationLiterals)) {
                    return;
                }
                try {
                    if (tryCoverWithoutMIG(combinationLiterals)) {
                        return;
                    }
                    if (tryCoverWithSat(combinationLiterals)) {
                        return;
                    }
                    newConfiguration(combinationLiterals);
                } finally {
                    candidateConfiguration.clear();
                    newConfiguration = null;
                }
            });
            setBestSolutionList();
        }
    }

    private void setBestSolutionList() {
        if (bestSample == null || bestSample.size() > currentSample.size()) {
            bestSample = currentSample;
        }
    }

    private void initSample() {
        curSolutionId = 0;
        overLimit = false;
        currentSample = new ArrayList<>();
        for (ABooleanAssignment config : initialSample) {
            if (currentSample.size() < maxSampleSize) {
                PartialConfiguration initialConfiguration =
                        new PartialConfiguration(curSolutionId++, allowChangeToInitialSample, mig, config.get());
                if (allowChangeToInitialSample) {
                    initialConfiguration.initSolutionList();
                }
                if (initialConfiguration.isComplete()) {
                    initialConfiguration.clear();
                }
                currentSample.add(initialConfiguration);
            } else {
                overLimit = true;
            }
        }
        currentSampleIndex = new SampleBitIndex(currentSample, n);
    }

    private void initRun() {
        newConfiguration = null;
        candidateConfiguration = new ArrayList<>();
        Collections.sort(currentSample, (a, b) -> b.countLiterals() - a.countLiterals());
    }

    private void select(PartialConfiguration solution, int[] literals) {
        final int lastIndex = solution.setLiteral(literals);
        solution.forEach(lastIndex, l -> currentSampleIndex.set(solution.id, l));
        solution.updateSolutionList(lastIndex);
    }

    private boolean tryCover(int[] literals) {
        return newConfiguration == null ? tryCoverWithoutMIG(literals) : tryCoverWithMIG(literals);
    }

    private boolean tryCoverWithoutMIG(int[] literals) {
        configLoop:
        for (final PartialConfiguration configuration : currentSample) {
            if (configuration.allowChange && !configuration.isComplete()) {
                final int[] literals2 = configuration.get();
                for (int i = 0; i < literals.length; i++) {
                    final int l = literals[i];
                    if (literals2[Math.abs(l) - 1] == -l) {
                        continue configLoop;
                    }
                }
                if (isSelectionPossibleSol(configuration, literals)) {
                    select(configuration, literals);
                    change(configuration);
                    return true;
                }
                candidateConfiguration.add(configuration);
            }
        }
        return false;
    }

    private boolean tryCoverWithMIG(int[] literals) {
        int end = newConfiguration.visitor.getAddedLiteralCount();
        int[] newConfigurationLiterals = newConfiguration.visitor.getAddedLiterals();
        configLoop:
        for (final PartialConfiguration configuration : currentSample) {
            if (configuration.allowChange && !configuration.isComplete()) {
                final int[] literals2 = configuration.get();
                for (int i = 0; i < end; i++) {
                    final int l = newConfigurationLiterals[i];
                    if (literals2[Math.abs(l) - 1] == -l) {
                        continue configLoop;
                    }
                }
                if (isSelectionPossibleSol(configuration, literals)) {
                    select(configuration, literals);
                    change(configuration);
                    return true;
                }
                candidateConfiguration.add(configuration);
            }
        }
        return false;
    }

    private void addToCandidateList(int[] literals) {
        if (newConfiguration != null) {
            int end = newConfiguration.visitor.getAddedLiteralCount();
            int[] newConfigurationLiterals = newConfiguration.visitor.getAddedLiterals();
            configLoop:
            for (final PartialConfiguration configuration : currentSample) {
                if (configuration.allowChange && !configuration.isComplete()) {
                    final int[] literals2 = configuration.get();
                    for (int i = 0; i < end; i++) {
                        final int l = newConfigurationLiterals[i];
                        if (literals2[Math.abs(l) - 1] == -l) {
                            continue configLoop;
                        }
                    }
                    candidateConfiguration.add(configuration);
                }
            }
        } else {
            configLoop:
            for (final PartialConfiguration configuration : currentSample) {
                if (configuration.allowChange && !configuration.isComplete()) {
                    final int[] literals2 = configuration.get();
                    for (int i = 0; i < literals.length; i++) {
                        final int l = literals[i];
                        if (literals2[Math.abs(l) - 1] == -l) {
                            continue configLoop;
                        }
                    }
                    candidateConfiguration.add(configuration);
                }
            }
        }
    }

    private void change(final PartialConfiguration configuration) {
        if (configuration.isComplete()) {
            configuration.clear();
        }
        Collections.sort(currentSample, (a, b) -> b.countLiterals() - a.countLiterals());
    }

    private boolean isCombinationInvalidMIG(int[] literals) {
        try {
            newConfiguration = new PartialConfiguration(curSolutionId++, true, mig, literals);
        } catch (RuntimeContradictionException e) {
            return true;
        }
        return false;
    }

    private boolean isCombinationInvalidSAT(int[] literals) {
        final int orgAssignmentLength = solver.getAssignment().size();
        try {
            if (newConfiguration != null) {
                newConfiguration.forEach(solver.getAssignment()::add);
            } else {
                for (int i = 0; i < literals.length; i++) {
                    solver.getAssignment().add(literals[i]);
                }
            }
            Result<Boolean> hasSolution = solver.hasSolution();
            if (hasSolution.isPresent()) {
                if (hasSolution.get()) {
                    BooleanSolution e = addSolverSolution();

                    addToCandidateList(literals);
                    PartialConfiguration compatibleConfiguration = null;
                    for (PartialConfiguration c : candidateConfiguration) {
                        if (!c.containsAnyNegated(e)) {
                            if (compatibleConfiguration == null) {
                                compatibleConfiguration = c;
                            } else {
                                c.solverSolutions.add(e);
                            }
                        }
                    }
                    if (compatibleConfiguration != null) {
                        select(compatibleConfiguration, literals);
                        compatibleConfiguration.solverSolutions.add(e);
                        change(compatibleConfiguration);
                        return true;
                    }
                    return false;
                } else {
                    return true;
                }
            } else {
                return true;
            }
        } finally {
            solver.getAssignment().clear(orgAssignmentLength);
        }
    }

    private boolean tryCoverWithSat(int[] literals) {
        for (PartialConfiguration configuration : candidateConfiguration) {
            if (trySelectSat(configuration, literals)) {
                change(configuration);
                return true;
            }
        }
        return false;
    }

    private void newConfiguration(int[] literals) {
        if (currentSample.size() < maxSampleSize) {
            if (newConfiguration == null) {
                newConfiguration = new PartialConfiguration(curSolutionId++, true, mig, literals);
            }
            newConfiguration.initSolutionList();
            currentSample.add(newConfiguration);
            change(newConfiguration);

            newConfiguration.forEach(l -> currentSampleIndex.set(newConfiguration.id, l));
        } else {
            overLimit = true;
        }
    }

    private BooleanSolution autoComplete(PartialConfiguration configuration) {
        if (configuration.allowChange && !configuration.isComplete()) {
            if (configuration.solverSolutions != null && configuration.solverSolutions.size() > 0) {
                final int[] configuration2 =
                        configuration.solverSolutions.get(0).get();
                System.arraycopy(configuration2, 0, configuration.get(), 0, configuration.size());
                configuration.clear();
            } else {
                final int orgAssignmentSize = setUpSolver(configuration);
                try {
                    Result<Boolean> hasSolution = solver.hasSolution();
                    if (hasSolution.isPresent()) {
                        if (hasSolution.get()) {
                            final int[] internalSolution = solver.getInternalSolution();
                            System.arraycopy(internalSolution, 0, configuration.get(), 0, configuration.size());
                            configuration.clear();
                        } else {
                            throw new RuntimeContradictionException();
                        }
                    } else {
                        throw new RuntimeTimeoutException();
                    }
                } finally {
                    solver.getAssignment().clear(orgAssignmentSize);
                }
            }
        }
        return new BooleanSolution(configuration.get(), false);
    }

    private boolean isSelectionPossibleSol(PartialConfiguration configuration, int[] literals) {
        for (BooleanSolution configuration2 : configuration.solverSolutions) {
            if (!configuration2.containsAnyNegated(literals)) {
                return true;
            }
        }
        return false;
    }

    private boolean trySelectSat(PartialConfiguration configuration, final int[] literals) {
        final int oldModelCount = configuration.visitor.getAddedLiteralCount();
        try {
            configuration.visitor.propagate(literals);
        } catch (RuntimeException e) {
            configuration.visitor.reset(oldModelCount);
            return false;
        }

        final int orgAssignmentSize = setUpSolver(configuration);
        try {
            if (newConfiguration != null) {
                int[] configurationLiterals = configuration.get();
                newConfiguration.forEach(l -> {
                    if (configurationLiterals[Math.abs(l) - 1] == 0) {
                        solver.getAssignment().add(l);
                    }
                });
            } else {
                for (int i = 0; i < literals.length; i++) {
                    int l = literals[i];
                    if (configuration.get()[Math.abs(l) - 1] == 0) {
                        solver.getAssignment().add(l);
                    }
                }
            }
            Result<Boolean> hasSolution = solver.hasSolution();
            if (hasSolution.isPresent()) {
                if (hasSolution.get()) {
                    final BooleanSolution e = addSolverSolution();

                    configuration.forEach(oldModelCount, l -> currentSampleIndex.set(configuration.id, l));
                    configuration.updateSolutionList(oldModelCount);
                    configuration.solverSolutions.add(e);
                    return true;
                } else {
                    configuration.visitor.reset(oldModelCount);
                }
            } else {
                configuration.visitor.reset(oldModelCount);
            }
        } finally {
            solver.getAssignment().clear(orgAssignmentSize);
        }
        return false;
    }

    private BooleanSolution addSolverSolution() {
        if (internalSample.size() == internalConfigurationLimit) {
            internalSolutionIndex = (internalSolutionIndex + 1) % internalConfigurationLimit;
            internalSample.removeFirst();
        }
        final int[] solution = solver.getInternalSolution();
        final BooleanSolution e = new BooleanSolution(Arrays.copyOf(solution, solution.length), false);
        internalSample.add(e);
        solver.shuffleOrder(random);

        internalSampleIndex.set(internalSolutionIndex, e);
        return e;
    }

    private int setUpSolver(PartialConfiguration configuration) {
        final int orgAssignmentSize = solver.getAssignment().size();
        configuration.forEach(solver.getAssignment()::add);
        return orgAssignmentSize;
    }
}
