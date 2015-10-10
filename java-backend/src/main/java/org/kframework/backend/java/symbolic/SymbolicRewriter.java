// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.kframework.backend.java.builtins.BoolToken;
import org.kframework.backend.java.builtins.FreshOperations;
import org.kframework.backend.java.builtins.MetaK;
import org.kframework.backend.java.indexing.RuleIndex;
import org.kframework.backend.java.kil.*;
import org.kframework.backend.java.strategies.TransitionCompositeStrategy;
import org.kframework.backend.java.util.Coverage;
import org.kframework.backend.java.util.JavaKRunState;
import org.kframework.backend.java.util.JavaTransition;
import org.kframework.kil.loader.Context;
import org.kframework.kompile.KompileOptions;
import org.kframework.krun.api.KRunGraph;
import org.kframework.krun.api.KRunState;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.rewriter.SearchType;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 *
 * @author AndreiS
 *
 */
public class SymbolicRewriter {

    private final JavaExecutionOptions javaOptions;
    private final TransitionCompositeStrategy strategy;
    private final Stopwatch stopwatch = Stopwatch.createUnstarted();
    private final Stopwatch ruleStopwatch = Stopwatch.createUnstarted();
    private final List<ConstrainedTerm> results = Lists.newArrayList();
    private final List<Rule> appliedRules = Lists.newArrayList();
    private final List<Map<Variable, Term>> substitutions = Lists.newArrayList();
    private KRunGraph executionGraph = null;
    private boolean transition;
    private final RuleIndex ruleIndex;
    private final KRunState.Counter counter;
    private SetMultimap<ConstrainedTerm, Rule> disabledRules = HashMultimap.create();

    @Inject
    public SymbolicRewriter(Definition definition, KompileOptions kompileOptions, JavaExecutionOptions javaOptions,
                            KRunState.Counter counter) {
        this.javaOptions = javaOptions;
        ruleIndex = definition.getIndex();
        this.counter = counter;
        this.strategy = new TransitionCompositeStrategy(kompileOptions.transition);
    }

    public KRunGraph getExecutionGraph() {
        return executionGraph;
    }

    public KRunState rewrite(ConstrainedTerm constrainedTerm, Context context, int bound, boolean computeGraph) {
        stopwatch.start();
        KRunState startState = new JavaKRunState(constrainedTerm, context, counter, Optional.of(0));
        if (computeGraph) {
            executionGraph = new KRunGraph();
            executionGraph.addVertex(startState);
        }
        KRunState crntState = startState;
        KRunState finalState = null;
        int step = 1;
        while (step <= bound || bound < 0) {
            /* get the first solution */
            computeRewriteStep(constrainedTerm, step, true);
            ConstrainedTerm result = getTransition(0);
            if (result != null) {
                if (computeGraph) {
                    KRunState nextState = new JavaKRunState(result, context, counter, Optional.of(step));
                    JavaTransition javaTransition = new JavaTransition(
                            getRule(0), getSubstitution(0), context);
                    executionGraph.addEdge(javaTransition, crntState, nextState);
                    crntState = nextState;
                }
                constrainedTerm = result;
                if (step == bound) {
                    finalState = new JavaKRunState(constrainedTerm, context, counter, Optional.of(step));
                }
            } else {
                finalState = new JavaKRunState(constrainedTerm, context, counter, Optional.of(step - 1));
                break;
            }
            step++;
        }

        stopwatch.stop();
        if (constrainedTerm.termContext().global().krunOptions.experimental.statistics) {
            System.err.println("[" + step + ", " + stopwatch + "]");
        }

        return finalState;
    }

    /**
     * Gets the rules that could be applied to a given term according to the
     * rule indexing mechanism.
     *
     * @param term
     *            the given term
     * @return a list of rules that could be applied
     */
    private List<Rule> getRules(Term term) {
        return ruleIndex.getRules(term);
    }

    private ConstrainedTerm getTransition(int n) {
        return n < results.size() ? results.get(n) : null;
    }

    private Rule getRule(int n) {
        return n < appliedRules.size() ? appliedRules.get(n) : null;
    }

    private Map<Variable, Term> getSubstitution(int n) {
        return n < substitutions.size() ? substitutions.get(n) : null;
    }

