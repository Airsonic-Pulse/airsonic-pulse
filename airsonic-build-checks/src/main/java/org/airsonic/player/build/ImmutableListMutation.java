/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.build;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

import java.util.ArrayList;
import java.util.List;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.instanceMethod;
import static com.google.errorprone.matchers.Matchers.staticMethod;

/**
 * Flags mutation of a List value known to be unmodifiable (#302, the #296/#351 crash class): the
 * mutated value is either the direct result of an immutable-List producer, or a local variable
 * whose value at the point of the mutation came from one.
 * <p>
 * Mutations: the instance mutators of {@code Collection} and {@code List}, and the static
 * {@code java.util.Collections} helpers that mutate their first argument ({@code sort},
 * {@code shuffle}, {@code reverse}, {@code swap}, {@code rotate}, {@code fill},
 * {@code replaceAll}, {@code copy}, {@code addAll}) (#380, the #414 shape).
 * <p>
 * Locals: one that is never reassigned is judged by its initialiser. One that is reassigned is
 * judged by the last assignment (or the initialiser) that precedes the mutation in source order,
 * and only when source order decides: that assignment must be a plain statement of a block that
 * also contains the mutation, and no assignment to the local may share a loop with the mutation.
 * Anything else is left alone. A list passed to another method as an argument is not followed.
 * <p>
 * Producers: {@code Stream.toList()}, {@code List.of(...)}, {@code List.copyOf(...)},
 * {@code collect(Collectors.toUnmodifiableList())}, {@code Collections.unmodifiableList/
 * unmodifiableCollection}, Spring Data's paged results ({@code Slice/Page.getContent()}), any
 * method annotated {@link UnmodifiableResult}, and {@code Arrays.asList(...)} for the
 * size-changing mutators only ({@code set/sort/replaceAll} are legal on it).
 */
@AutoService(BugChecker.class)
@BugPattern(
        summary = "Structural mutation of an unmodifiable List throws UnsupportedOperationException"
                + " at runtime; copy it first: new ArrayList<>(...)",
        severity = ERROR)
public class ImmutableListMutation extends BugChecker implements MethodInvocationTreeMatcher {

    private static final long serialVersionUID = 1L;

    /** Mutators illegal on every producer, including the fixed-size Arrays.asList. */
    private static final Matcher<ExpressionTree> COLLECTION_MUTATOR = instanceMethod()
            .onDescendantOf("java.util.Collection")
            .namedAnyOf("add", "addAll", "remove", "removeAll", "removeIf", "retainAll", "clear");

    /** Element-replacing mutators: legal on Arrays.asList, illegal on the immutable producers. */
    private static final Matcher<ExpressionTree> LIST_ONLY_MUTATOR = instanceMethod()
            .onDescendantOf("java.util.List")
            .namedAnyOf("set", "sort", "replaceAll");

    /** Collections statics that replace elements of their first argument; legal on Arrays.asList. */
    private static final Matcher<ExpressionTree> COLLECTIONS_ELEMENT_MUTATOR = staticMethod()
            .onClass("java.util.Collections")
            .namedAnyOf("sort", "shuffle", "reverse", "swap", "rotate", "fill", "replaceAll", "copy");

    /** The one Collections static that grows its first argument. */
    private static final Matcher<ExpressionTree> COLLECTIONS_ADD_ALL =
            staticMethod().onClass("java.util.Collections").named("addAll");

    private static final Matcher<ExpressionTree> IMMUTABLE_PRODUCER = anyOf(
            instanceMethod().onDescendantOf("java.util.stream.Stream").named("toList"),
            staticMethod().onClass("java.util.List").named("of"),
            staticMethod().onClass("java.util.List").named("copyOf"),
            staticMethod().onClass("java.util.Collections")
                    .namedAnyOf("unmodifiableList", "unmodifiableCollection"),
            // Spring Data paged results: getContent is declared on Slice, Page extends it
            instanceMethod().onDescendantOf("org.springframework.data.domain.Slice").named("getContent"));

    private static final Matcher<ExpressionTree> AS_LIST =
            staticMethod().onClass("java.util.Arrays").named("asList");

