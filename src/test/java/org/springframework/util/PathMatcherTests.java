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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.springframework.util.MatchResult;
import org.springframework.util.PathMatcher;
import org.springframework.util.URITemplate;

/**
 * Exercise the {@link PathMatcher}.
 * 
 * @author Andy Clement
 */
public class PathMatcherTests {

	// Verify the data structures that the PathMatcher is building:
	
	@Test
	public void verifyingPatternGraph() {
		checkMatches("/foo/bar","/foo/bar");
		PathMatcher p = new PathMatcher();
		addTemplate(p,"/foo/bar");
		assertContains("/foo/bar", p.getPatterns());
	}
	
	// Verify matching behaviour
	
	@Test
	public void basicMatching() {
		checkMatches("foo","foo");
		checkMatches("/foo","/foo");
		checkMatches("/foo/","/foo/");
		checkMatches("/foo/bar","/foo/bar");
		checkMatches("/foo/bar/","/foo/bar/");
		checkMatches("/foo/bar/woo","/foo/bar/woo");
	}
	
	@Test
	public void capturing() {
		
		checkCapture("/{bla}.*", "/testing.html","bla","testing");
		
		checkCapture("{id}","99","id","99");
		checkCapture("/customer/{customerId}","/customer/78","customerId","78");
		checkCapture("/customer/{customerId}/banana","/customer/42/banana","customerId","42");
		checkCapture("{id}/{id2}","99/98","id","99","id2","98");
		checkCapture("/foo/{bar}/boo/{baz}","/foo/plum/boo/apple","bar","plum","baz","apple");
	}
	
	@Test
	public void multiCapture() {
		checkCapture("/customer/{*something}","/customer/99","something","99");
		checkCapture("/customer/{*something}","/customer/aa/bb/cc","something","aa/bb/cc");
		// TODO [verify] is this one correct?
		checkCapture("/customer/{*something}","/customer/","something","");
	}
	
	@Test
	public void questionMarks() {
		checkMatches("/f?o/bar", "/foo/bar");
		
		PathMatcher p = new PathMatcher();
		addTemplate(p,"/f?o/bar");
		addTemplate(p,"/foo/b2r");
		assertTrue(p.matches("/foo/bar"));

		p = new PathMatcher();
		addTemplate(p,"tes?");
		assertFalse(p.matches("te"));

		p = new PathMatcher();
		addTemplate(p,"tes?");
		assertFalse(p.matches("tes"));
		assertFalse(p.matches("testt"));
		assertFalse(p.matches("tsst"));
	}

	@Test
	public void noLeadingSeparator() {
		PathMatcher matcher = new PathMatcher();
		matcher.addURITemplate(TestURITemplate.createFor("foo/bar"));
		List<MatchResult> matches = matcher.findAllMatches("foo/bar");
		assertMatchCount(1, matches);
	}

	@Test
	public void wildcards() {
		PathMatcher p = new PathMatcher();
		addTemplate(p,"/f*/bar");
		assertTrue(p.matches("/foo/bar"));
	}

	@Test
	public void random() {
		PathMatcher p = new PathMatcher();
		addTemplate(p,"/foo/bar");
		addTemplate(p,"/boo/bar");
		addTemplate(p,"/foo/boo");
		addTemplate(p,"/foo/baz");
		assertEquals(4, p.getPatterns().length);
		assertContains("/foo/bar", p.getPatterns());
		assertContains("/boo/bar", p.getPatterns());
		assertContains("/foo/boo", p.getPatterns());
		assertContains("/foo/baz", p.getPatterns());
	}

	@Test
	public void matching1() {
		PathMatcher p = new PathMatcher();
		addTemplate(p,"/foo/bar");
		assertTrue(p.matches("/foo/bar"));
		assertFalse(p.matches("/foo/baz"));
		
		p = new PathMatcher();
		addTemplate(p,"/**/bar");
		assertTrue(p.matches("/foo/bar"));
		assertFalse(p.matches("/foo/baz"));
	}
	
	@Test
	public void singleMatch() {
		PathMatcher p = new PathMatcher();
		addTemplate(p,"/foo/bar");
		addTemplate(p,"/foo/b?r");
		assertMatchCount(1, p.findFirstMatch("/foo/bar"));
	}

	@Test
	public void matching2() {
		PathMatcher p = new PathMatcher();
		addTemplate(p,"/foo/bar");
		addTemplate(p,"/foo/baz");
		assertTrue(p.matches("/foo/bar"));
		assertTrue(p.matches("/foo/baz"));
		assertFalse(p.matches("/foo/bat"));
		assertFalse(p.matches("/foo/boo"));
	}

	// These are the tests from Spring Framework AntPathMatchersTests

//	static class Matcher {
//		
//		char sep = '.';
//		boolean trimTokens = false;
//
//		public boolean match(String pattern, String path) {
//			PathMatcher pm = new PathMatcher(sep,trimTokens);
//			pm.addURITemplate(new TestURITemplate(pattern));
//			return pm.matches(path);
//		}
//
//		public boolean matchStart(String pattern, String path) {
//			PathMatcher pm = new PathMatcher(sep,trimTokens);
//			pm.addURITemplate(new TestURITemplate(pattern));
//			return pm.findAllPrefixMatchesStarting(path).size()!=0;
//		}
//
//		public void setPathSeparator(char sep) {
//			this.sep = sep;
//		}
//
//		public void setTrimTokens(boolean b) {
//			this.trimTokens = b;
//		}
//
//	}
//
//	Matcher pathMatcher = new Matcher();
	
