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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO capture in match start?
// TODO [question] Is there any need/benefit to sorting the tree if they always ask for all of them?
// TODO [general] How about a tree 'verifier' function that checks it is well formed
// TODO [question] What about two templates that are the same, is that a bug? What if they differ in 'subtle' ways (one captures, one doesn't)
// TODO [optimization] Worth caching hashcodes of Segment subtypes?
// TODO [parsing] What about bad templates? Is double slash bad?
// TODO [parsing] Support escaping in URI templates?
// TODO [parsing] Enforce captures are complete before next separator (plus various other error checks)
// TODO [question] Should two matchsuccesssegments with templates containing the same text be the same (hashcode/equals)?
// TODO make sure no-one uses the candidate.length ?
// TODO give me a u, give me a n, give me a i, give me a have-you-tried-this-with-unicode? (paths/patterns)
// TODO when matching on trimmed patterns/paths - the 'matchLength' may be odd if used with the original input data - problem?
// TODO [question] surely trimtokens is not the default case, so we are ok to be slower for that?
// TODO case sensitivity

/**
 * A PathMatcher is populated with a number of URI templates and then will match
 * any specified string (assumed to be a URL) against those templates. Typical
 * usage:
 * 
 * <ol>
 * <li>Create it:<br>
 * PathMatcher matcher = new PathMatcher();
 * <li>Populate it:<br>
 * <tt>matcher.addURITemplate("/f?o/bar");</tt><br>
 * <tt>matcher.addURITemplate("/foo/b2r");</tt>
 * <li>Use that populated matcher over and over:<br>
 * <tt>matcher.matches("/foo/bar"));</tt>
 * </ol>
 * 
 * Supported syntax in the URI templates:
 * <ul>
 * <li>/foo/bar</li>
 * <li>
 * </ul>
 *
 * <b>Internals:</b> The path matcher will pre-process any URI templates passed
 * in to construct a tree made up of their segments. For example if the paths
 * /foo/bar and /foo/baz are added to the path matcher the tree will look like
 * this:
 * 
 * <pre>
 * <code>
 * /
 *  foo
 *     /
 *      bar
 *      baz
 * </code>
 * </pre>
 * 
 * In this way during the matching process we only have to match the foo element
 * once, regardless of what comes after it. There are multiple roots to the tree
 * and they are grouped by the number of separators they contain. By quickly
 * scanning the incoming URL to match to discover how many separators there are
 * it is possible to 'fast match' and effectively only then process patterns
 * that might match this URL.
 * 
 * @author Andy Clement
 */
public class PathMatcher {

	protected final static boolean DEBUG = false;
	
	private static final List<MatchResult> NO_MATCHES = Collections.emptyList();
	
	public static final char DEFAULT_PATH_SEPARATOR = '/';
	
	/**
	 * patterns is keyed by a number of separators and returns all tree roots that
	 * contain patterns with that number of separators in.
	 */
	private Map<Integer, Segment[]> patternsMap = new TreeMap<>();

	/**
	 * The highest key value set in the patterns map, it is a sparse structure so
	 * not all from 0 to maxKey may have been set.
	 */
	private int maxKey = 0;
	
	/**
	 * Holds patterns that may match a variable number of segments.
	 */
	private List<VariableSegmentRoot> patternsVariableSeparators = new LinkedList<>();

	private char separator = DEFAULT_PATH_SEPARATOR;
	
	private boolean trimTokens = false;
	
	private boolean caseSensitive = true;

	public PathMatcher() {
	}
	
	public PathMatcher(char separator) {
		this.separator = separator;
	}

	public PathMatcher(char separator, boolean trimTokens) {
		this.separator = separator;
		this.trimTokens = trimTokens;
	}

	public PathMatcher(char separator, boolean trimTokens, boolean caseSensitive) {
		this.separator = separator;
		this.trimTokens = trimTokens;
		this.caseSensitive = caseSensitive;
	}

	public void addURITemplate(URITemplate template) {
		new URITemplateProcessor().process(template);
	}

	/**
	 * Process a URITemplate by parsing the text into a segment chain then
	 * recording that chain in the appropriate patterns data structure. If
	 * it overlaps with an already learned pattern it will be merged to
	 * create a tree.
	 */
	class URITemplateProcessor {

		private URITemplate templatex;
		private char[] templateText;
		private int len, pos, start;
		private boolean qmark, wildcard, capturing, multiSegmentMatching;
		private List<Segment> segments = new ArrayList<Segment>();
		private int separatorCount;

		// TODO verify nothing after {*foo}
		// TODO [1] tidy up this mess
		private void process(URITemplate template) {
			this.templatex = template;
			this.templateText = template.getTemplateText().toCharArray();
			len = this.templateText.length;
			if (trimTokens) {
				if (DEBUG) 
					System.out.println("Pre  trim: '"+new String(templateText)+"' length="+templateText.length);
				int c = 0; // the position in the new data
				int i = 0; // the position in the original data
				// Skip over leading whitespace
				while (i<len && templateText[i]==' ') i++;
				// Find separators and for each one remove whitespace around it
				while (i<len) {
					char ch = templateText[i];
					if (ch==separator) {
						// scan backwards over preceding whitespace
						while (c>0 && templateText[c-1]==' ') c--;
						templateText[c++] = ch;
						i++;
						// Scan over whitespace after the separator
						while (i<len && templateText[i]==' ') i++;
					} else {
						templateText[c++] = caseSensitive?Character.toLowerCase(ch):ch;
						i++;
					}
				}
				// Scan backwards over whitespace
				while (c>0 && templateText[c-1]==' ') c--;
				if (DEBUG) 
					System.out.println("Post trim: '"+new String(templateText,0,c)+"' length: "+c);
				len = c;
			} else if (!caseSensitive) {
				// Convert the whole path to lower case (patterns will have already been converted)
				for (int i=0;i<len;i++) {
					templateText[i] = Character.toLowerCase(templateText[i]);
				}
			}
			start = -1; // Tracks the beginning of the current element being
						// processed within the template
			qmark = false; // Is there a ? in the element being processed
			wildcard = false; // Is there a * in the element being processed
			capturing = false; // Is there a {...} in the element being
								// processed
			multiSegmentMatching = false; // Does this template use elements that match multiple segments: /** or /{*foo}
			pos = 0;
			parseToSegmentChain();
		}