    private void computeRewriteStep(ConstrainedTerm subject, int step, boolean computeOne) {
        subject.termContext().setTopTerm(subject.term());
        results.clear();
        appliedRules.clear();
        substitutions.clear();

        RuleAuditing.setAuditingRule(javaOptions, step, subject.termContext().definition());

        // Applying a strategy to a list of rules divides the rules up into
        // equivalence classes of rules. We iterate through these equivalence
        // classes one at a time, seeing which one contains rules we can apply.
        strategy.reset(getRules(subject.term()));

        try {
            while (strategy.hasNext()) {
                transition = strategy.nextIsTransition();
                LinkedHashSet<Rule> rules = Sets.newLinkedHashSet(strategy.next());
                rules.removeAll(disabledRules.get(subject));

                ArrayList<Pair<ConstrainedTerm, Rule>> internalResults = Lists.newArrayList();
                ArrayList<Rule> failedRules = Lists.newArrayList();
                Stream<List<Pair<ConstrainedTerm, Rule>>> resultsStream = rules.stream()
                        .map(r -> Pair.of(computeRewriteStepByRule(subject, r), r))
                        .peek(p -> {
                            if (p.getLeft().isEmpty()) {
                                failedRules.add(p.getRight());
                            }
                        })
                        .map(Pair::getLeft)
                        .filter(l -> !l.isEmpty());
                if (computeOne) {
                    Optional<List<Pair<ConstrainedTerm, Rule>>> any = resultsStream.findAny();
                    if (any.isPresent()) {
                        internalResults.add(any.get().get(0));
                    }
                } else {
                    resultsStream.forEach(internalResults::addAll);
                }

                // If we've found matching results from one equivalence class then
                // we are done, as we can't match rules from two equivalence classes
                // in the same step.
                if (internalResults.size() > 0) {
                    internalResults.stream().map(Pair::getLeft).forEach(results::add);
                    SetMultimap<ConstrainedTerm, Rule> resultDisabledRules = HashMultimap.create();
                    internalResults.stream().forEach(p -> {
                        if (p.getRight().isCompiledForFastRewriting()) {
                            failedRules.stream()
                                    .filter(r -> r.readCells() != null && p.getRight().writeCells() != null)
                                    .filter(r -> Sets.intersection(r.readCells(), p.getRight().writeCells()).isEmpty())
                                    .forEach(r -> resultDisabledRules.put(p.getLeft(), r));
                            disabledRules.get(subject).stream()
                                    .filter(r -> r.readCells() != null && p.getRight().writeCells() != null)
                                    .filter(r -> Sets.intersection(r.readCells(), p.getRight().writeCells()).isEmpty())
                                    .forEach(r -> resultDisabledRules.put(p.getLeft(), r));
                        }
                    });
                    disabledRules = resultDisabledRules;
                    return;
                }
            }
        } finally {
            RuleAuditing.clearAuditingRule();
        }
    }

    private List<Pair<ConstrainedTerm, Rule>> computeRewriteStepByRule(ConstrainedTerm subject, Rule rule) {
        try {
            if (rule == RuleAuditing.getAuditingRule()) {
                RuleAuditing.beginAudit();
            } else if (RuleAuditing.isAuditBegun() && RuleAuditing.getAuditingRule() == null) {
                System.err.println("\nAuditing " + rule + "...\n");
            }

            return subject.unify(buildPattern(rule, subject.termContext()), rule.matchingInstructions(), rule.lhsOfReadCell(), rule.matchingVariables()).stream()
                    .peek(s -> {
                        RuleAuditing.succeed(rule);
                        appliedRules.add(rule);
                        substitutions.add(s.getLeft().substitution());
                        Coverage.print(subject.termContext().global().krunOptions.experimental.coverage, subject);
                        Coverage.print(subject.termContext().global().krunOptions.experimental.coverage, rule);
                    })
                    .map(s -> Pair.of(buildResult(rule, s.getLeft(), subject.term(), !s.getRight()), rule))
                    .collect(Collectors.toList());
        } catch (KEMException e) {
            e.exception.addTraceFrame("while evaluating rule at " + rule.getSource() + rule.getLocation());
            throw e;
        } finally {
            if (RuleAuditing.isAuditBegun()) {
                if (RuleAuditing.getAuditingRule() == rule) {
                    RuleAuditing.endAudit();
                }
                if (!RuleAuditing.isSuccess()
                        && RuleAuditing.getAuditingRule() == rule) {
                    throw RuleAuditing.fail();
                }
            }
        }
    }