	@Test
	public void oldAntPathMatcherTests() {
		// test exact matching
		checkMatches("test", "test");
		checkMatches("/test", "/test");
		checkMatches("http://example.org", "http://example.org");
		checkNoMatch("/test.jpg", "test.jpg");
		checkNoMatch("test", "/test");
		checkNoMatch("/test", "test");

		// test matching with ?'s
		checkMatches("t?st", "test");
		checkMatches("??st", "test");
		checkMatches("tes?", "test");
		checkMatches("te??", "test");
		checkMatches("?es?", "test");
		checkNoMatch("tes?", "tes");
		checkNoMatch("tes?", "testt");
		checkNoMatch("tes?", "tsst");

		// test matching with *'s
		checkMatches("*", "test");
		checkMatches("test*", "test");
		checkMatches("test*", "testTest");
		checkMatches("test/*", "test/Test");
		checkMatches("test/*", "test/t");
		checkMatches("test/*", "test/");
		checkMatches("*test*", "AnothertestTest");
		checkMatches("*test", "Anothertest");
		checkMatches("*.*", "test.");
		checkMatches("*.*", "test.test");
		checkMatches("*.*", "test.test.test");
		checkMatches("test*aaa", "testblaaaa");
		checkNoMatch("test*", "tst");
		checkNoMatch("test*", "tsttest");
		checkNoMatch("test*", "test/");
		checkNoMatch("test*", "test/t");
		checkNoMatch("test/*", "test");
		checkNoMatch("*test*", "tsttst");
		checkNoMatch("*test", "tsttst");
		checkNoMatch("*.*", "tsttst");
		checkNoMatch("test*aaa", "test");
		checkNoMatch("test*aaa", "testblaaab");

		// test matching with ?'s and /'s
		checkMatches("/?", "/a");
		checkMatches("/?/a", "/a/a");
		checkMatches("/a/?", "/a/b");
		checkMatches("/??/a", "/aa/a");
		checkMatches("/a/??", "/a/bb");
		checkMatches("/?", "/a");

		// test matching with **'s
		checkMatches("/**/foo", "/foo");
		checkMatches("/**", "/testing/testing");
		checkMatches("/*/**", "/testing/testing");
		checkMatches("/**/*", "/testing/testing");
		checkMatches("/bla/**/bla", "/bla/testing/testing/bla");
		checkMatches("/bla/**/bla", "/bla/testing/testing/bla/bla");
		checkMatches("/**/test", "/bla/bla/test");
		checkMatches("/bla/**/**/bla", "/bla/bla/bla/bla/bla/bla");
		checkMatches("/bla*bla/test", "/blaXXXbla/test");
		checkMatches("/*bla/test", "/XXXbla/test");
		checkNoMatch("/bla*bla/test", "/blaXXXbl/test");
		checkNoMatch("/*bla/test", "XXXblab/test");
		checkNoMatch("/*bla/test", "XXXbl/test");

		checkNoMatch("/????", "/bala/bla");
		checkNoMatch("/**/*bla", "/bla/bla/bla/bbb");

		checkMatches("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing/");
		checkMatches("/*bla*/**/bla/*", "/XXXblaXXXX/testing/testing/bla/testing");
		checkMatches("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing");
		checkMatches("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing.jpg");

		checkMatches("*bla*/**/bla/**", "XXXblaXXXX/testing/testing/bla/testing/testing/");
		checkMatches("*bla*/**/bla/*", "XXXblaXXXX/testing/testing/bla/testing");
		checkMatches("*bla*/**/bla/**", "XXXblaXXXX/testing/testing/bla/testing/testing");
		checkNoMatch("*bla*/**/bla/*", "XXXblaXXXX/testing/testing/bla/testing/testing");

		checkNoMatch("/x/x/**/bla", "/x/x/x/");

		checkMatches("/foo/bar/**", "/foo/bar");

		checkMatches("", "");

		checkMatches("/{bla}.html", "/testing.html");

		checkCapture("/{bla}.*", "/testing.html","bla","testing");
	}

	@Test
	public void multipleMatches() {
		PathMatcher p = new PathMatcher();
		addTemplate(p, "/f?o/bar");
		addTemplate(p, "/foo/bar");
		assertMatchCount(2, p.findAllMatches("/foo/bar"));
	}

	@Test
	public void multipleMatches2() {
		PathMatcher p = new PathMatcher();
		addTemplate(p, "/foo/b?r");
		addTemplate(p, "/foo/b*r");
		addTemplate(p, "/foo/bar");
		assertMatchCount(3, p.findAllMatches("/foo/bar"));
	}
	
	@Test
	public void matchStart() {
		checkStartMatches("/foo/bar","/foo");
//		PathMatcher p = new PathMatcher();
//		addTemplate(p,"/foo/bar");
//		List<MatchResult> results = p.findAllPrefixMatchesStarting("/foo");
//		assertMatchCount(1,results);
//		MatchResult result = results.get(0);
//		assertEquals("/foo",result.getMatchingPath());
//		assertEquals("/foo/bar",result.getMatchingTemplate().getTemplateText());
	}
	
	@Test
	public void matchStart2() {
		PathMatcher p = new PathMatcher();
		addTemplate(p,"/**/foo");
		List<MatchResult> results = p.findAllPrefixMatchesStarting("/foo");
		assertMatchCount(1,results);
		MatchResult result = results.get(0);
		assertEquals("/foo",result.getMatchingPath());
		assertEquals("/**/foo",result.getMatchingTemplate().getTemplateText());
	}