		private void parseToSegmentChain() {
			for (pos = 0; pos < len; pos++) {
				char ch = templateText[pos];
				if (ch == separator) {
					if (start != -1) {
						pushSegment();
					}
					if (peekSlashStarStar()) {
						segments.add(new SeparatorStarStarSegment(pos));
						multiSegmentMatching = true;
						pos += 2;
					} else {
						segments.add(new SeparatorSegment(pos));
						separatorCount++;
					}
				} else {
					if (ch == '?') {
						qmark = true;
					} else if (ch == '*') {
						wildcard = true;
					} else if (ch == '{') {
						capturing = true;
					}
					if (start == -1) {
						start = pos;
					}
				}
			}
			// Anything left over? (Could be if it doesn't end with a separator)
			if (start != -1) {
				pushSegment();
			}
			segments.add(new MatchSuccessSegment(templateText.length, templatex));

			// Join them in a chain
			for (int s = segments.size() - 2; s >= 0; s--) {
				Segment segment = segments.get(s);
				segment.nextSegments = new Segment[] { segments.get(s + 1) };
			}
			// Some of these backpointers will be modified when the new chain is
			// inserted into the tree
			for (int s = segments.size() - 1; s > 0; s--) {
				Segment segment = segments.get(s);
				segment.previousSegment = segments.get(s - 1);
			}
			printChainHelper(System.out, segments.get(0), 0);
			Segment segmentToInsert = segments.get(0);
			if (!multiSegmentMatching) {
				recordPattern(segmentToInsert, separatorCount, true);
			} else {
				// TODO [1] should attempt merge into existing before adding new, it may be possible
				patternsVariableSeparators.add(new VariableSegmentRoot(segmentToInsert, separatorCount));
			}
		}

		private boolean recordPattern(Segment segment, int separatorCount, boolean insertIfNewRoot) {
			Segment[] roots = patternsMap.get(separatorCount);
			if (roots == null) {
				if (!insertIfNewRoot) {
					return false;
				} else {
					roots = new Segment[] { segment };
					patternsMap.put(separatorCount, roots);
					if (separatorCount>maxKey) {
						maxKey = separatorCount;
					}
					return true;
				}
			}
			// There are existing roots, let's see if this merges with any of
			// them
			for (Segment root : roots) {
				if (root.equals(segment)) {
					// Candidate
					merge(root, segment);
					return true;
				}
			}
			if (insertIfNewRoot) {
				// It is a new root
				Segment[] newRoots = new Segment[roots.length + 1];
				System.arraycopy(roots, 0, newRoots, 1, roots.length);
				newRoots[0] = segment;
				patternsMap.put(separatorCount, newRoots);
				return true;
			}
			return false;
		}

		private void pushSegment() {
			String segmentText = new String(templateText,start,pos-start);
			if (wildcard || capturing) {
				if (capturing && (start==0 || templateText[start-1]==separator) && templateText[pos-1]=='}' && (pos==len || templateText[pos]==separator)) {
					// It is a full capture /{...}/
					// Is it a {*foobar} or just a {foobar}
					if (templateText[start+1]=='*') {
						multiSegmentMatching=true;
						// TODO could reduce all this String creation by working with subsequences throughout - but strings are easier and this code is part of the upfront cost, not the ongoing matching cost.
						segments.add(new CapturingMultiTextSegment(start, segmentText));
					} else {
						segments.add(new CapturingTextSegment(start, segmentText));
					}
				} else {
					// TODO need smarter capture handling in the general case here:
					segments.add(new WildcardedTextSegment(start, segmentText));
				}
			} else {
				if (qmark) {
					segments.add(new QuestionMarkedTextSegment(start, segmentText));
				} else {
					segments.add(new LiteralSegment(start, segmentText));
				}
			}
			start = -1;
			qmark = false;
			wildcard = false;
			capturing = false;
		}

		private boolean peekSlashStarStar() {
			// We know template[pos] == separator
			return ((pos + 2) < len && templateText[pos + 1] == '*' && templateText[pos + 2] == '*');
		}

	}

	public void dumpMatcherState(PrintStream stream) {
		if (!DEBUG) return;
		for (Map.Entry<Integer, Segment[]> entry : patternsMap.entrySet()) {
			stream.println("Separators: #" + entry.getKey());
			for (Segment root : entry.getValue()) {
				printChainHelper(stream, root, 0);
			}
		}
		stream.println("Patterns length = #" + patternsMap.size());
	}