    /**
     * Builds the pattern term used in unification by composing the left-hand
     * side of a specified rule and its preconditions.
     */
    public static ConstrainedTerm buildPattern(Rule rule, TermContext context) {
        return new ConstrainedTerm(
                rule.leftHandSide(),
                ConjunctiveFormula.of(context).add(rule.lookups()).addAll(rule.requires()));
    }

    /**
     * Builds the result of rewrite based on the unification constraint.
     * It applies the unification constraint on the right-hand side of the rewrite rule,
     * if the rule is not compiled for fast rewriting.
     * It uses build instructions, if the rule is compiled for fast rewriting.
     */
    public static ConstrainedTerm buildResult(
            Rule rule,
            ConjunctiveFormula constraint,
            Term subject,
            boolean expandPattern) {
        for (Variable variable : rule.freshConstants()) {
            constraint = constraint.add(
                    variable,
                    FreshOperations.freshOfSort(variable.sort(), constraint.termContext()));
        }
        constraint = constraint.addAll(rule.ensures()).simplify();

        Term term;

        /* apply the constraints substitution on the rule RHS */
        constraint.termContext().setTopConstraint(constraint);
        Set<Variable> substitutedVars = Sets.union(rule.freshConstants(), rule.matchingVariables());
        constraint = constraint.orientSubstitution(substitutedVars);
        if (rule.isCompiledForFastRewriting()) {
            term = AbstractKMachine.apply((CellCollection) subject, constraint.substitution(), rule, constraint.termContext());
        } else {
            term = rule.rightHandSide().substituteAndEvaluate(constraint.substitution(), constraint.termContext());
        }

        /* eliminate bindings of the substituted variables */
        constraint = constraint.removeBindings(substitutedVars);

        /* get fresh substitutions of rule variables */
        Map<Variable, Variable> renameSubst = Variable.rename(rule.variableSet());

        /* rename rule variables in the rule RHS */
        term = term.substituteWithBinders(renameSubst, constraint.termContext());
        /* rename rule variables in the constraints */
        constraint = ((ConjunctiveFormula) constraint.substituteWithBinders(renameSubst, constraint.termContext())).simplify();

        ConstrainedTerm result = new ConstrainedTerm(term, constraint);
        if (expandPattern) {
            if (rule.isCompiledForFastRewriting()) {
                result = new ConstrainedTerm(term.substituteAndEvaluate(constraint.substitution(), constraint.termContext()), constraint);
            }
            // TODO(AndreiS): move these some other place
            result = result.expandPatterns(true);
            if (result.constraint().isFalse() || result.constraint().checkUnsat()) {
                result = null;
            }
        }

        return result;
    }

    /**
     * Apply a specification rule
     */
    private ConstrainedTerm applyRule(ConstrainedTerm constrainedTerm, List<Rule> rules) {
        for (Rule rule : rules) {
            ruleStopwatch.reset();
            ruleStopwatch.start();

            ConstrainedTerm leftHandSideTerm = buildPattern(rule, constrainedTerm.termContext());
            ConjunctiveFormula constraint = constrainedTerm.matchImplies(leftHandSideTerm, true);
            if (constraint == null) {
                continue;
            }

            return buildResult(rule, constraint, null, true);
        }

        return null;
    }