	@Test
	public void sfwwithMatchStart() {		
		// TODO findMatchSuccesses needs dealing with across all Segment nodes
		// TODO wildcard pattern matching needs adjustment to $ anchoring for match start (otherwise it won't work for
		// match start on partial input data)
		
		// test exact matching
		checkStartMatches("test", "test");
		checkStartMatches("/test", "/test");
		checkStartNoMatch("/test.jpg", "test.jpg");
		checkStartNoMatch("test", "/test");
		checkStartNoMatch("/test", "test");

		// test matching with ?'s
		checkStartMatches("t?st", "test");
		checkStartMatches("??st", "test");
		checkStartMatches("tes?", "test");
		checkStartMatches("te??", "test");
		checkStartMatches("?es?", "test");
		checkStartNoMatch("tes?", "tes");
		checkStartNoMatch("tes?", "testt");
		checkStartNoMatch("tes?", "tsst");

		// test matching with *'s
		checkStartMatches("*", "test");
		checkStartMatches("test*", "test");
		checkStartMatches("test*", "testTest");
		checkStartMatches("test/*", "test/Test");
		checkStartMatches("test/*", "test/t");
		checkStartMatches("test/*", "test/");
		checkStartMatches("*test*", "AnothertestTest");
		checkStartMatches("*test", "Anothertest");
		checkStartMatches("*.*", "test.");
		checkStartMatches("*.*", "test.test");
		checkStartMatches("*.*", "test.test.test");
		checkStartMatches("test*aaa", "testblaaaa");
		checkStartNoMatch("test*", "tst");
		checkStartNoMatch("test*", "test/");
		checkStartNoMatch("test*", "tsttest");
		checkStartNoMatch("test*", "test/t");
		checkStartMatches("test/*", "test");
		checkStartMatches("test/t*.txt", "test");
		checkStartNoMatch("*test*", "tsttst"); 
		checkStartNoMatch("*test", "tsttst");
		checkStartNoMatch("*.*", "tsttst");
		checkStartNoMatch("test*aaa", "test");
		checkStartNoMatch("test*aaa", "testblaaab");

		// test matching with ?'s and /'s
		checkStartMatches("/?", "/a");
		checkStartMatches("/?/a", "/a/a");
		checkStartMatches("/a/?", "/a/b");
		checkStartMatches("/??/a", "/aa/a");
		checkStartMatches("/a/??", "/a/bb");
		checkStartMatches("/?", "/a");

		// test matching with **'s
		checkStartMatches("/**", "/testing/testing");
		checkStartMatches("/*/**", "/testing/testing");
		checkStartMatches("/**/*", "/testing/testing");
		checkStartMatches("test*/**", "test/");
		checkStartMatches("test*/**", "test/t");
		checkStartMatches("/bla/**/bla", "/bla/testing/testing/bla");
		checkStartMatches("/bla/**/bla", "/bla/testing/testing/bla/bla");
		checkStartMatches("/**/test", "/bla/bla/test");
		checkStartMatches("/bla/**/**/bla", "/bla/bla/bla/bla/bla/bla");
		checkStartMatches("/bla*bla/test", "/blaXXXbla/test");
		checkStartMatches("/*bla/test", "/XXXbla/test");
		checkStartNoMatch("/bla*bla/test", "/blaXXXbl/test");
		checkStartNoMatch("/*bla/test", "XXXblab/test");
		checkStartNoMatch("/*bla/test", "XXXbl/test");

		checkStartNoMatch("/????", "/bala/bla");
		checkStartMatches("/**/*bla", "/bla/bla/bla/bbb");

		checkStartMatches("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing/");
		checkStartMatches("/*bla*/**/bla/*", "/XXXblaXXXX/testing/testing/bla/testing");
		checkStartMatches("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing");
		checkStartMatches("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing.jpg");

		checkStartMatches("*bla*/**/bla/**", "XXXblaXXXX/testing/testing/bla/testing/testing/");
		checkStartMatches("*bla*/**/bla/*", "XXXblaXXXX/testing/testing/bla/testing");
		checkStartMatches("*bla*/**/bla/**", "XXXblaXXXX/testing/testing/bla/testing/testing");
		checkStartMatches("*bla*/**/bla/*", "XXXblaXXXX/testing/testing/bla/testing/testing");

		checkStartMatches("/x/x/**/bla", "/x/x/x/");

		checkStartMatches("", "");
	}

	@Test
	public void trimTokens() {
		PathMatcher pathMatcher = new PathMatcher(PathMatcher.DEFAULT_PATH_SEPARATOR,true,true);
		pathMatcher.addURITemplate(TestURITemplate.createFor("/foo/bar"));
		List<MatchResult> matches = pathMatcher.findAllMatches("/ foo / bar");
		assertMatchCount(1, matches);
		
		pathMatcher.clear();
		pathMatcher.addURITemplate(TestURITemplate.createFor("   /    foo    /   bar   "));
		matches = pathMatcher.findAllMatches(" / foo  /  bar    ");
		assertMatchCount(1, matches);

		pathMatcher.clear();
		pathMatcher.addURITemplate(TestURITemplate.createFor(" //    foo   ///   bar   "));
		matches = pathMatcher.findAllMatches(" // foo  / / /  bar    ");
		assertMatchCount(1, matches);

		pathMatcher = new PathMatcher('.',true,true);
		pathMatcher.addURITemplate(TestURITemplate.createFor("   .    foo    .   bar   "));
		matches = pathMatcher.findAllMatches(" . foo  .  bar    ");
		assertMatchCount(1, matches);
		System.out.println(matches.get(0));
	}
	
	@Test
	public void caseSensitivity() {
		// Case sensitive = true
		PathMatcher pathMatcher = new PathMatcher(PathMatcher.DEFAULT_PATH_SEPARATOR,false,true);
		pathMatcher.addURITemplate(TestURITemplate.createFor("/foo/bar"));
		List<MatchResult> matches = pathMatcher.findAllMatches("/foo/bar");
		assertMatchCount(1, matches);
		
		// Case sensitive = false (mixed case path)
		pathMatcher = new PathMatcher(PathMatcher.DEFAULT_PATH_SEPARATOR,false,false);
		pathMatcher.addURITemplate(TestURITemplate.createFor("/foo/bar"));
		matches = pathMatcher.findAllMatches("/fOo/bAr");
		assertMatchCount(1, matches);
		
		// Case sensitive = false (mixed case pattern)
		pathMatcher = new PathMatcher(PathMatcher.DEFAULT_PATH_SEPARATOR,false,false);
		pathMatcher.addURITemplate(TestURITemplate.createFor("/fOo/bAr"));
		matches = pathMatcher.findAllMatches("/foo/bar");
		assertMatchCount(1, matches);
	}
	