	void printChainHelper(PrintStream stream, Segment segment, int indent) {
		for (int i = 0; i < indent; i++) {
			stream.print(" ");
		}
		if (indent != 0) {
			stream.print("-> ");
		}
		stream.println(segment + "   (<- " + segment.previousSegment + ")");
		if (segment.nextSegments != null) {
			for (int i = 0; i < segment.nextSegments.length; i++) {
				printChainHelper(stream, segment.nextSegments[i], indent + 2);
			}
		}
	}

	/**
	 * Target is a potential place where we might slot in the segment toMerge.
	 */
	private void merge(Segment target, Segment toMerge) {
		if (!target.equals(toMerge)) {
			throw new IllegalStateException("?? " + target + " != " + toMerge);
		}
		// We know that target matches toMerge, so now we need to see what else
		// matches down this route
		if (toMerge.nextSegments != null && toMerge.nextSegments.length > 1) {
			throw new IllegalStateException("Inconceivable!");
		}
		Segment nextMergeableSegment = toMerge.nextSegments[0];
		Segment[] nextTargetSegments = target.nextSegments;
		if (nextTargetSegments == null) {
			// Can't match anything there, insert this as a new leaf.
			nextTargetSegments = new Segment[] { nextMergeableSegment };
			nextMergeableSegment.previousSegment = target;
		} else {
			boolean insert = true;
			for (Segment nextTargetSegment : nextTargetSegments) {
				if (nextTargetSegment.equals(nextMergeableSegment)) {
					// Match, pass it on
					merge(nextTargetSegment, nextMergeableSegment);
					insert = false;
					break;
				}
			}
			if (insert) {
				// Not found amongst existing next links, insert
				Segment[] newNext = new Segment[nextTargetSegments.length + 1];
				System.arraycopy(nextTargetSegments, 0, newNext, 1, nextTargetSegments.length);
				newNext[0] = nextMergeableSegment;
				nextMergeableSegment.previousSegment = target;
				target.nextSegments = newNext;
			}
		}

		// if (target.equals(toMerge)) {
		// // it does match here, no need to include toMerge but we should try
		// to include toMerges targets.
		// if (toMerge.nextSegments!=null && toMerge.nextSegments.length>1) {
		// throw new IllegalStateException("Inconceivable!");
		// }
		// if (target.nextSegments!=null) {
		// // At this stage of merge toMerge.nextSegments should be at most 1
		// entry
		// for (Segment segment: target.nextSegments) {
		// if (merge(segment,toMerge.nextSegments[0])) {
		// // Job done
		// break;
		// }
		// }
		// }
		// return true;
		// } else {
		// // Doesn't match, we need to patch things up.
		// Segment previous = target.previousSegment;
		// if (previous == null) {
		// return false;
		// }
		// if (previous.nextSegments==null) {
		// previous.nextSegments = new Segment[]{toMerge};
		// } else {
		// Segment[] newNext = new Segment[previous.nextSegments.length+1];
		// System.arraycopy(previous.nextSegments, 0, newNext, 1,
		// previous.nextSegments.length);
		// newNext[0] = toMerge;
		// previous.nextSegments = newNext;
		// }
		// return true;
		// }
	}

	public String[] getPatterns() {
		List<String> patternList = new ArrayList<String>();
		for (Segment[] chain : patternsMap.values()) {
			if (chain != null) {
				for (Segment segment : chain) {
					collect(segment, patternList);
				}
			}
		}
		return patternList.toArray(new String[patternList.size()]);
	}

	private void collect(Segment segment, List<String> patterns) {
		if (segment instanceof MatchSuccessSegment) {
			patterns.add(((MatchSuccessSegment) segment).template.getTemplateText());
		} else {
			Segment[] nextSegments = segment.nextSegments;
			for (Segment nextSegment : nextSegments) {
				collect(nextSegment, patterns);
			}
		}
	}



	// TODO delete this or keep it? Change name of incoming
	public boolean matches(String incoming) {
		MatchingContext matchingContext = new MatchingContext(incoming,false);
		dumpMatcherState(System.out);
		// Try exact ones
		Segment[] candidates = patternsMap.get(matchingContext.separatorCount);
		if (candidates != null) {
			for (Segment candidate : candidates) {
				candidate.matches(0, 0, matchingContext);
				if (matchingContext.hasResults()) {
					return true;
				}
			}
		}
		// Try variable length ones
		for (VariableSegmentRoot vsr : patternsVariableSeparators) {
			if (vsr.getMinimumSegmentCount()<=matchingContext.separatorCount) {
				// TODO verify we only test the right number of candidates when there are multiple
				vsr.getRoot().matches(0, 0, matchingContext);
				if (matchingContext.hasResults()) {
					return true;
				}
			}
		}
		return false;
	}

	// TODO [1] name
	public List<MatchResult> findAllPrefixMatchesStarting(String pathToMatch) {
		MatchingContext matchingContext = new MatchingContext(pathToMatch, true, true);
		dumpMatcherState(System.out);

		for (int i = matchingContext.separatorCount; i<=maxKey; i++) {
			Segment[] candidates = patternsMap.get(i);
			if (candidates != null) {
				for (Segment candidate : candidates) {
					// TODO push sepnum/candidateindex into mc?
					candidate.matches(0, 0, matchingContext); 
					if (matchingContext.hasResults() && !matchingContext.findAllMatches) {
						return matchingContext.getMatchResults();
					}
				}
			}
		}
		// Now must try the variable ones (/** /{*foobar})
		// Try variable length ones
		for (VariableSegmentRoot vsr : patternsVariableSeparators) {
			if (vsr.getMinimumSegmentCount()<=matchingContext.separatorCount) {
				// TODO verify we only test the right number of candidates when there are multiple
				vsr.getRoot().matches(0, 0, matchingContext);
				if (matchingContext.hasResults() && !matchingContext.findAllMatches) {
					return matchingContext.getMatchResults();
				}
			}
		}
		// 2: Those variable ones
		// Segment[] candidates = findCandidatesToMatch();
		// if (candidates != null) {
		// for (Segment candidate : candidates) {
		// candidate.matches(0, 0, matchingContext); // TODO push
		// sepnum/candidateindex into mc?
		// }
		// }
		return matchingContext.getMatchResults();
	}

