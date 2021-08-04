/*
 * Copyright (C) 2014, Andrey Loskutov <loskutov@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.beyond.jgit.ignore.internal;


import com.beyond.jgit.ignore.error.InvalidPatternException;

import java.util.regex.Pattern;

import static com.beyond.jgit.ignore.internal.Strings.convertGlob;


/**
 * Matcher built from path segments containing wildcards. This matcher converts
 * glob wildcards to Java {@link Pattern}'s.
 * <p>
 * This class is immutable and thread safe.
 */
public class WildCardMatcher extends NameMatcher {

	final Pattern p;

	WildCardMatcher(String pattern, Character pathSeparator, boolean dirOnly)
			throws InvalidPatternException {
		super(pattern, pathSeparator, dirOnly, false);
		p = convertGlob(subPattern);
	}

	/** {@inheritDoc} */
	@Override
	public boolean matches(String segment, int startIncl, int endExcl) {
		return p.matcher(segment.substring(startIncl, endExcl)).matches();
	}
}