	@Test
	public void alternativeDelimiter() {
		try {
			separator = '.';
	
			// test exact matching
			checkMatches("test", "test");
			checkMatches(".test", ".test");
			checkNoMatch(".test/jpg", "test/jpg");
			checkNoMatch("test", ".test");
			checkNoMatch(".test", "test");
	
			// test matching with ?'s
			checkMatches("t?st", "test");
			checkMatches("??st", "test");
			checkMatches("tes?", "test");
			checkMatches("te??", "test");
			checkMatches("?es?", "test");
			checkNoMatch("tes?", "tes");
			checkNoMatch("tes?", "testt");
			checkNoMatch("tes?", "tsst");
	
			// test matching with *'s
			checkMatches("*", "test");
			checkMatches("test*", "test");
			checkMatches("test*", "testTest");
			checkMatches("*test*", "AnothertestTest");
			checkMatches("*test", "Anothertest");
			checkMatches("*/*", "test/");
			checkMatches("*/*", "test/test");
			checkMatches("*/*", "test/test/test");
			checkMatches("test*aaa", "testblaaaa");
			checkNoMatch("test*", "tst");
			checkNoMatch("test*", "tsttest");
			checkNoMatch("*test*", "tsttst");
			checkNoMatch("*test", "tsttst");
			checkNoMatch("*/*", "tsttst");
			checkNoMatch("test*aaa", "test");
			checkNoMatch("test*aaa", "testblaaab");
	
			// test matching with ?'s and .'s
			checkMatches(".?", ".a");
			checkMatches(".?.a", ".a.a");
			checkMatches(".a.?", ".a.b");
			checkMatches(".??.a", ".aa.a");
			checkMatches(".a.??", ".a.bb");
			checkMatches(".?", ".a");
	
			// test matching with **'s
			checkMatches(".**", ".testing.testing");
			checkMatches(".*.**", ".testing.testing");
			checkMatches(".**.*", ".testing.testing");
			checkMatches(".bla.**.bla", ".bla.testing.testing.bla");
			checkMatches(".bla.**.bla", ".bla.testing.testing.bla.bla");
			checkMatches(".**.test", ".bla.bla.test");
			checkMatches(".bla.**.**.bla", ".bla.bla.bla.bla.bla.bla");
			checkMatches(".bla*bla.test", ".blaXXXbla.test");
			checkMatches(".*bla.test", ".XXXbla.test");
			checkNoMatch(".bla*bla.test", ".blaXXXbl.test");
			checkNoMatch(".*bla.test", "XXXblab.test");
			checkNoMatch(".*bla.test", "XXXbl.test");
		} finally {
			separator = PathMatcher.DEFAULT_PATH_SEPARATOR;
		}
	}

