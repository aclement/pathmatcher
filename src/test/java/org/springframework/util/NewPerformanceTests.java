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
import java.util.Map;

import org.springframework.util.PathMatcherTests.TestURITemplate;

/**
 * 
 * @author Andy Clement
 */
public class NewPerformanceTests {
	
	static int REPEAT=1000000;
	
	public static void main(String[] args) {
//		new NewPerformanceTests().measure5(); 
		new NewPerformanceTests().measure5a(); 
	}
	
	public void measure1() {
		PathMatcher pm = new PathMatcher();
		pm.addURITemplate(TestURITemplate.createFor("/customer/foo"));
		for (int i=0;i<10000;i++) {
			boolean b = pm.matches("/customer/foo");
			if (!b) throw new IllegalStateException();
			b = pm.matches("/foo/bar");
			if (b) throw new IllegalStateException();
			b = pm.matches("/foo");
			if (b) throw new IllegalStateException();
		}
		long stime = System.currentTimeMillis();
		for (int i=0;i<REPEAT;i++) {  
			pm.matches("/customer/foo");
			pm.matches("/foo/bar");
			pm.matches("/foo");
		}
		long etime = System.currentTimeMillis();
		System.out.println(REPEAT+" took "+(etime-stime)+"ms"); // 4412ms
	}

	public void measure2() {
		PathMatcher pm = new PathMatcher();
		pm.addURITemplate(TestURITemplate.createFor("/customer/{id}"));
		pm.addURITemplate(TestURITemplate.createFor("/customer/book/{isbn}"));
		
		// warmup
		for (int i=0;i<REPEAT;i++) {
			List<MatchResult> results = pm.findAllMatches("/customer/99");
			String s = results.get(0).getValue("id");
			if (!s.equals("99")) {
				throw new IllegalStateException();
			} 
			results = pm.findAllMatches("/customer/book/376");
			s = results.get(0).getValue("isbn");
			if (!s.equals("376")) {
				throw new IllegalStateException();
			} 
		}

		long stime = System.currentTimeMillis();
		for (int i=0;i<1000000;i++) {  
			List<MatchResult> results = pm.findAllMatches("/customer/99");
			String s = results.get(0).getValue("id");
			results = pm.findAllMatches("/customer/book/376");
			s = results.get(0).getValue("id");
		}
		long etime = System.currentTimeMillis();
		System.out.println("1 million took "+(etime-stime)+"ms"); // 4412ms
	}
	

	public void measure5() {
		PathMatcher pm = new PathMatcher();
		pm.addURITemplate(TestURITemplate.createFor("/customer/{id}"));
		pm.addURITemplate(TestURITemplate.createFor("/customer/book/{isbn}"));
		pm.addURITemplate(TestURITemplate.createFor("/customer"));
		pm.addURITemplate(TestURITemplate.createFor("/foo/{one}/*/{two}"));
		pm.addURITemplate(TestURITemplate.createFor("/{one}/**/{two}"));
		
		// warmup
		for (int i=0;i<10000;i++) {
			List<MatchResult> results = pm.findAllMatches("/customer/99");
			String s = results.get(0).getValue("id");
			if (!s.equals("99")) {
				throw new IllegalStateException();
			} 
			results = pm.findAllMatches("/customer/book/376");
			s = results.get(0).getValue("isbn");
			if (!s.equals("376")) {
				throw new IllegalStateException();
			} 
			results = pm.findAllMatches("/customer");
			if (results.size()!=1) throw new IllegalStateException();
			results = pm.findAllMatches("/foo/aaa/something/bbb");
			s = results.get(0).getValue("one");
			if (!s.equals("aaa")) 
				throw new IllegalStateException();
			s = results.get(0).getValue("two");
			if (!s.equals("bbb")) 
				throw new IllegalStateException();
			results = pm.findAllMatches("/aaa/foo/bar/bbb");
			s = results.get(0).getValue("one");
			if (!s.equals("aaa")) 
				throw new IllegalStateException();
			s = results.get(0).getValue("two");
			if (!s.equals("bbb")) 
				throw new IllegalStateException();
		}

		long stime = System.currentTimeMillis();
		for (int i=0;i<1000000;i++) {  
			List<MatchResult> results = pm.findAllMatches("/customer/99");
			String s = results.get(0).getValue("id");
			results = pm.findAllMatches("/customer/book/376");
			s = results.get(0).getValue("id");
			results = pm.findAllMatches("/customer");
			results = pm.findAllMatches("/foo/aaa/something/bbb");
			s = results.get(0).getValue("one");
			s = results.get(0).getValue("two");
			results = pm.findAllMatches("/aaa/foo/bar/bbb");
			s = results.get(0).getValue("one");
			s = results.get(0).getValue("two");
		}
		long etime = System.currentTimeMillis();
		System.out.println("1 million took "+(etime-stime)+"ms"); // 4412ms
	}

