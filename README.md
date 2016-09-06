# pathmatcher

Experimenting with pattern matching.

The PatternMatcher here is fed patterns and builds a tree from them. The tree splits at points where the patterns change.
By spending time up front on building the tree structure the hope is that the matching process can be fast. For example
if matching "/customer/endpoint1" and "/customer/endpoint2" we will only match "/customer/" once because it is the same
across both patterns.

Usage, see Sample.java:

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