	// @Test
	// public void extractPathWithinPattern() throws Exception {
	// assertEquals("",
	// pathMatcher.extractPathWithinPattern("/docs/commit.html",
	// "/docs/commit.html"));
	//
	// assertEquals("cvs/commit",
	// pathMatcher.extractPathWithinPattern("/docs/*", "/docs/cvs/commit"));
	// assertEquals("commit.html",
	// pathMatcher.extractPathWithinPattern("/docs/cvs/*.html",
	// "/docs/cvs/commit.html"));
	// assertEquals("cvs/commit",
	// pathMatcher.extractPathWithinPattern("/docs/**", "/docs/cvs/commit"));
	// assertEquals("cvs/commit.html",
	// pathMatcher.extractPathWithinPattern("/docs/**/*.html",
	// "/docs/cvs/commit.html"));
	// assertEquals("commit.html",
	// pathMatcher.extractPathWithinPattern("/docs/**/*.html",
	// "/docs/commit.html"));
	// assertEquals("commit.html",
	// pathMatcher.extractPathWithinPattern("/*.html", "/commit.html"));
	// assertEquals("docs/commit.html",
	// pathMatcher.extractPathWithinPattern("/*.html", "/docs/commit.html"));
	// assertEquals("/commit.html",
	// pathMatcher.extractPathWithinPattern("*.html", "/commit.html"));
	// assertEquals("/docs/commit.html",
	// pathMatcher.extractPathWithinPattern("*.html", "/docs/commit.html"));
	// assertEquals("/docs/commit.html",
	// pathMatcher.extractPathWithinPattern("**/*.*", "/docs/commit.html"));
	// assertEquals("/docs/commit.html",
	// pathMatcher.extractPathWithinPattern("*", "/docs/commit.html"));
	// // SPR-10515
	// assertEquals("/docs/cvs/other/commit.html",
	// pathMatcher.extractPathWithinPattern("**/commit.html",
	// "/docs/cvs/other/commit.html"));
	// assertEquals("cvs/other/commit.html",
	// pathMatcher.extractPathWithinPattern("/docs/**/commit.html",
	// "/docs/cvs/other/commit.html"));
	// assertEquals("cvs/other/commit.html",
	// pathMatcher.extractPathWithinPattern("/docs/**/**/**/**",
	// "/docs/cvs/other/commit.html"));
	//
	// assertEquals("docs/cvs/commit",
	// pathMatcher.extractPathWithinPattern("/d?cs/*", "/docs/cvs/commit"));
	// assertEquals("cvs/commit.html",
	// pathMatcher.extractPathWithinPattern("/docs/c?s/*.html",
	// "/docs/cvs/commit.html"));
	// assertEquals("docs/cvs/commit",
	// pathMatcher.extractPathWithinPattern("/d?cs/**", "/docs/cvs/commit"));
	// assertEquals("docs/cvs/commit.html",
	// pathMatcher.extractPathWithinPattern("/d?cs/**/*.html",
	// "/docs/cvs/commit.html"));
	// }
	//
	// @Test
	// public void extractUriTemplateVariables() throws Exception {
	// Map<String, String> result =
	// pathMatcher.extractUriTemplateVariables("/hotels/{hotel}", "/hotels/1");
	// assertEquals(Collections.singletonMap("hotel", "1"), result);
	//
	// result = pathMatcher.extractUriTemplateVariables("/h?tels/{hotel}",
	// "/hotels/1");
	// assertEquals(Collections.singletonMap("hotel", "1"), result);
	//
	// result =
	// pathMatcher.extractUriTemplateVariables("/hotels/{hotel}/bookings/{booking}",
	// "/hotels/1/bookings/2");
	// Map<String, String> expected = new LinkedHashMap<>();
	// expected.put("hotel", "1");
	// expected.put("booking", "2");
	// assertEquals(expected, result);
	//
	// result = pathMatcher.extractUriTemplateVariables("/**/hotels/**/{hotel}",
	// "/foo/hotels/bar/1");
	// assertEquals(Collections.singletonMap("hotel", "1"), result);
	//
	// result = pathMatcher.extractUriTemplateVariables("/{page}.html",
	// "/42.html");
	// assertEquals(Collections.singletonMap("page", "42"), result);
	//
	// result = pathMatcher.extractUriTemplateVariables("/{page}.*",
	// "/42.html");
	// assertEquals(Collections.singletonMap("page", "42"), result);
	//
	// result = pathMatcher.extractUriTemplateVariables("/A-{B}-C", "/A-b-C");
	// assertEquals(Collections.singletonMap("B", "b"), result);
	//
	// result = pathMatcher.extractUriTemplateVariables("/{name}.{extension}",
	// "/test.html");
	// expected = new LinkedHashMap<>();
	// expected.put("name", "test");
	// expected.put("extension", "html");
	// assertEquals(expected, result);
	// }
	//
	// @Test
	// public void extractUriTemplateVariablesRegex() {
	// Map<String, String> result = pathMatcher
	// .extractUriTemplateVariables("{symbolicName:[\\w\\.]+}-{version:[\\w\\.]+}.jar",
	// "com.example-1.0.0.jar");
	// assertEquals("com.example", result.get("symbolicName"));
	// assertEquals("1.0.0", result.get("version"));
	//
	//
	// result =
	// pathMatcher.extractUriTemplateVariables("{symbolicName:[\\w\\.]+}-sources-{version:[\\w\\.]+}.jar",
	// "com.example-sources-1.0.0.jar");
	// assertEquals("com.example", result.get("symbolicName"));
	// assertEquals("1.0.0", result.get("version"));
	// }
	//
	// /**
	// * SPR-7787
	// */
	// @Test
	// public void extractUriTemplateVarsRegexQualifiers() {
	// Map<String, String> result = pathMatcher.extractUriTemplateVariables(
	// "{symbolicName:[\\p{L}\\.]+}-sources-{version:[\\p{N}\\.]+}.jar",
	// "com.example-sources-1.0.0.jar");
	// assertEquals("com.example", result.get("symbolicName"));
	// assertEquals("1.0.0", result.get("version"));
	//
	// result = pathMatcher.extractUriTemplateVariables(
	// "{symbolicName:[\\w\\.]+}-sources-{version:[\\d\\.]+}-{year:\\d{4}}{month:\\d{2}}{day:\\d{2}}.jar",
	// "com.example-sources-1.0.0-20100220.jar");
	// assertEquals("com.example", result.get("symbolicName"));
	// assertEquals("1.0.0", result.get("version"));
	// assertEquals("2010", result.get("year"));
	// assertEquals("02", result.get("month"));
	// assertEquals("20", result.get("day"));
	//
	// result = pathMatcher.extractUriTemplateVariables(
	// "{symbolicName:[\\p{L}\\.]+}-sources-{version:[\\p{N}\\.\\{\\}]+}.jar",
	// "com.example-sources-1.0.0.{12}.jar");
	// assertEquals("com.example", result.get("symbolicName"));
	// assertEquals("1.0.0.{12}", result.get("version"));
	// }
	//
	// /**
	// * SPR-8455
	// */
	// @Test
	// public void extractUriTemplateVarsRegexCapturingGroups() {
	// exception.expect(IllegalArgumentException.class);
	// exception.expectMessage(containsString("The number of capturing groups in
	// the pattern"));
	// pathMatcher.extractUriTemplateVariables("/web/{id:foo(bar)?}",
	// "/web/foobar");
	// }
	//
	// @Test
	// public void combine() {
	// assertEquals("", pathMatcher.combine(null, null));
	// assertEquals("/hotels", pathMatcher.combine("/hotels", null));
	// assertEquals("/hotels", pathMatcher.combine(null, "/hotels"));
	// assertEquals("/hotels/booking", pathMatcher.combine("/hotels/*",
	// "booking"));
	// assertEquals("/hotels/booking", pathMatcher.combine("/hotels/*",
	// "/booking"));
	// assertEquals("/hotels/**/booking", pathMatcher.combine("/hotels/**",
	// "booking"));
	// assertEquals("/hotels/**/booking", pathMatcher.combine("/hotels/**",
	// "/booking"));
	// assertEquals("/hotels/booking", pathMatcher.combine("/hotels",
	// "/booking"));
	// assertEquals("/hotels/booking", pathMatcher.combine("/hotels",
	// "booking"));
	// assertEquals("/hotels/booking", pathMatcher.combine("/hotels/",
	// "booking"));
	// assertEquals("/hotels/{hotel}", pathMatcher.combine("/hotels/*",
	// "{hotel}"));
	// assertEquals("/hotels/**/{hotel}", pathMatcher.combine("/hotels/**",
	// "{hotel}"));
	// assertEquals("/hotels/{hotel}", pathMatcher.combine("/hotels",
	// "{hotel}"));
	// assertEquals("/hotels/{hotel}.*", pathMatcher.combine("/hotels",
	// "{hotel}.*"));
	// assertEquals("/hotels/*/booking/{booking}",
	// pathMatcher.combine("/hotels/*/booking", "{booking}"));
	// assertEquals("/hotel.html", pathMatcher.combine("/*.html",
	// "/hotel.html"));
	// assertEquals("/hotel.html", pathMatcher.combine("/*.html", "/hotel"));
	// assertEquals("/hotel.html", pathMatcher.combine("/*.html", "/hotel.*"));
	// assertEquals("/*.html", pathMatcher.combine("/**", "/*.html"));
	// assertEquals("/*.html", pathMatcher.combine("/*", "/*.html"));
	// assertEquals("/*.html", pathMatcher.combine("/*.*", "/*.html"));
	// assertEquals("/{foo}/bar", pathMatcher.combine("/{foo}", "/bar")); //
	// SPR-8858
	// assertEquals("/user/user", pathMatcher.combine("/user", "/user")); //
	// SPR-7970
	// assertEquals("/{foo:.*[^0-9].*}/edit/",
	// pathMatcher.combine("/{foo:.*[^0-9].*}", "/edit/")); // SPR-10062
	// assertEquals("/1.0/foo/test", pathMatcher.combine("/1.0", "/foo/test"));
	// // SPR-10554
	// assertEquals("/hotel", pathMatcher.combine("/", "/hotel")); // SPR-12975
	// assertEquals("/hotel/booking", pathMatcher.combine("/hotel/",
	// "/booking")); // SPR-12975
	// }
	//
	// @Test
	// public void combineWithTwoFileExtensionPatterns() {
	// exception.expect(IllegalArgumentException.class);
	// pathMatcher.combine("/*.html", "/*.txt");
	// }
	//
	// @Test
	// public void patternComparator() {
	// Comparator<String> comparator =
	// pathMatcher.getPatternComparator("/hotels/new");
	//
	// assertEquals(0, comparator.compare(null, null));
	// assertEquals(1, comparator.compare(null, "/hotels/new"));
	// assertEquals(-1, comparator.compare("/hotels/new", null));
	//
	// assertEquals(0, comparator.compare("/hotels/new", "/hotels/new"));
	//
	// assertEquals(-1, comparator.compare("/hotels/new", "/hotels/*"));
	// assertEquals(1, comparator.compare("/hotels/*", "/hotels/new"));
	// assertEquals(0, comparator.compare("/hotels/*", "/hotels/*"));
	//
	// assertEquals(-1, comparator.compare("/hotels/new", "/hotels/{hotel}"));
	// assertEquals(1, comparator.compare("/hotels/{hotel}", "/hotels/new"));
	// assertEquals(0, comparator.compare("/hotels/{hotel}",
	// "/hotels/{hotel}"));
	// assertEquals(-1, comparator.compare("/hotels/{hotel}/booking",
	// "/hotels/{hotel}/bookings/{booking}"));
	// assertEquals(1, comparator.compare("/hotels/{hotel}/bookings/{booking}",
	// "/hotels/{hotel}/booking"));
	//
	// // SPR-10550
	// assertEquals(-1,
	// comparator.compare("/hotels/{hotel}/bookings/{booking}/cutomers/{customer}",
	// "/**"));
	// assertEquals(1, comparator.compare("/**",
	// "/hotels/{hotel}/bookings/{booking}/cutomers/{customer}"));
	// assertEquals(0, comparator.compare("/**", "/**"));
	//
	// assertEquals(-1, comparator.compare("/hotels/{hotel}", "/hotels/*"));
	// assertEquals(1, comparator.compare("/hotels/*", "/hotels/{hotel}"));
	//
	// assertEquals(-1, comparator.compare("/hotels/*", "/hotels/*/**"));
	// assertEquals(1, comparator.compare("/hotels/*/**", "/hotels/*"));
	//
	// assertEquals(-1, comparator.compare("/hotels/new", "/hotels/new.*"));
	// assertEquals(2, comparator.compare("/hotels/{hotel}",
	// "/hotels/{hotel}.*"));
	//
	// // SPR-6741
	// assertEquals(-1,
	// comparator.compare("/hotels/{hotel}/bookings/{booking}/cutomers/{customer}",
	// "/hotels/**"));
	// assertEquals(1, comparator.compare("/hotels/**",
	// "/hotels/{hotel}/bookings/{booking}/cutomers/{customer}"));
	// assertEquals(1, comparator.compare("/hotels/foo/bar/**",
	// "/hotels/{hotel}"));
	// assertEquals(-1, comparator.compare("/hotels/{hotel}",
	// "/hotels/foo/bar/**"));
	// assertEquals(2, comparator.compare("/hotels/**/bookings/**",
	// "/hotels/**"));
	// assertEquals(-2, comparator.compare("/hotels/**",
	// "/hotels/**/bookings/**"));
	//
	// // SPR-8683
	// assertEquals(1, comparator.compare("/**", "/hotels/{hotel}"));
	//
	// // longer is better
	// assertEquals(1, comparator.compare("/hotels", "/hotels2"));
	//
	// // SPR-13139
	// assertEquals(-1, comparator.compare("*", "*/**"));
	// assertEquals(1, comparator.compare("*/**", "*"));
	// }
	//
	// @Test
	// public void patternComparatorSort() {
	// Comparator<String> comparator =
	// pathMatcher.getPatternComparator("/hotels/new");
	// List<String> paths = new ArrayList<>(3);
	//
	// paths.add(null);
	// paths.add("/hotels/new");
	// Collections.sort(paths, comparator);
	// assertEquals("/hotels/new", paths.get(0));
	// assertNull(paths.get(1));
	// paths.clear();
	//
	// paths.add("/hotels/new");
	// paths.add(null);
	// Collections.sort(paths, comparator);
	// assertEquals("/hotels/new", paths.get(0));
	// assertNull(paths.get(1));
	// paths.clear();
	//
	// paths.add("/hotels/*");
	// paths.add("/hotels/new");
	// Collections.sort(paths, comparator);
	// assertEquals("/hotels/new", paths.get(0));
	// assertEquals("/hotels/*", paths.get(1));
	// paths.clear();
	//
	// paths.add("/hotels/new");
	// paths.add("/hotels/*");
	// Collections.sort(paths, comparator);
	// assertEquals("/hotels/new", paths.get(0));
	// assertEquals("/hotels/*", paths.get(1));
	// paths.clear();
	//
	// paths.add("/hotels/**");
	// paths.add("/hotels/*");
	// Collections.sort(paths, comparator);
	// assertEquals("/hotels/*", paths.get(0));
	// assertEquals("/hotels/**", paths.get(1));
	// paths.clear();
	//
	// paths.add("/hotels/*");
	// paths.add("/hotels/**");
	// Collections.sort(paths, comparator);
	// assertEquals("/hotels/*", paths.get(0));
	// assertEquals("/hotels/**", paths.get(1));
	// paths.clear();
	//
	// paths.add("/hotels/{hotel}");
	// paths.add("/hotels/new");
	// Collections.sort(paths, comparator);
	// assertEquals("/hotels/new", paths.get(0));
	// assertEquals("/hotels/{hotel}", paths.get(1));
	// paths.clear();
	//
	// paths.add("/hotels/new");
	// paths.add("/hotels/{hotel}");
	// Collections.sort(paths, comparator);
	// assertEquals("/hotels/new", paths.get(0));
	// assertEquals("/hotels/{hotel}", paths.get(1));
	// paths.clear();
	//
	// paths.add("/hotels/*");
	// paths.add("/hotels/{hotel}");
	// paths.add("/hotels/new");
	// Collections.sort(paths, comparator);
	// assertEquals("/hotels/new", paths.get(0));
	// assertEquals("/hotels/{hotel}", paths.get(1));
	// assertEquals("/hotels/*", paths.get(2));
	// paths.clear();
	//
	// paths.add("/hotels/ne*");
	// paths.add("/hotels/n*");
	// Collections.shuffle(paths);
	// Collections.sort(paths, comparator);
	// assertEquals("/hotels/ne*", paths.get(0));
	// assertEquals("/hotels/n*", paths.get(1));
	// paths.clear();
	//
	// comparator = pathMatcher.getPatternComparator("/hotels/new.html");
	// paths.add("/hotels/new.*");
	// paths.add("/hotels/{hotel}");
	// Collections.shuffle(paths);
	// Collections.sort(paths, comparator);
	// assertEquals("/hotels/new.*", paths.get(0));
	// assertEquals("/hotels/{hotel}", paths.get(1));
	// paths.clear();
	//
	// comparator =
	// pathMatcher.getPatternComparator("/web/endUser/action/login.html");
	// paths.add("/**/login.*");
	// paths.add("/**/endUser/action/login.*");
	// Collections.sort(paths, comparator);
	// assertEquals("/**/endUser/action/login.*", paths.get(0));
	// assertEquals("/**/login.*", paths.get(1));
	// paths.clear();
	// }
	//
	// @Test // SPR-8687
	// public void trimTokensOff() {
	// pathMatcher.setTrimTokens(false);
	//
	// checkMatches("/group/{groupName}/members",
	// "/group/sales/members"));
	// checkMatches("/group/{groupName}/members", "/group/
	// sales/members"));
	// checkNoMatch("/group/{groupName}/members", "/Group/
	// Sales/Members"));
	// }
	//
	// @Test // SPR-13286
	// public void caseInsensitive() {
	// pathMatcher.setCaseSensitive(false);
	//
	// checkMatches("/group/{groupName}/members",
	// "/group/sales/members"));
	// checkMatches("/group/{groupName}/members",
	// "/Group/Sales/Members"));
	// checkMatches("/Group/{groupName}/Members",
	// "/group/Sales/members"));
	// }
	//
	// @Test
	// public void defaultCacheSetting() {
	// match();
	// assertTrue(pathMatcher.stringMatcherCache.size() > 20);
	//
	// for (int i = 0; i < 65536; i++) {
	// pathMatcher.match("test" + i, "test");
	// }
	// // Cache turned off because it went beyond the threshold
	// assertTrue(pathMatcher.stringMatcherCache.isEmpty());
	// }
	//
	// @Test
	// public void cachePatternsSetToTrue() {
	// pathMatcher.setCachePatterns(true);
	// match();
	// assertTrue(pathMatcher.stringMatcherCache.size() > 20);
	//
	// for (int i = 0; i < 65536; i++) {
	// pathMatcher.match("test" + i, "test" + i);
	// }
	// // Cache keeps being alive due to the explicit cache setting
	// assertTrue(pathMatcher.stringMatcherCache.size() > 65536);
	// }
	//
	// @Test
	// public void
	// preventCreatingStringMatchersIfPathDoesNotStartsWithPatternPrefix() {
	// pathMatcher.setCachePatterns(true);
	// assertEquals(0, pathMatcher.stringMatcherCache.size());
	//
	// pathMatcher.match("test?", "test");
	// assertEquals(1, pathMatcher.stringMatcherCache.size());
	//
	// pathMatcher.match("test?", "best");
	// pathMatcher.match("test/*", "view/test.jpg");
	// pathMatcher.match("test/**/test.jpg", "view/test.jpg");
	// pathMatcher.match("test/{name}.jpg", "view/test.jpg");
	// assertEquals(1, pathMatcher.stringMatcherCache.size());
	// }
	//
	// @Test
	// public void
	// creatingStringMatchersIfPatternPrefixCannotDetermineIfPathMatch() {
	// pathMatcher.setCachePatterns(true);
	// assertEquals(0, pathMatcher.stringMatcherCache.size());
	//
	// pathMatcher.match("test", "testian");
	// pathMatcher.match("test?", "testFf");
	// pathMatcher.match("test/*", "test/dir/name.jpg");
	// pathMatcher.match("test/{name}.jpg", "test/lorem.jpg");
	// pathMatcher.match("bla/**/test.jpg", "bla/test.jpg");
	// pathMatcher.match("**/{name}.jpg", "test/lorem.jpg");
	// pathMatcher.match("/**/{name}.jpg", "/test/lorem.jpg");
	// pathMatcher.match("/*/dir/{name}.jpg", "/*/dir/lorem.jpg");
	//
	// assertEquals(7, pathMatcher.stringMatcherCache.size());
	// }
	//
	// @Test
	// public void cachePatternsSetToFalse() {
	// pathMatcher.setCachePatterns(false);
	// match();
	// assertTrue(pathMatcher.stringMatcherCache.isEmpty());
	// }
	//
	// @Test
	// public void extensionMappingWithDotPathSeparator() {
	// pathMatcher.setPathSeparator(".");
	// assertEquals("Extension mapping should be disabled with \".\" as path
	// separator",
	// "/*.html.hotel.*", pathMatcher.combine("/*.html", "hotel.*"));
	// }
	//
	// }

