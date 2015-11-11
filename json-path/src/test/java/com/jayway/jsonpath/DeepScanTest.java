package com.jayway.jsonpath;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.jayway.jsonpath.JsonPath.using;
import static com.jayway.jsonpath.TestUtils.assertEvaluationThrows;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deep scan is indefinite, so certain "illegal" actions become a no-op instead of a path evaluation exception.
 */
public class DeepScanTest extends BaseTest {

    @Test
    public void when_deep_scanning_non_array_subscription_is_ignored() {
        Object result = JsonPath.parse("{\"x\": [0,1,[0,1,2,3,null],null]}").read("$..[2][3]");
        assertThat(result).asList().containsOnly(3);
        result = JsonPath.parse("{\"x\": [0,1,[0,1,2,3,null],null], \"y\": [0,1,2]}").read("$..[2][3]");
        assertThat(result).asList().containsOnly(3);

        result = JsonPath.parse("{\"x\": [0,1,[0,1,2],null], \"y\": [0,1,2]}").read("$..[2][3]");
        assertThat(result).asList().isEmpty();
    }

    @Test
    public void when_deep_scanning_null_subscription_is_ignored() {
        Object result = JsonPath.parse("{\"x\": [null,null,[0,1,2,3,null],null]}").read("$..[2][3]");
        assertThat(result).asList().containsOnly(3);
        result = JsonPath.parse("{\"x\": [null,null,[0,1,2,3,null],null], \"y\": [0,1,null]}").read("$..[2][3]");
        assertThat(result).asList().containsOnly(3);
    }

    @Test
    public void when_deep_scanning_array_index_oob_is_ignored() {
        Object result = JsonPath.parse("{\"x\": [0,1,[0,1,2,3,10],null]}").read("$..[4]");
        assertThat(result).asList().containsOnly(10);

        result = JsonPath.parse("{\"x\": [null,null,[0,1,2,3]], \"y\": [null,null,[0,1]]}").read("$..[2][3]");
        assertThat(result).asList().containsOnly(3);
    }

    @Test
    public void definite_upstream_illegal_array_access_throws() {
        assertEvaluationThrows("{\"foo\": {\"bar\": null}}", "$.foo.bar.[5]", PathNotFoundException.class);
        assertEvaluationThrows("{\"foo\": {\"bar\": null}}", "$.foo.bar.[5, 10]", PathNotFoundException.class);

        assertEvaluationThrows("{\"foo\": {\"bar\": 4}}", "$.foo.bar.[5]", InvalidPathException.class);
        assertEvaluationThrows("{\"foo\": {\"bar\": 4}}", "$.foo.bar.[5, 10]", InvalidPathException.class);

        assertEvaluationThrows("{\"foo\": {\"bar\": []}}", "$.foo.bar.[5]", PathNotFoundException.class);
    }

    @Test
    public void when_deep_scanning_illegal_property_access_is_ignored() {
        Object result = JsonPath.parse("{\"x\": {\"foo\": {\"bar\": 4}}, \"y\": {\"foo\": 1}}").read("$..foo");
        assertThat(result).asList().hasSize(2);

        result = JsonPath.parse("{\"x\": {\"foo\": {\"bar\": 4}}, \"y\": {\"foo\": 1}}").read("$..foo.bar");
        assertThat(result).asList().containsOnly(4);
        result = JsonPath.parse("{\"x\": {\"foo\": {\"bar\": 4}}, \"y\": {\"foo\": 1}}").read("$..[*].foo.bar");
        assertThat(result).asList().containsOnly(4);
        result = JsonPath.parse("{\"x\": {\"foo\": {\"baz\": 4}}, \"y\": {\"foo\": 1}}").read("$..[*].foo.bar");
        assertThat(result).asList().isEmpty();
    }

    @Test
    public void when_deep_scanning_illegal_predicate_is_ignored() {
        Object result = JsonPath.parse("{\"x\": {\"foo\": {\"bar\": 4}}, \"y\": {\"foo\": 1}}").read(
                "$..foo[?(@.bar)].bar");
        assertThat(result).asList().containsOnly(4);

        result = JsonPath.parse("{\"x\": {\"foo\": {\"bar\": 4}}, \"y\": {\"foo\": 1}}").read(
                "$..[*]foo[?(@.bar)].bar");
        assertThat(result).asList().containsOnly(4);
    }

    @Test
    public void when_deep_scanning_require_properties_still_counts() {
        final Configuration conf = Configuration.defaultConfiguration().addOptions(Option.REQUIRE_PROPERTIES);

        Object result = JsonPath.parse("[{\"x\": {\"foo\": {\"x\": 4}, \"x\": null}, \"y\": {\"x\": 1}}, {\"x\": []}]").read(
                "$..x");
        assertThat(result).asList().hasSize(5);

        // foo.bar must be found in every object node after deep scan (which is impossible)
        assertEvaluationThrows("{\"foo\": {\"bar\": 4}}", "$..foo.bar", PathNotFoundException.class, conf);

        assertEvaluationThrows("{\"foo\": {\"bar\": 4}, \"baz\": 2}", "$..['foo', 'baz']", PathNotFoundException.class, conf);
    }

    @Test
    public void when_deep_scanning_leaf_multi_props_work() {
        Object result = JsonPath.parse("[{\"a\": \"a-val\", \"b\": \"b-val\", \"c\": \"c-val\"}, [1, 5], {\"a\": \"a-val\"}]").read(
                "$..['a', 'c']");
        // This is current deep scan semantics: only objects containing all properties specified in multiprops token are
        // considered.
        assertThat(result).asList().hasSize(1);
        result = ((List)result).get(0);

        assertThat(result).isInstanceOf(Map.class);
        assertThat((Map)result).hasSize(2).containsEntry("a", "a-val").containsEntry("c", "c-val");

        // But this semantics changes when DEFAULT_PATH_LEAF_TO_NULL comes into play.
        Configuration conf = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);
        result = using(conf).parse("[{\"a\": \"a-val\", \"b\": \"b-val\", \"c\": \"c-val\"}, [1, 5], {\"a\": \"a-val\"}]").read(
                "$..['a', 'c']");
        // todo: deep equality test, but not tied to any json provider
        assertThat(result).asList().hasSize(2);
        for (final Object node : (List)result) {
            assertThat(node).isInstanceOf(Map.class);
            assertThat((Map)node).hasSize(2).containsEntry("a", "a-val");
        }
    }
}