	/**
	 * Return all the matches, not just the first one.
	 */
	public List<MatchResult> findAllMatches(String pathToMatch) {
		MatchingContext matchingContext = new MatchingContext(pathToMatch,true);
		dumpMatcherState(System.out);
		Segment[] candidates = patternsMap.get(matchingContext.separatorCount);
		if (candidates != null) {
			for (Segment candidate : candidates) {
				// TODO push sepnum/candidateindex into mc?
				candidate.matches(0, 0, matchingContext); 
				if (matchingContext.hasResults() && !matchingContext.findAllMatches) {
					return matchingContext.getMatchResults();
				}
			}
		}
		for (VariableSegmentRoot vsr : patternsVariableSeparators) {
			if (vsr.getMinimumSegmentCount()<=matchingContext.separatorCount) {
				vsr.getRoot().matches(0, 0, matchingContext);
				if (matchingContext.hasResults() && !matchingContext.findAllMatches) {
					return matchingContext.getMatchResults();
				}
			}
		}
		return matchingContext.getMatchResults();
	}

	/**
	 * Return all the matches, not just the first one.
	 */
	public List<MatchResult> findFirstMatch(String pathToMatch) {
		MatchingContext matchingContext = new MatchingContext(pathToMatch,false);
		dumpMatcherState(System.out);
		Segment[] candidates = patternsMap.get(matchingContext.separatorCount);
		if (candidates != null) {
			for (Segment candidate : candidates) {
				candidate.matches(0, 0, matchingContext);
				if (matchingContext.hasResults()) {
					return matchingContext.getMatchResults();
				}
			}
		}
		return null;
	}

	class MatchingContext {

		int[] separatorPositions;
		int separatorCount = 0;
		char[] candidate;
		String candidateText;
		int candidateLength;
		int currentTagIndex = 0;
		
		// Configuration of the behaviour for matching during a specific walk of
		// candidates

		// Find either the first or all matches  TODO change to findFirstMatch?
		private boolean findAllMatches = true;
		
		// Find templates that at least match as much path as has been supplied
		private boolean matchStart = false;

		private List<MatchResult> matchResults;

		public MatchingContext(String pathToMatch, boolean findAllMatches) {
			this.findAllMatches = findAllMatches;
			prepare(pathToMatch);
		}

		public MatchingContext(String pathToMatch, boolean findAllMatches, boolean matchStart) {
			this.findAllMatches = findAllMatches;
			this.matchStart = matchStart;
			prepare(pathToMatch);
		}

		public void addMatchResult(URITemplate template, String matchingCandidate) {
			if (matchResults == null) {
				matchResults = new LinkedList<>();
			}
			matchResults.add(new MatchResult(template, matchingCandidate));
		}

		// TODO [1] name of this is hopeless
		public boolean isOnlyMatchStartCheck() {
			return matchStart;
		}

		public boolean hasResults() {
			// TODO reduce cost of check? Or could eliminate with custom match method that always new to look for all results.
			return this.matchResults != null && this.matchResults.size() != 0;
		}

		public List<MatchResult> getMatchResults() {
			if (this.matchResults == null) {
				return NO_MATCHES;
			} else {
				return this.matchResults;
			}
		}
		
		public void tag() {
			currentTagIndex = matchResults.size();
		}

		public void set(String key, String value) {
			matchResults.get(matchResults.size()-1).set(key,value);
		}
		

		/**
		 * Analyze the input path that will be used for matching in order to respect
		 * settings like token trimming or case sensitivity, and compute the separators
		 * to enable jumping around the input data during the match.
		 */
		private void prepare(String pathToMatch) {
			// At most every character would be a separator
			separatorPositions = new int[pathToMatch.length()+1];
			separatorCount = 0;
			candidateText = pathToMatch;
			candidate = pathToMatch.toCharArray();
			candidateLength = this.candidate.length;
			if (trimTokens) {
				candidateLength = trim();
			} else if (!caseSensitive) {
				// Convert the whole path to lower case (patterns will have already been converted)
				// Also make a not of separator positions
				for (int i=0, max = candidate.length;i<max;i++) {
					char ch = candidate[i];
					if (ch== separator) {
						separatorPositions[separatorCount++] = i;						
						candidate[i] = separator;
					} else {
						candidate[i] = Character.toLowerCase(ch);
					}
				}
			} else {
				for (int i = 0, max = pathToMatch.length(); i < max; i++) {
					if (pathToMatch.charAt(i) == separator) {
						separatorPositions[separatorCount++] = i;
					}
				}
			}
			// Final sentinel entry marks end of the path (separatorCount deliberately not increased)
			separatorPositions[separatorCount] = pathToMatch.length();
		}
		// TODO is it overkill to avoid creating the new char array here? Are there going to be more problems down the line because you need to remember to use candidateLength and not candidate.length
		/**
		 * Trim whitespace around path elements. The char array 'candidate' contains the
		 * path that may contain whitespace. Rather than create a new array, instead we modify it in place
		 * and record the new length. If the matching is case insensitive input characters are also converted
		 * to lower case.
		 * 
		 * @return the length after trimming (may be the same as the input data length)
		 */
		private int trim() {
			if (DEBUG) 
				System.out.println("Pre  trim: '"+new String(candidate)+"' length="+candidate.length);
			int c = 0; // the position in the new data
			int i = 0; // the position in the original data
			// Skip over leading whitespace
			while (i<candidateLength && candidate[i]==' ') i++;
			// Find separators and for each one remove whitespace around it
			while (i<candidateLength) {
				char ch = candidate[i];
				if (ch==separator) {
					separatorPositions[separatorCount++] = i;					
					// scan backwards over preceding whitespace
					while (c>0 && candidate[c-1]==' ') c--;
					candidate[c++] = ch;
					i++;
					// Scan over whitespace after the separator
					while (i<candidateLength && candidate[i]==' ') i++;
				} else {
					candidate[c++] = caseSensitive?Character.toLowerCase(ch):ch;
					i++;
				}
			}
			// Scan backwards over whitespace
			while (c>0 && candidate[c-1]==' ') c--;
			if (DEBUG) 
				System.out.println("Post trim: '"+new String(candidate,0,c)+"' length: "+c);
			return c;
		}
		

	}
	
	