	// ---

	private void assertContains(String uriTemplateToFind, String[] uriTemplatesToSearch) {
		StringBuilder s = new StringBuilder();
		for (String uriTemplate : uriTemplatesToSearch) {
			s.append(uriTemplate).append("\n");
			if (uriTemplate.equals(uriTemplateToFind)) {
				return;
			}
		}
		fail("Did not find expected URI template '" + uriTemplateToFind + "' in candidates:\n" + s.toString());
	}

//	private void checkMatches(String pattern, String... inputs) {
//		Experiments matcher = new Experiments(pattern);
//		for (String input : inputs) {
//			assertTrue("Expected pattern '" + pattern + "' to match on '" + input + "'", matcher.match(input));
//		}
//	}
//
//	private void checkNoMatch(String pattern, String... inputs) {
//		Experiments matcher = new Experiments(pattern);
//		for (String input : inputs) {
//			assertFalse("Expected pattern '" + pattern + "' *not* to match on '" + input + "'", matcher.match(input));
//		}
//	}

	private void addTemplate(PathMatcher pathMatcher, String templateString) {
		pathMatcher.addURITemplate(new TestURITemplate(templateString));
	}

	static class TestURITemplate implements URITemplate {

		private String templateString;

		public TestURITemplate(String templateString) {
			this.templateString = templateString;
		}

