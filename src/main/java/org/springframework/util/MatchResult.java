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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encapsulates information a successful match discovered by the {@link PathMatcher}.
 *
 * @author Andy Clement
 */
public class MatchResult {

	private final static Map<String,String> NO_VARIABLES = Collections.emptyMap();
	
	/**
	 * Which template successfully matched.
	 */
	private URITemplate matchingTemplate;
	
	/**
	 * The path which matched the template.
	 */
	private String matchingPath;
	
	/**
	 * A map of captured data. If the URI pattern captured values, for example with the pattern: /customer/{customerId}
	 * then the map will contain a key for customerId with the value being what was extracted from the matching path.
	 */
	private Map<String,String> capturedVariables;
	
	public MatchResult(URITemplate matchingTemplate, String matchingPath) {
		this.matchingTemplate = matchingTemplate;
		this.matchingPath = matchingPath;
	}
	
	/**
	 * @return the URITemplate that was matched.
	 */
	public URITemplate getMatchingTemplate() {
		return this.matchingTemplate;
	}

	/**
	 * @return the path that was matched
	 */
	public String getMatchingPath() {
		return this.matchingPath;
	}
	
	/**
	 * @return a map of any variables captured during the match, an empty map returned if nothing captured (never null).
	 */
	public Map<String,String> getCapturedVariables() {
		if (capturedVariables == null) {
			return NO_VARIABLES;
		}
		return capturedVariables;
	}

	/**
	 * @param key the name of a variable possibly specified in the template
	 * @return the value of that variable is successfully captured from the path, otherwise null
	 */
	public String getValue(String key) {
		if (capturedVariables == null) {
			return null;
		}
		return capturedVariables.get(key);
	}

	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append("MatchResult: path '").append(matchingPath).append("' matches template '");
		s.append(matchingTemplate.getTemplateText()).append(")");
		return s.toString();
	}

	// TODO [future] could receive/store positional info here and only chop up the URL on a request for the key
	void set(String key, String value) {
		if (capturedVariables == null) {
			capturedVariables = new LinkedHashMap<>();
		}
		capturedVariables.put(key,value);
	}
}