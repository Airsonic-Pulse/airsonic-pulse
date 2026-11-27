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

import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

/**
 * Compilation tests for {@link ImmutableListMutation}: the three positive shapes from the #302
 * spike (at-site, cross-method via the annotation, library paged result) plus the producer
 * variants, and the negative cases the checker must not flag.
 */
public class ImmutableListMutationTest {

    private final CompilationTestHelper helper =
            CompilationTestHelper.newInstance(ImmutableListMutation.class, getClass());

    @Test
    public void flagsAtSiteMutationOfStreamToList() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.List;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    List<String> shape() {",
                        "        List<String> list = Stream.of(\"x\").toList();",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        list.add(\"y\");",
                        "        return list;",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsChainedMutationOfListOf() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.List;",
                        "class Test {",
                        "    void shape() {",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        List.of(\"x\").add(\"y\");",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsMutationOfAnnotatedProducerResult() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.List;",
                        "import org.airsonic.player.build.UnmodifiableResult;",
                        "class Test {",
                        "    @UnmodifiableResult",
                        "    List<String> producer() {",
                        "        return List.copyOf(List.of(\"x\"));",
                        "    }",
                        "    void caller() {",
                        "        List<String> songs = producer();",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        songs.remove(\"x\");",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsMutationOfPagedContent() {
        this.helper
                .addSourceLines("Slice.java",
                        "package org.springframework.data.domain;",
                        "import java.util.List;",
                        "public interface Slice<T> {",
                        "    List<T> getContent();",
                        "}")
                .addSourceLines("Page.java",
                        "package org.springframework.data.domain;",
                        "public interface Page<T> extends Slice<T> {",
                        "}")
                .addSourceLines("Test.java",
                        "import java.util.List;",
                        "import org.springframework.data.domain.Page;",
                        "class Test {",
                        "    void caller(Page<String> page) {",
                        "        List<String> files = page.getContent();",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        files.removeIf(\"x\"::equals);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsCollectToUnmodifiableList() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.List;",
                        "import java.util.stream.Collectors;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = Stream.of(\"x\").collect(Collectors.toUnmodifiableList());",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        list.clear();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsSizeChangeOnArraysAsList() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.Arrays;",
                        "import java.util.List;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = Arrays.asList(\"x\", \"y\");",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        list.remove(\"x\");",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsClearOnUnmodifiableList() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.ArrayList;",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = Collections.unmodifiableList(new ArrayList<>());",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        list.clear();",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void ignoresMutableCopy() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.ArrayList;",
                        "import java.util.List;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = new ArrayList<>(Stream.of(\"x\").toList());",
                        "        list.add(\"y\");",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void ignoresCollectorsToList() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.List;",
                        "import java.util.stream.Collectors;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = Stream.of(\"x\").collect(Collectors.toList());",
                        "        list.add(\"y\");",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void ignoresLocalReassignedBeforeMutation() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.ArrayList;",
                        "import java.util.List;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = List.of(\"x\");",
                        "        list = new ArrayList<>(list);",
                        "        list.add(\"y\");",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void ignoresSetOnArraysAsList() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.Arrays;",
                        "import java.util.List;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = Arrays.asList(\"x\", \"y\");",
                        "        list.set(0, \"z\");",
                        "        list.sort(null);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void ignoresUnannotatedMethodResult() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.ArrayList;",
                        "import java.util.List;",
                        "class Test {",
                        "    List<String> producer() {",
                        "        return new ArrayList<>();",
                        "    }",
                        "    void caller() {",
                        "        List<String> songs = producer();",
                        "        songs.remove(\"x\");",
                        "    }",
                        "}")
                .doTest();
    }

    // ---- static java.util.Collections helpers: the list is an argument, not the receiver (#380)

    @Test
    public void flagsCollectionsShuffleOnStreamToList() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = Stream.of(\"x\", \"y\").toList();",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.shuffle(list);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsCollectionsSortOnStreamToList() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.Collections;",
                        "import java.util.Comparator;",
                        "import java.util.List;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = Stream.of(\"x\", \"y\").toList();",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.sort(list);",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.sort(list, Comparator.reverseOrder());",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsCollectionsReverseOnStreamToList() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = Stream.of(\"x\", \"y\").toList();",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.reverse(list);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsCollectionsSwapOnStreamToList() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = Stream.of(\"x\", \"y\").toList();",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.swap(list, 0, 1);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsRemainingCollectionsElementMutators() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "import java.util.Random;",
                        "class Test {",
                        "    void shape(List<String> source) {",
                        "        List<String> list = List.of(\"x\", \"y\");",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.shuffle(list, new Random());",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.rotate(list, 1);",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.fill(list, \"z\");",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.replaceAll(list, \"x\", \"z\");",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.copy(list, source);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsCollectionsShuffleOnAnnotatedProducerResult() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "import org.airsonic.player.build.UnmodifiableResult;",
                        "class Test {",
                        "    @UnmodifiableResult",
                        "    private List<String> producer() {",
                        "        return List.copyOf(List.of(\"x\", \"y\"));",
                        "    }",
                        "    void caller() {",
                        "        List<String> songs = producer();",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.shuffle(songs);",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.shuffle(producer());",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsCollectionsAddAllOnListOf() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = List.of(\"x\");",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.addAll(list, \"y\", \"z\");",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsCollectionsAddAllOnArraysAsList() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.Arrays;",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = Arrays.asList(\"x\", \"y\");",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.addAll(list, \"z\");",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void ignoresCollectionsShuffleOnMutableCopy() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.ArrayList;",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = new ArrayList<>(Stream.of(\"x\", \"y\").toList());",
                        "        Collections.shuffle(list);",
                        "        Collections.shuffle(new ArrayList<>(List.of(\"x\", \"y\")));",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void ignoresCollectionsElementMutatorsOnArraysAsList() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.Arrays;",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = Arrays.asList(\"x\", \"y\");",
                        "        Collections.shuffle(list);",
                        "        Collections.sort(list);",
                        "        Collections.reverse(list);",
                        "        Collections.swap(list, 0, 1);",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void ignoresReadOnlyCollectionsStatics() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "class Test {",
                        "    String shape() {",
                        "        List<String> list = List.of(\"x\", \"y\");",
                        "        List<String> view = Collections.unmodifiableList(list);",
                        "        int frequency = Collections.frequency(list, \"x\");",
                        "        return Collections.max(list) + view.size() + frequency;",
                        "    }",
                        "}")
                .doTest();
    }

    // ---- reassigned locals: the last assignment before the mutation governs (#380)

    @Test
    public void flagsShuffleBeforeLaterReassignment() {
        // the #414 shape: the reassignment comes after the mutation
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "import org.airsonic.player.build.UnmodifiableResult;",
                        "class Test {",
                        "    @UnmodifiableResult",
                        "    private List<String> filter(List<String> files) {",
                        "        return files.stream().filter(file -> !file.isEmpty()).toList();",
                        "    }",
                        "    List<String> caller(List<String> files, int count) {",
                        "        List<String> songs = filter(files);",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        Collections.shuffle(songs);",
                        "        songs = songs.subList(0, Math.min(count, songs.size()));",
                        "        return songs;",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsInstanceMutationBeforeLaterReassignment() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.ArrayList;",
                        "import java.util.List;",
                        "import java.util.stream.Stream;",
                        "class Test {",
                        "    List<String> shape() {",
                        "        List<String> list = Stream.of(\"x\", \"y\").toList();",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        list.removeIf(String::isEmpty);",
                        "        list = new ArrayList<>(list);",
                        "        return list;",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void flagsMutationAfterReassignmentToImmutable() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.ArrayList;",
                        "import java.util.List;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = new ArrayList<>();",
                        "        list.add(\"x\");",
                        "        list = List.copyOf(list);",
                        "        // BUG: Diagnostic contains: ImmutableListMutation",
                        "        list.add(\"y\");",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void ignoresCollectionsShuffleAfterReassignmentToMutableCopy() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.ArrayList;",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "class Test {",
                        "    void shape() {",
                        "        List<String> list = List.of(\"x\", \"y\");",
                        "        list = new ArrayList<>(list);",
                        "        Collections.shuffle(list);",
                        "        list.add(\"z\");",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void ignoresMutationWhenAssignmentIsInAnotherBranch() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.ArrayList;",
                        "import java.util.Collections;",
                        "import java.util.List;",
                        "class Test {",
                        "    void ifElse(boolean flag) {",
                        "        List<String> list = new ArrayList<>();",
                        "        if (flag) {",
                        "            list = List.of(\"x\", \"y\");",
                        "        } else {",
                        "            list.add(\"z\");",
                        "            Collections.shuffle(list);",
                        "        }",
                        "    }",
                        "    void afterBranch(boolean flag) {",
                        "        List<String> list = new ArrayList<>();",
                        "        if (flag) {",
                        "            list = List.of(\"x\", \"y\");",
                        "        }",
                        "        list.add(\"z\");",
                        "    }",
                        "    void tryCatch() {",
                        "        List<String> list = new ArrayList<>();",
                        "        try {",
                        "            list = List.of(\"x\", \"y\");",
                        "        } catch (RuntimeException e) {",
                        "            list.add(\"z\");",
                        "        }",
                        "    }",
                        "    void switchCase(int choice) {",
                        "        List<String> list = new ArrayList<>();",
                        "        switch (choice) {",
                        "            case 1:",
                        "                list = List.of(\"x\", \"y\");",
                        "                break;",
                        "            default:",
                        "                list.add(\"z\");",
                        "                break;",
                        "        }",
                        "    }",
                        "}")
                .doTest();
    }

    @Test
    public void ignoresMutationWhenLoopReassigns() {
        this.helper
                .addSourceLines("Test.java",
                        "import java.util.ArrayList;",
                        "import java.util.List;",
                        "class Test {",
                        "    void shape(List<List<String>> batches) {",
                        "        List<String> list = List.of();",
                        "        for (List<String> batch : batches) {",
                        "            list = new ArrayList<>(batch);",
                        "            list.add(\"x\");",
                        "        }",
                        "        List<String> carried = List.of();",
                        "        for (List<String> batch : batches) {",
                        "            if (!carried.isEmpty()) {",
                        "                carried.addAll(batch);",
                        "            }",
                        "            carried = new ArrayList<>(batch);",
                        "        }",
                        "    }",
                        "}")
                .doTest();
    }
}