		public static URITemplate createFor(String templateString) {
			return new TestURITemplate(templateString);
		}

		@Override
		public String getTemplateText() {
			return this.templateString;
		}

	}

	private void assertMatchCount(int expectedMatchCount, List<MatchResult> matches) {
		if (expectedMatchCount!=matches.size()) {
			fail("Expected "+expectedMatchCount+" matches but found "+matches.size()+"\n"+matches);
		}
	}

	private char separator = PathMatcher.DEFAULT_PATH_SEPARATOR;
	
	private void checkMatches(String uriTemplate, String path) {
		PathMatcher pathMatcher = (separator==PathMatcher.DEFAULT_PATH_SEPARATOR?new PathMatcher():new PathMatcher(separator));
		addTemplate(pathMatcher,uriTemplate);
		List<MatchResult> matchResults = pathMatcher.findAllMatches(path);
		assertMatchCount(1, matchResults);
		MatchResult matchResult = matchResults.get(0);
		assertEquals(path,matchResult.getMatchingPath());
		assertEquals(uriTemplate,matchResult.getMatchingTemplate().getTemplateText());
	}

	private void checkStartMatches(String uriTemplate, String path) {
		PathMatcher pathMatcher = new PathMatcher();
		addTemplate(pathMatcher,uriTemplate);
		List<MatchResult> matchResults = pathMatcher.findAllPrefixMatchesStarting(path);
		if (matchResults.size()!=1 && matchResults.size()!=2) {
			// TODO currently matchStart when /** is in use will include dups in the results.
			// If /** is no longer supported then life is simpler. If it is supported need
			// to handle it better.
			fail();
		}
		MatchResult matchResult = matchResults.get(0);
		assertEquals(path,matchResult.getMatchingPath());
		assertEquals(uriTemplate,matchResult.getMatchingTemplate().getTemplateText());
		assertEquals(path,matchResult.getMatchingPath());
	}
	