	public void clear() {
		patternsMap.clear();
	}

	static class SubSequence implements CharSequence {

		private char[] chars;
		private int start, end;

		SubSequence(char[] chars, int start, int end) {
			this.chars = chars;
			this.start = start;
			this.end = end;
		}

		@Override
		public int length() {
			return end - start;
		}

		@Override
		public char charAt(int index) {
			return chars[start + index];
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			return new SubSequence(chars, this.start + start, this.start + end);
		}
		
		public String toString() {
			return new String(chars,start,end-start);
		}

	}

	class VariableSegmentRoot {

		private Segment root;
		private int minimumSegments;

		public VariableSegmentRoot(Segment segmentToInsert, int sepCollectorPtr) {
			root = segmentToInsert;
			minimumSegments = sepCollectorPtr;
		}

		public Segment getRoot() {
			return root;
		}

		public int getMinimumSegmentCount() {
			return minimumSegments;
		}

	}

	// The tree node implementation classes:

	abstract class Segment {


		int pos;
		Segment[] nextSegments;
		Segment previousSegment;

		Segment(int pos) {
			this.pos = pos;
		}

		public abstract boolean matches(int candidatePos, int sepNum, MatchingContext matchingContext);

		protected final void findMatchSuccesses(Segment segment, Consumer<URITemplate> fn) {
			if (segment instanceof MatchSuccessSegment) {
				fn.accept(((MatchSuccessSegment)segment).template);
			} else {
				if (nextSegments != null) {
					for (Segment nextSegment: segment.nextSegments) {
						findMatchSuccesses(nextSegment,fn);
					}
				}
			}
		}
		
		protected void printMatchStateDebug(MatchingContext matchingContext, int index) {
			StringBuilder s = new StringBuilder();
			s.append("Match attempt on " + toString() + "\n");
			s.append("  " + new String(matchingContext.candidate,0,matchingContext.candidateLength)).append("\n  ");
			for (int i = 0; i < index; i++) {
				s.append(' ');
			}
			s.append('^');
			System.out.println(s.toString());
		}

	}

	class SeparatorSegment extends Segment {

		SeparatorSegment(int pos) {
			super(pos);
		}

		public String toString() {
			return "Separator(" + separator + ")";
		}

		@Override
		public boolean matches(int candidateIndex, int sn, MatchingContext matchingContext) {
			if (DEBUG)
				printMatchStateDebug(matchingContext, candidateIndex);
			boolean matched = false;
			if (candidateIndex < matchingContext.candidateLength) {
				if (matchingContext.candidate[candidateIndex] == separator) {
					for (Segment nextSegment : nextSegments) {
						boolean match = nextSegment.matches(candidateIndex + 1, sn + 1, matchingContext);
						if (match) {
							matched = true;
							if (!matchingContext.findAllMatches) {
								return true;
							}
						}
					}
				}				
			} else {
				if (matchingContext.isOnlyMatchStartCheck()) {
					// Chase down match success segments below this point, they all match
					// TODO don't need to pass in candidateText
					findMatchSuccesses(this,(template) -> { matchingContext.addMatchResult(template, matchingContext.candidateText); });
					matched = true;
				}
			}
			return matched;
		}

		public int hashCode() {
			return 17 + this.pos * 37;
		}

		public boolean equals(Object o) {
			return (o instanceof SeparatorSegment) && ((SeparatorSegment) o).pos == this.pos;
		}

	}

	class LiteralSegment extends Segment {

		private char[] text;
		private int len;

		public LiteralSegment(int pos, String literalText) {
			super(pos);
			this.text = literalText.toCharArray();
			this.len = this.text.length;
		}

		@Override
		public boolean matches(int candidateIndex, int sn, MatchingContext matchingContext) {
			if (DEBUG)
				printMatchStateDebug(matchingContext, candidateIndex);
			// TODO what about 'matchStart' when a partial literal is supplied? Or is that not allowed
			if ((candidateIndex + text.length) > matchingContext.candidateLength) {
				return false;
			}
			for (int i = 0; i < len; i++) {
				if (matchingContext.candidate[candidateIndex++] != text[i]) {
					return false;
				}
			}
			boolean somethingMatched = false;
			for (Segment nextSegment : nextSegments) {
				boolean match = nextSegment.matches(candidateIndex, sn, matchingContext);
				if (match) {
					somethingMatched = true;
					if (!matchingContext.findAllMatches) {
						return true;
					}
				}
			}
			return somethingMatched;
		}