    private static final Matcher<ExpressionTree> STREAM_COLLECT =
            instanceMethod().onDescendantOf("java.util.stream.Stream").named("collect");

    // Collectors.toList() is deliberately NOT a producer: its result is mutable
    private static final Matcher<ExpressionTree> TO_UNMODIFIABLE_LIST =
            staticMethod().onClass("java.util.stream.Collectors").named("toUnmodifiableList");

    private static final String ANNOTATION = "org.airsonic.player.build.UnmodifiableResult";

    /** What produced the mutated value. */
    private enum Producer {
        NONE,
        /** Arrays.asList: fixed-size but element-replaceable. */
        FIXED_SIZE,
        /** Fully unmodifiable. */
        IMMUTABLE
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        boolean sizeChanging;
        ExpressionTree mutated;
        if (COLLECTION_MUTATOR.matches(tree, state)) {
            sizeChanging = true;
            mutated = ASTHelpers.getReceiver(tree);
        } else if (LIST_ONLY_MUTATOR.matches(tree, state)) {
            sizeChanging = false;
            mutated = ASTHelpers.getReceiver(tree);
        } else if (COLLECTIONS_ADD_ALL.matches(tree, state)) {
            sizeChanging = true;
            mutated = firstArgument(tree);
        } else if (COLLECTIONS_ELEMENT_MUTATOR.matches(tree, state)) {
            sizeChanging = false;
            mutated = firstArgument(tree);
        } else {
            return Description.NO_MATCH;
        }
        if (mutated == null) {
            return Description.NO_MATCH;
        }
        Producer producer = producerOf(ASTHelpers.stripParentheses(mutated), state);
        if (producer == Producer.NONE || (producer == Producer.FIXED_SIZE && !sizeChanging)) {
            return Description.NO_MATCH;
        }
        return describeMatch(tree);
    }

    private static ExpressionTree firstArgument(MethodInvocationTree tree) {
        return tree.getArguments().isEmpty() ? null : tree.getArguments().get(0);
    }

    private Producer producerOf(ExpressionTree receiver, VisitorState state) {
        if (receiver instanceof MethodInvocationTree chained) {
            return classify(chained, state);
        }
        if (receiver instanceof IdentifierTree ident) {
            return localProducer(ident, state);
        }
        return Producer.NONE;
    }

    private Producer classify(MethodInvocationTree invocation, VisitorState state) {
        if (IMMUTABLE_PRODUCER.matches(invocation, state)) {
            return Producer.IMMUTABLE;
        }
        if (ASTHelpers.hasAnnotation(ASTHelpers.getSymbol(invocation), ANNOTATION, state)) {
            return Producer.IMMUTABLE;
        }
        if (STREAM_COLLECT.matches(invocation, state)
                && invocation.getArguments().size() == 1
                && invocation.getArguments().get(0) instanceof MethodInvocationTree collector
                && TO_UNMODIFIABLE_LIST.matches(collector, state)) {
            return Producer.IMMUTABLE;
        }
        if (AS_LIST.matches(invocation, state)) {
            return Producer.FIXED_SIZE;
        }
        return Producer.NONE;
    }

    /**
     * Classifies a local variable. Never reassigned: its initialiser decides. Reassigned: the
     * last assignment (or the initialiser) before the mutation in source order decides, but only
     * where source order is the execution order; see {@link #governingProducer}.
     */
    private Producer localProducer(IdentifierTree ident, VisitorState state) {
        Element sym = ASTHelpers.getSymbol(ident);
        if (sym == null || sym.getKind() != ElementKind.LOCAL_VARIABLE) {
            return Producer.NONE;
        }
        TreePath enclosingMethod = state.findPathToEnclosing(MethodTree.class);
        if (enclosingMethod == null) {
            return Producer.NONE;
        }
        List<TreePath> declarations = new ArrayList<>();
        List<TreePath> assignments = new ArrayList<>();
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitVariable(VariableTree node, Void unused) {
                if (sym.equals(ASTHelpers.getSymbol(node))) {
                    declarations.add(getCurrentPath());
                }
                return super.visitVariable(node, unused);
            }

            @Override
            public Void visitAssignment(AssignmentTree node, Void unused) {
                Tree variable = node.getVariable();
                if (variable instanceof IdentifierTree id && sym.equals(ASTHelpers.getSymbol(id))) {
                    assignments.add(getCurrentPath());
                }
                return super.visitAssignment(node, unused);
            }
        }.scan(enclosingMethod, null);