	private void checkNoMatch(String uriTemplate, String path) {
		PathMatcher pathMatcher = new PathMatcher();
		addTemplate(pathMatcher,uriTemplate);
		List<MatchResult> matchResults = pathMatcher.findAllMatches(path);
		assertMatchCount(0, matchResults);
	}

	private void checkStartNoMatch(String uriTemplate, String path) {
		PathMatcher pathMatcher = new PathMatcher();
		addTemplate(pathMatcher,uriTemplate);
		List<MatchResult> matchResults = pathMatcher.findAllPrefixMatchesStarting(path);
		assertMatchCount(0, matchResults);
	}

	private void checkCapture(String uriTemplate, String path, String... keyValues) {
		PathMatcher pathMatcher = new PathMatcher();
		addTemplate(pathMatcher,uriTemplate);
		List<MatchResult> matchResults = pathMatcher.findAllMatches(path);
		assertMatchCount(1, matchResults);
		MatchResult matchResult = matchResults.get(0);
		assertEquals(path,matchResult.getMatchingPath());
		assertEquals(uriTemplate,matchResult.getMatchingTemplate().getTemplateText());
		Map<String,String> expectedKeyValues = new HashMap<>();
		if (keyValues!=null) {
			for (int i=0;i<keyValues.length;i+=2) {
				expectedKeyValues.put(keyValues[i], keyValues[i+1]);
			}
		}
		Map<String,String> capturedVariables = matchResult.getCapturedVariables();
		for (Map.Entry<String,String> me: expectedKeyValues.entrySet()) {
			String value = capturedVariables.get(me.getKey());
			if (value == null) {
				fail("Did not find key '"+me.getKey()+"' in captured variables: "+capturedVariables);
			}
			if (!value.equals(me.getValue())) {
				fail("Expected value '"+me.getValue()+"' for key '"+me.getKey()+"' but was '"+value+"'");
			}
		}
	}

}