		public String toString() {
			return "Literal(" + new String(text) + ")";
		}

		public int hashCode() {
			return 17 + (37 * this.pos + text.hashCode()) * 37;
		}

		public boolean equals(Object o) {
			return (o instanceof LiteralSegment) && ((LiteralSegment) o).pos == this.pos
					&& ((LiteralSegment) o).text.equals(this.text);
		}

	}

	/**
	 * Segment that contains regular text plus one or more '?' characters
	 */
	class QuestionMarkedTextSegment extends Segment {

		private char[] text;
		private int len;

		public QuestionMarkedTextSegment(int pos, String literalText) {
			super(pos);
			this.text = literalText.toCharArray();
			this.len = this.text.length;
		}

		@Override
		public boolean matches(int candidateIndex, int sn, MatchingContext matchingContext) {
			if (DEBUG)
				printMatchStateDebug(matchingContext,candidateIndex);
			// TODO [1] a 'fast match' rejection could compare the position of
			// the
			// next separator compared to the current pointer and the length of
			// this string.
			// need to work out when to STOP
			if (matchingContext.candidateLength < (candidateIndex + len)) {
				// There isn't enough data to match this pattern!
				return false;
			}
			for (int i = 0; i < len; i++) {
				if (text[i] != '?' && matchingContext.candidate[candidateIndex] != text[i]) {
					return false;
				}
				candidateIndex++;
			}
			// TODO [1] text this if, why is it here, why not just delegate? is
			// it a 'run out of data' problem? is there more data?
			if (matchingContext.separatorPositions[sn] > candidateIndex) {
				// There is more data that did not match the pattern
				return false;
			}
			boolean somethingMatched = false;
			for (Segment nextSegment : nextSegments) {
				boolean match = nextSegment.matches(candidateIndex, sn, matchingContext);
				if (match) {
					somethingMatched = true;
					if (!matchingContext.findAllMatches) {
						return true;
					}
				}
			}
			return somethingMatched;
		}

		public String toString() {
			return "QuestionMarkedText(" + new String(text) + ")";
		}

		public int hashCode() {
			return 19 + (37 * this.pos + text.hashCode()) * 37;
		}

		public boolean equals(Object o) {
			return (o instanceof QuestionMarkedTextSegment) && ((QuestionMarkedTextSegment) o).pos == this.pos
					&& ((QuestionMarkedTextSegment) o).text.equals(this.text);
		}

	}
	
	class CapturingTextSegment extends Segment {

		private String key;
		private Pattern constraintPattern;
		
		/**
		 * @param pos
		 * @param captureDescriptor is of the form {AAAAA[:pattern]}
		 */
		CapturingTextSegment(int pos, String captureDescriptor) {
			super(pos);
			int colon = captureDescriptor.indexOf(":");
			if (colon == -1) {
				// no constraint
				key = captureDescriptor.substring(1, captureDescriptor.length()-1);
			} else {
				key = captureDescriptor.substring(1, colon);
				// TODO do I need to prefix ^ and suffix $ ?
				constraintPattern = Pattern.compile(captureDescriptor.substring(colon+1, captureDescriptor.length()-1));
			}
		}

		@Override
		public boolean matches(int candidateIndex, int sn, MatchingContext matchingContext) {
			if (candidateIndex == matchingContext.separatorPositions[sn]) {
				return false;
			}
			if (constraintPattern!=null) {
				// TODO what if not enough data for the next line?
				Matcher m = constraintPattern.matcher(new SubSequence(matchingContext.candidate,candidateIndex,matchingContext.separatorPositions[sn]));
				if (!m.matches()) {
					return false;
				}
			}			
			boolean somethingMatched = false;
			for (Segment nextSegment : nextSegments) {
				boolean match = nextSegment.matches(matchingContext.separatorPositions[sn], sn, matchingContext);
				if (match) {
					somethingMatched = true;
					if (!matchingContext.findAllMatches) {
						return true;
					}
				}
			}
			if (somethingMatched && !matchingContext.matchStart) {
				// Need to do capture - TODO less object creation please
				matchingContext.set(key,matchingContext.candidateText.substring(candidateIndex, matchingContext.separatorPositions[sn]));
			}
			return somethingMatched;
		}

		public String toString() {
			return "CapturingText({" + new String(key) + (constraintPattern==null?"":":"+constraintPattern.pattern())+"})";
		}

		public int hashCode() {
			return 19 + ((this.pos * 37 + key.hashCode()) * 37 + (constraintPattern==null?0:constraintPattern.hashCode()))*37 ;
		}

		public boolean equals(Object o) {
			if (!(o instanceof CapturingTextSegment)) {
				return false;
			}
			CapturingTextSegment that = (CapturingTextSegment)o;
			return that.pos == this.pos && 
					that.key.equals(this.key) && 
					this.constraintPattern==null?that.constraintPattern==null:this.constraintPattern.pattern().equals(that.constraintPattern.pattern());
		}
	}
	
	class CapturingMultiTextSegment extends Segment {

		private String key;
		
		CapturingMultiTextSegment(int pos, String captureDescriptor) {
			super(pos);
			key = captureDescriptor.substring(2, captureDescriptor.length()-1);
		}

