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
}
