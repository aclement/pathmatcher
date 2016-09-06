/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.util;

import java.util.List;

import org.springframework.util.PathMatcherTests.TestURITemplate;

/**
 * Exercise the {@link PathMatcher}.
 * 
 * @author Andy Clement
 */
public class Sample {

	public static void main(String[] args) {
		PathMatcher matcher = new PathMatcher();
		matcher.addURITemplate(TestURITemplate.createFor("/foo/bar"));
		matcher.addURITemplate(TestURITemplate.createFor("/foo/bar/boo"));
		matcher.addURITemplate(TestURITemplate.createFor("/foo/{id}"));
		List<MatchResult> results = matcher.findAllMatches("/foo/bar");
		System.out.println(results);
		// First match is for /foo/{id}
		// Second match is for /foo/bar
		MatchResult result = results.get(0);
		System.out.println(result.getValue("id")); // should be 'bar'
	}
	
}