		@Override
		public boolean matches(int candidateIndex, int sn, MatchingContext matchingContext) {
			if (DEBUG) printMatchStateDebug(matchingContext,candidateIndex);
			boolean somethingMatched = false;
			for (Segment nextSegment : nextSegments) {
				boolean b = nextSegment.matches(candidateIndex, sn, matchingContext);
				if (b) {
					somethingMatched = true;
					if (!matchingContext.findAllMatches) {
						return true;
					}
					// TODO should break here? why go looking for more?
					if (!matchingContext.matchStart) {
						// Going to assume that {*foo} is always the 'last' element in a chain.
						// This means the 'value' is the rest-of-the-data
						matchingContext.set(key,matchingContext.candidateText.substring(candidateIndex));
					}
					break; // TODO why go looking for more?
				} else {
					for (int i = sn + 1; i <= matchingContext.separatorCount; i++) {
						System.out.println("/** skipping to next candidate, #separator=" + i + " pos=" + matchingContext.separatorPositions[i]);
						b = nextSegment.matches(matchingContext.separatorPositions[i], i, matchingContext);
						if (b) {
							somethingMatched = true;
							if (!matchingContext.findAllMatches) {
								return true;
							}
							if (!matchingContext.matchStart) {
								// Going to assume that {*foo} is always the 'last' element in a chain.
								// This means the 'value' is the rest-of-the-data
								matchingContext.set(key,matchingContext.candidateText.substring(candidateIndex));
//								matchingContext.set(key,candidateText.substring(candidateIndex, sepCollector[i]));
							}
							// TODO should break here? why go looking for more?
							break;
						}
					}
				}
			}
//			if (somethingMatched && !matchingContext.matchStart) {
//				// Need to do capture - TODO less object creation please
//				matchingContext.set(key,candidateText.substring(candidateIndex, sepCollector[sn]));
//			}
			return somethingMatched;
			
//			boolean somethingMatched = false;
//			for (Segment nextSegment : nextSegments) {
//				boolean match = nextSegment.matches(sepCollector[sn], sn, matchingContext);
//				if (match) {
//					somethingMatched = true;
//					if (!matchingContext.findAllMatches) {
//						return true;
//					}
//				}
//			}
//			if (somethingMatched && !matchingContext.matchStart) {
//				// Need to do capture - TODO less object creation please
//				matchingContext.set(key,candidateText.substring(candidateIndex, sepCollector[sn]));
//			}
//			return somethingMatched;
		}

		public String toString() {
			return "CapturingMultiText({*" + new String(key) + "})";
		}

		public int hashCode() {
			return 19 + (this.pos * 37 + key.hashCode()) * 37 ;
		}

		public boolean equals(Object o) {
			if (!(o instanceof CapturingMultiTextSegment)) {
				return false;
			}
			CapturingMultiTextSegment that = (CapturingMultiTextSegment)o;
			return that.pos == this.pos && 
					that.key.equals(this.key);
		}
	}

	class WildcardedTextSegment extends Segment {

		private char[] text;

		// +? is 'reluctant' one or more times 
		// (?: is 'non capturing group' 
		// '?' or '*' or '{'  ( (?: {[^/]+?} | [^/{}] | \\[{}])+?)  '}'"
		// TODO [1] 
		// TODO push somewhere that it can stay a constant?
		private  final Pattern GLOB_PATTERN = Pattern.compile("\\?|\\*|\\{((?:\\{[^/]+?\\}|[^/{}]|\\\\[{}])+?)\\}");

		private final String DEFAULT_VARIABLE_PATTERN = "(.*)";

		private final List<String> variableNames = new LinkedList<>();
		private Pattern pattern;

		public WildcardedTextSegment(int pos, String text) {
			super(pos);
			this.text = text.toCharArray();
			buildPattern(text);
			String patternText = text.replaceAll("\\{[^\\}]*\\}", "(CaPtUrE)"); // TODO
																				// too
																				// crude
			patternText = "^" + patternText.replace("^", "\\^").replace("$", "\\$").replace(".", "\\.")
					.replace("*", ".*").replace("?", ".").replace("(CaPtUrE)", "(.*)") + "$";
			if (DEBUG)
				System.out.println("WildcardedTextSegment: transformed incoming text " + text + " to " + patternText);
//			this.pattern = Pattern.compile(patternText);
		}
		
		public void buildPattern(String text) {
			if (DEBUG) System.out.println("Pattern in :"+text);
			StringBuilder patternBuilder = new StringBuilder();
			Matcher matcher = GLOB_PATTERN.matcher(text);
			int end = 0;
			while (matcher.find()) {
				patternBuilder.append(quote(text, end, matcher.start()));
				String match = matcher.group();
				if ("?".equals(match)) {
					patternBuilder.append('.');
				}
				else if ("*".equals(match)) {
					patternBuilder.append(".*");
				}
				else if (match.startsWith("{") && match.endsWith("}")) {
					int colonIdx = match.indexOf(':');
					if (colonIdx == -1) {
						patternBuilder.append(DEFAULT_VARIABLE_PATTERN);
						this.variableNames.add(matcher.group(1));
					}
					else {
						String variablePattern = match.substring(colonIdx + 1, match.length() - 1);
						patternBuilder.append('(');
						patternBuilder.append(variablePattern);
						patternBuilder.append(')');
						String variableName = match.substring(1, colonIdx);
						this.variableNames.add(variableName);
					}
				}
				end = matcher.end();
			}
			patternBuilder.append(quote(text, end, text.length()));
			if (DEBUG) System.out.println("Pattern out: "+patternBuilder.toString());
			this.pattern = (caseSensitive ? Pattern.compile(patternBuilder.toString()) :
					Pattern.compile(patternBuilder.toString(), Pattern.CASE_INSENSITIVE));
		}

