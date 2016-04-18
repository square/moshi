package com.squareup.moshi;

import android.test.AndroidTestCase;

import java.io.IOException;
import java.lang.reflect.Type;

public class MoshiParametrizedTypeAndroidTest extends AndroidTestCase {

    public void testParametrizedType_fromJson() throws IOException {
        final JsonAdapter<RulesList<Rule>> moshiAdapter = jsonAdapter();

        final RulesList<Rule> rulesList = moshiAdapter.fromJson("{\"serializedName\":\"FOO\"}");
        assertEquals("FOO", rulesList.item.name);
    }

    public void testParametrizedType_toJson() throws IOException {
        final JsonAdapter<RulesList<Rule>> moshiAdapter = jsonAdapter();

        final RulesList<Rule> rulesList = new RulesList<>();
        rulesList.item.name = "BAR";
        final String json = moshiAdapter.toJson(rulesList);
        assertEquals("{\"serializedName\":\"BAR\"}", json);
    }

    JsonAdapter<RulesList<Rule>> jsonAdapter() {
        final Moshi moshi = new Moshi.Builder().add(new RulesListAdapter()).build();
        final Type type = Types.newParameterizedType(RulesList.class, Rule.class);
        return moshi.adapter(type);
    }

    static class RulesListAdapter {
        @FromJson
        RulesList<Rule> fromJson(RuleJson jsonRules) {
            final RulesList<Rule> rulesList = new RulesList<>();
            rulesList.item.name = jsonRules.serializedName;
            return rulesList;
        }

        @ToJson
        RuleJson toJson(RulesList<Rule> rulesList) {
            final RuleJson ruleJson = new RuleJson();
            ruleJson.serializedName = rulesList.item.name;
            return ruleJson;
        }
    }
}

// domain model - not json
class RulesList<T> {
    Rule item = new Rule();
}

// domain model - not json
class Rule {
    String name;
}

// serialized json format
class RuleJson {
    String serializedName;
}