        if (declarations.isEmpty()) {
            return Producer.NONE;
        }
        if (assignments.isEmpty()) {
            return assignedProducer(declarations.get(0).getLeaf(), state);
        }
        List<TreePath> definitions = new ArrayList<>(declarations);
        definitions.addAll(assignments);
        return governingProducer(definitions, assignments, state);
    }

    /**
     * The producer of the value a reassigned local holds at the mutation, or NONE when that
     * cannot be read off the source order.
     * <p>
     * The governing definition is the last one that starts before the mutation. It counts only
     * when it always runs before the mutation and nothing can run in between: it must be a plain
     * statement of a block that also contains the mutation (so not inside an if, switch, try,
     * loop header, conditional or lambda of its own), and no assignment to the local may sit in a
     * loop that also contains the mutation (a later iteration would see that value instead).
     */
    private Producer governingProducer(List<TreePath> definitions, List<TreePath> assignments, VisitorState state) {
        TreePath mutation = state.getPath();
        int mutationStart = ASTHelpers.getStartPosition(mutation.getLeaf());
        TreePath governing = null;
        int governingStart = -1;
        for (TreePath definition : definitions) {
            int start = ASTHelpers.getStartPosition(definition.getLeaf());
            if (start < mutationStart && start > governingStart && !isAncestor(definition.getLeaf(), mutation)) {
                governing = definition;
                governingStart = start;
            }
        }
        if (governing == null) {
            return Producer.NONE;
        }
        Producer producer = assignedProducer(governing.getLeaf(), state);
        if (producer == Producer.NONE) {
            return Producer.NONE;
        }
        Tree block = straightLineBlock(governing);
        if (block == null || !isAncestor(block, mutation)) {
            return Producer.NONE;
        }
        for (TreePath assignment : assignments) {
            for (TreePath path = assignment.getParentPath(); path != null; path = path.getParentPath()) {
                if (isLoop(path.getLeaf()) && isAncestor(path.getLeaf(), mutation)) {
                    return Producer.NONE;
                }
            }
        }
        return producer;
    }

    /** Classifies the value a declaration or an assignment gives the local. */
    private Producer assignedProducer(Tree definition, VisitorState state) {
        ExpressionTree value = null;
        if (definition instanceof VariableTree declaration) {
            value = declaration.getInitializer();
        } else if (definition instanceof AssignmentTree assignment) {
            value = assignment.getExpression();
        }
        if (value != null && ASTHelpers.stripParentheses(value) instanceof MethodInvocationTree invocation) {
            return classify(invocation, state);
        }
        return Producer.NONE;
    }

    /**
     * The block a definition is a plain statement of, or null when anything other than statement
     * wrapping lies between the definition and its nearest block.
     */
    private static Tree straightLineBlock(TreePath definition) {
        for (TreePath path = definition.getParentPath(); path != null; path = path.getParentPath()) {
            Tree node = path.getLeaf();
            if (node instanceof BlockTree) {
                return node;
            }
            if (!(node instanceof ExpressionStatementTree
                    || node instanceof ParenthesizedTree
                    || node instanceof LabeledStatementTree)) {
                return null;
            }
        }
        return null;
    }

    private static boolean isLoop(Tree node) {
        return node instanceof ForLoopTree
                || node instanceof EnhancedForLoopTree
                || node instanceof WhileLoopTree
                || node instanceof DoWhileLoopTree;
    }

    private static boolean isAncestor(Tree candidate, TreePath of) {
        for (TreePath path = of; path != null; path = path.getParentPath()) {
            if (path.getLeaf() == candidate) {
                return true;
            }
        }
        return false;
    }
}
