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
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.instanceMethod;
import static com.google.errorprone.matchers.Matchers.staticMethod;

/**
 * Flags structural mutation of a List value known to be unmodifiable (#302, the #296/#351 crash
 * class): the mutated value is either the direct result of an immutable-List producer, or a local
 * variable initialised from one and never reassigned in the enclosing method.
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
        boolean sizeChanging = COLLECTION_MUTATOR.matches(tree, state);
        if (!sizeChanging && !LIST_ONLY_MUTATOR.matches(tree, state)) {
            return Description.NO_MATCH;
        }
        ExpressionTree receiver = ASTHelpers.getReceiver(tree);
        if (receiver == null) {
            return Description.NO_MATCH;
        }
        Producer producer = producerOf(receiver, state);
        if (producer == Producer.NONE || (producer == Producer.FIXED_SIZE && !sizeChanging)) {
            return Description.NO_MATCH;
        }
        return describeMatch(tree);
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
     * Classifies a local variable receiver: it counts as a producer result only when its
     * declaration initialiser is a producer invocation and the variable is never reassigned
     * anywhere in the enclosing method.
     */
    private Producer localProducer(IdentifierTree ident, VisitorState state) {
        Element sym = ASTHelpers.getSymbol(ident);
        if (sym == null || sym.getKind() != ElementKind.LOCAL_VARIABLE) {
            return Producer.NONE;
        }
        MethodTree enclosingMethod = state.findEnclosing(MethodTree.class);
        if (enclosingMethod == null) {
            return Producer.NONE;
        }
        Producer[] fromInitializer = {Producer.NONE};
        boolean[] reassigned = {false};
        enclosingMethod.accept(new TreeScanner<Void, Void>() {
            @Override
            public Void visitVariable(VariableTree node, Void unused) {
                if (sym.equals(ASTHelpers.getSymbol(node))
                        && node.getInitializer() instanceof MethodInvocationTree init) {
                    fromInitializer[0] = classify(init, state);
                }
                return super.visitVariable(node, unused);
            }

            @Override
            public Void visitAssignment(AssignmentTree node, Void unused) {
                Tree variable = node.getVariable();
                if (variable instanceof IdentifierTree id && sym.equals(ASTHelpers.getSymbol(id))) {
                    reassigned[0] = true;
                }
                return super.visitAssignment(node, unused);
            }
        }, null);
        return reassigned[0] ? Producer.NONE : fromInitializer[0];
    }
}
