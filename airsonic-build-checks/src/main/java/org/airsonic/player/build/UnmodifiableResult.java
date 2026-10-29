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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated method returns a {@link java.util.List} (or collection) the caller
 * must not structurally mutate: every return path yields an unmodifiable instance, and any
 * {@code add}, {@code remove}, {@code set}, {@code clear}, {@code removeIf}, {@code sort} or
 * similar call on it throws {@link UnsupportedOperationException} at runtime.
 * <p>
 * The {@code ImmutableListMutation} error-prone check in this module treats annotated methods as
 * immutable-List producers and fails the build when a caller mutates their result (#302). Callers
 * that need a mutable list must copy first: {@code new ArrayList<>(annotatedMethod())}.
 * <p>
 * Only annotate a method when <em>every</em> return path is unmodifiable; a method with mixed
 * mutable and immutable paths must not carry this annotation.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface UnmodifiableResult {
}