	// Different matchers
	public void measure5a() {
		PathMatcher pm1 = new PathMatcher();
		pm1.addURITemplate(TestURITemplate.createFor("/customer/{id}"));
		PathMatcher pm2 = new PathMatcher();
		pm2.addURITemplate(TestURITemplate.createFor("/customer/book/{isbn}"));
		PathMatcher pm3 = new PathMatcher();
		pm3.addURITemplate(TestURITemplate.createFor("/customer"));
		PathMatcher pm4 = new PathMatcher();
		pm4.addURITemplate(TestURITemplate.createFor("/foo/{one}/*/{two}"));
		PathMatcher pm5 = new PathMatcher();
		pm5.addURITemplate(TestURITemplate.createFor("/{one}/**/{two}"));
		
		// warmup
		for (int i=0;i<10000;i++) {
			List<MatchResult> results = pm1.findAllMatches("/customer/99");
			String s = results.get(0).getValue("id");
			if (!s.equals("99")) {
				throw new IllegalStateException();
			} 
			results = pm2.findAllMatches("/customer/book/376");
			s = results.get(0).getValue("isbn");
			if (!s.equals("376")) {
				throw new IllegalStateException();
			} 
			results = pm3.findAllMatches("/customer");
			if (results.size()!=1) throw new IllegalStateException();
			results = pm4.findAllMatches("/foo/aaa/something/bbb");
			s = results.get(0).getValue("one");
			if (!s.equals("aaa")) 
				throw new IllegalStateException();
			s = results.get(0).getValue("two");
			if (!s.equals("bbb")) 
				throw new IllegalStateException();
			results = pm5.findAllMatches("/aaa/foo/bar/bbb");
			s = results.get(0).getValue("one");
			if (!s.equals("aaa")) 
				throw new IllegalStateException();
			s = results.get(0).getValue("two");
			if (!s.equals("bbb")) 
				throw new IllegalStateException();
		}

		long stime = System.currentTimeMillis();
		for (int i=0;i<1000000;i++) {  
			List<MatchResult> results = pm1.findAllMatches("/customer/99");
			String s = results.get(0).getValue("id");
			results = pm2.findAllMatches("/customer/book/376");
			s = results.get(0).getValue("id");
			results = pm3.findAllMatches("/customer");
			results = pm4.findAllMatches("/foo/aaa/something/bbb");
			s = results.get(0).getValue("one");
			s = results.get(0).getValue("two");
			results = pm5.findAllMatches("/aaa/foo/bar/bbb");
			s = results.get(0).getValue("one");
			s = results.get(0).getValue("two");
		}
		long etime = System.currentTimeMillis();
		System.out.println("1 million took "+(etime-stime)+"ms"); // 4412ms
	}
	
//	public void measure2() {
//		AntPathMatcher apm = new AntPathMatcher();
//		String pattern = "/customer/{id}" ;
//		// warmup
//		for (int i=0;i<10000;i++) { 
//			Map<String,String> vars = apm.extractUriTemplateVariables(pattern, "/customer/99");
//			String s = vars.get("id"); 
//			if (!s.equals("99")) {
//				throw new IllegalStateException();
//			}
//		}
//	}
}