		private String quote(String s, int start, int end) {
			if (start == end) {
				return "";
			}
			return Pattern.quote(s.substring(start, end));
		}


		@Override
		public boolean matches(int candidateIndex, int sn, MatchingContext matchingContext) {
			// TODO what if not enough data for the next line?
			Matcher m = pattern.matcher(new SubSequence(matchingContext.candidate, candidateIndex, matchingContext.separatorPositions[sn]));
			boolean matches = m.matches();
			boolean somethingMatched = false;
			if (matches) {
				for (Segment nextSegment : nextSegments) {
					boolean match = nextSegment.matches(matchingContext.separatorPositions[sn], sn, matchingContext);
					if (match) {
						somethingMatched = true;
						if (!matchingContext.findAllMatches) {
							return true;
						}
					}
				}
			}
			if (somethingMatched && !matchingContext.matchStart) {
				// TODO
//				if (this.variableNames.size() != m.groupCount()) { SPR-8455
//					throw new IllegalArgumentException("The number of capturing groups in the pattern segment " +
//							this.pattern + " does not match the number of URI template variables it defines, " +
//							"which can occur if capturing groups are used in a URI template regex. " +
//							"Use non-capturing groups instead.");
//				}
				for (int i = 1; i <= m.groupCount(); i++) {
					String name = this.variableNames.get(i - 1);
					String value = m.group(i);
					System.out.println("Found "+name+" = "+value);
					matchingContext.set(name, value);
//					uriTemplateVariables.put(name, value);
				}
				// Need to do capture - TODO less object creation please
//				matchingContext.set(key,matchingContext.candidateText.substring(candidateIndex, matchingContext.separatorPositions[sn]));
			}
			return somethingMatched;
		}

		public String toString() {
			return "WildcardedText(" + new String(text) + ")";
		}

		// TODO [1] hashcode?
		public int hashCode() {
			return 19 + (37 * this.pos + text.hashCode()) * 37;
		}

		public boolean equals(Object o) {
			return (o instanceof WildcardedTextSegment) && ((WildcardedTextSegment) o).pos == this.pos
					&& ((WildcardedTextSegment) o).text.equals(this.text);
		}

	}

	class SeparatorStarStarSegment extends Segment {

		SeparatorStarStarSegment(int pos) {
			super(pos);
		}

		public String toString() {
			return "SeparatorStarStar(" + separator + "**)";
		}

		@Override
		public boolean matches(int candidateIndex, int sn, MatchingContext matchingContext) {
			if (DEBUG)
				printMatchStateDebug(matchingContext,candidateIndex);
			// this may be a piece of the pattern /**/foo
			// the candidate might be >/<foo or >/<testing/foo - both of these
			// should match (the >.< indicate candidateIndex)

			// Basically we have permission to jump down the segment chain
			// looking for matches if the 'current setup'
			// doesn't match

			boolean somethingMatched = false;
			for (Segment nextSegment : nextSegments) {
				boolean b = nextSegment.matches(candidateIndex, sn, matchingContext);
				if (b) {
					somethingMatched = true;
					if (!matchingContext.findAllMatches) {
						return true;
					}
					// TODO should 'break' here - why go looking for more?
				} else {
					for (int i = sn + 1; i <= matchingContext.separatorCount; i++) {
						if (DEBUG)
						System.out.println("/** skipping to next candidate, #separator=" + i + " pos=" + matchingContext.separatorPositions[i]);
						b = nextSegment.matches(matchingContext.separatorPositions[i], i, matchingContext);
						if (b) {
							somethingMatched = true;
							if (!matchingContext.findAllMatches) {
								return true;
							}
							// TODO should 'break' here - why go looking for more?
						}
					}
				}
			}
			return somethingMatched;
		}

		// TODO [1]
		public int hashCode() {
			return 17 + this.pos * 37;
		}

		public boolean equals(Object o) {
			return (o instanceof SeparatorStarStarSegment) && ((SeparatorStarStarSegment) o).pos == this.pos;
		}

	}

	class MatchSuccessSegment extends Segment {

		private URITemplate template;

		public MatchSuccessSegment(int pos, URITemplate template) {
			super(pos);
			this.template = template;
		}

		public String toString() {
			return "MatchSuccessSegment(" + template + ")";
		}

		@Override
		public boolean matches(int candidatePos, int sn, MatchingContext matchingContext) {
			if (DEBUG)
				printMatchStateDebug(matchingContext,candidatePos);
			// If there is more path then it is not a match
			if (candidatePos < matchingContext.candidateLength) {
				// unless the prevsegment was one of those munching ones
				if (previousSegment instanceof SeparatorStarStarSegment || previousSegment instanceof CapturingMultiTextSegment) {
					matchingContext.addMatchResult(this.template, matchingContext.candidateText);
					return true;
				} else {
					return false;
				}
			} else {
				matchingContext.addMatchResult(this.template, matchingContext.candidateText);
				return true;
			}
		}

		public int hashCode() {
			return 17 + (37 * this.pos + template.hashCode()) * 37;
		}

		public boolean equals(Object o) {
			return (o instanceof MatchSuccessSegment) && ((MatchSuccessSegment) o).pos == this.pos
					&& ((MatchSuccessSegment) o).template.equals(this.template);
		}

	}


}