    /**
     * Unifies the term with the pattern, and computes a map from variables in
     * the pattern to the terms they unify with. Adds as many search results
     * up to the bound as were found, and returns {@code true} if the bound has been reached.
     */
    private boolean addSearchResult(
            List<Substitution<Variable, Term>> searchResults,
            ConstrainedTerm initialTerm,
            Rule pattern,
            int bound) {
        assert Sets.intersection(initialTerm.term().variableSet(),
                initialTerm.constraint().substitution().keySet()).isEmpty();
        List<Substitution<Variable, Term>> discoveredSearchResults = PatternMatcher.match(
                initialTerm.term(),
                pattern,
                initialTerm.termContext());
        for (Substitution<Variable, Term> searchResult : discoveredSearchResults) {
            searchResults.add(searchResult);
            if (searchResults.size() == bound) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param initialTerm
     * @param pattern the pattern we are searching for
     * @param bound a negative value specifies no bound
     * @param depth a negative value specifies no bound
     * @param searchType defines when we will attempt to match the pattern

     * @param computeGraph determines whether the transition graph should be computed.
     * @return a list of substitution mappings for results that matched the pattern
     */
    public List<Substitution<Variable,Term>> search(
            Term initialTerm,
            Rule pattern,
            int bound,
            int depth,
            SearchType searchType,
            TermContext context,
            boolean computeGraph) {
        stopwatch.start();

        Context kilContext = context.definition().context();
        JavaKRunState crntState = new JavaKRunState(initialTerm, kilContext, counter);
        if (computeGraph) {
            executionGraph = new KRunGraph();
            executionGraph.addVertex(crntState);
        }

        List<Substitution<Variable,Term>> searchResults = Lists.newArrayList();
        Set<ConstrainedTerm> visited = Sets.newHashSet();

        ConstrainedTerm initCnstrTerm = new ConstrainedTerm(initialTerm, context);

        // If depth is 0 then we are just trying to match the pattern.
        // A more clean solution would require a bit of a rework to how patterns
        // are handled in krun.Main when not doing search.
        if (depth == 0) {
            addSearchResult(searchResults, initCnstrTerm, pattern, bound);
            stopwatch.stop();
            if(context.global().krunOptions.experimental.statistics)
                System.err.println("[" + visited.size() + "states, " + 0 + "steps, " + stopwatch + "]");
            return searchResults;
        }

        // The search queues will map terms to their depth in terms of transitions.
        Map<ConstrainedTerm, Integer> queue = Maps.newLinkedHashMap();
        Map<ConstrainedTerm, Integer> nextQueue = Maps.newLinkedHashMap();

        visited.add(initCnstrTerm);
        queue.put(initCnstrTerm, 0);

        if (searchType == SearchType.ONE) {
            depth = 1;
        }
        if (searchType == SearchType.STAR) {
            if (addSearchResult(searchResults, initCnstrTerm, pattern, bound)) {
                stopwatch.stop();
                if(context.global().krunOptions.experimental.statistics)
                    System.err.println("[" + visited.size() + "states, " + 0 + "steps, " + stopwatch + "]");
                return searchResults;
            }
        }

        int step;
    label:
        for (step = 0; !queue.isEmpty(); ++step) {
            for (Map.Entry<ConstrainedTerm, Integer> entry : queue.entrySet()) {
                ConstrainedTerm term = entry.getKey();
                Integer currentDepth = entry.getValue();

                if (computeGraph) {
                    crntState = new JavaKRunState(term.term(), kilContext, counter);
                }

                computeRewriteStep(term, step, false);
//                    System.out.println(step);
//                    System.err.println(term);
//                    for (ConstrainedTerm r : results) {
//                        System.out.println(r);
//                    }

                if (results.isEmpty() && searchType == SearchType.FINAL) {
                    if (addSearchResult(searchResults, term, pattern, bound)) {
                        break label;
                    }
                }

                for (int resultIndex = 0; resultIndex < results.size(); resultIndex++) {
                    ConstrainedTerm result = results.get(resultIndex);
                    if (computeGraph) {
                        JavaKRunState resultState = new JavaKRunState(result.term(), kilContext, counter);
                        JavaTransition javaTransition = new JavaTransition(appliedRules.get(resultIndex),substitutions.get(resultIndex), kilContext);
                        executionGraph.addVertex(resultState);
                        executionGraph.addEdge(javaTransition, crntState, resultState);
                    }
                    if (!transition) {
                        nextQueue.put(result, currentDepth);
                        break;
                    } else {
                        // Continue searching if we haven't reached our target
                        // depth and we haven't already visited this state.
                        if (currentDepth + 1 != depth && visited.add(result)) {
                            nextQueue.put(result, currentDepth + 1);
                        }
                        // If we aren't searching for only final results, then
                        // also add this as a result if it matches the pattern.
                        if (searchType != SearchType.FINAL || currentDepth + 1 == depth) {
                            if (addSearchResult(searchResults, result, pattern, bound)) {
                                break label;
                            }
                        }
                    }
                }
            }
//                System.out.println("+++++++++++++++++++++++");

            /* swap the queues */
            Map<ConstrainedTerm, Integer> temp;
            temp = queue;
            queue = nextQueue;
            nextQueue = temp;
            nextQueue.clear();
        }

        stopwatch.stop();
        if (context.global().krunOptions.experimental.statistics) {
            System.err.println("[" + visited.size() + "states, " + step + "steps, " + stopwatch + "]");
        }

        return searchResults;
    }

    public List<ConstrainedTerm> proveRule(
            ConstrainedTerm initialTerm,
            ConstrainedTerm targetTerm,
            List<Rule> rules) {
        List<ConstrainedTerm> proofResults = new ArrayList<>();
        Set<ConstrainedTerm> visited = new HashSet<>();
        List<ConstrainedTerm> queue = new ArrayList<>();
        List<ConstrainedTerm> nextQueue = new ArrayList<>();

        initialTerm = initialTerm.expandPatterns(true);

        visited.add(initialTerm);
        queue.add(initialTerm);
        boolean guarded = false;
        int step = 0;
        while (!queue.isEmpty()) {
            step++;
//            System.err.println("step #" + step);
            for (ConstrainedTerm term : queue) {
                if (term.implies(targetTerm)) {
                    continue;
                }

                List<Term> leftKContents = term.term().getCellContentsByName(CellLabel.K);
                List<Term> rightKContents = targetTerm.term().getCellContentsByName(CellLabel.K);
                // TODO(YilongL): the `get(0)` seems hacky
                if (leftKContents.size() == 1 && rightKContents.size() == 1) {
                    Pair<Term, Variable> leftKPattern = splitKContent(leftKContents.get(0));
                    Pair<Term, Variable> rightKPattern = splitKContent(rightKContents.get(0));
                    if (leftKPattern.getRight() != null && rightKPattern.getRight() != null
                            && leftKPattern.getRight().equals(rightKPattern.getRight())) {
                        BoolToken matchable = MetaK.matchable(
                                leftKPattern.getLeft(),
                                rightKPattern.getLeft(),
                                term.termContext());
                        if (matchable != null && matchable.booleanValue()) {
                            proofResults.add(term);
                            continue;
                        }
                    }
                }

                if (guarded) {
                    ConstrainedTerm result = applyRule(term, rules);
                    if (result != null) {
                        if (visited.add(result))
                            nextQueue.add(result);
                        continue;
                    }
                }

                computeRewriteStep(term, step, false);
                if (results.isEmpty()) {
                    /* final term */
                    proofResults.add(term);
                } else {
//                    for (Rule rule : appliedRules) {
//                        System.err.println(rule.getLocation() + " " + rule.getSource());
//                    }

                    /* add helper rule */
                    HashSet<Variable> ruleVariables = new HashSet<>(initialTerm.variableSet());
                    ruleVariables.addAll(targetTerm.variableSet());

                    /*
                    rules.add(new Rule(
                            term.term().substitute(freshSubstitution, definition),
                            targetTerm.term().substitute(freshSubstitution, definition),
                            term.constraint().substitute(freshSubstitution, definition),
                            Collections.<Variable>emptyList(),
                            new SymbolicConstraint(definition).substitute(freshSubstitution, definition),
                            IndexingPair.getIndexingPair(term.term()),
                            new Attributes()));
                     */
                }

                for (int i = 0; getTransition(i) != null; ++i) {
                    ConstrainedTerm result = new ConstrainedTerm(
                            getTransition(i).term(),
                            getTransition(i).constraint().removeBindings(
                                    Sets.difference(
                                            getTransition(i).constraint().substitution().keySet(),
                                            initialTerm.variableSet())));
                    if (visited.add(result)) {
                        nextQueue.add(result);
                    }
                }
            }

            /* swap the queues */
            List<ConstrainedTerm> temp;
            temp = queue;
            queue = nextQueue;
            nextQueue = temp;
            nextQueue.clear();
            guarded = true;

            /*
            for (ConstrainedTerm result : queue) {
                System.err.println(result);
            }
            System.err.println("============================================================");
             */
        }

        return proofResults;
    }

    private static Pair<Term, Variable> splitKContent(Term kContent) {
        Variable frame = KSequence.getFrame(kContent);
        if (frame != null) {
            KSequence.Builder builder = KSequence.builder();
            KSequence.getElements(kContent).stream().forEach(builder::concatenate);
            kContent = builder.build();
        }
        return Pair.of(kContent, frame);
    }

}
