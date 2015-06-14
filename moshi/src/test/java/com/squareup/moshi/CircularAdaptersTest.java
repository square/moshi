/*
 * Copyright (C) 2015 Square, Inc.
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
package com.squareup.moshi;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.Type;
import java.util.Set;
import org.junit.Test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

public final class CircularAdaptersTest {
  static class Team {
    final String lead;
    final Project[] projects;

    public Team(String lead, Project... projects) {
      this.lead = lead;
      this.projects = projects;
    }
  }

  static class Project {
    final String name;
    final Team[] teams;

    Project(String name, Team... teams) {
      this.name = name;
      this.teams = teams;
    }
  }

  @Test public void circularAdapters() throws Exception {
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<Team> teamAdapter = moshi.adapter(Team.class);

    Team team = new Team("Alice", new Project("King", new Team("Charlie",
        new Project("Delivery", null))));
    assertThat(teamAdapter.toJson(team)).isEqualTo("{\"lead\":\"Alice\",\"projects\":[{\"name\":"
        + "\"King\",\"teams\":[{\"lead\":\"Charlie\",\"projects\":[{\"name\":\"Delivery\"}]}]}]}");

    Team fromJson = teamAdapter.fromJson("{\"lead\":\"Alice\",\"projects\":[{\"name\":"
        + "\"King\",\"teams\":[{\"lead\":\"Charlie\",\"projects\":[{\"name\":\"Delivery\"}]}]}]}");
    assertThat(fromJson.lead).isEqualTo("Alice");
    assertThat(fromJson.projects[0].name).isEqualTo("King");
    assertThat(fromJson.projects[0].teams[0].lead).isEqualTo("Charlie");
    assertThat(fromJson.projects[0].teams[0].projects[0].name).isEqualTo("Delivery");
  }

  @Retention(RUNTIME)
  @JsonQualifier
  public @interface Left {
  }

  @Retention(RUNTIME)
  @JsonQualifier
  public @interface Right {
  }

  static class Node {
    final String name;
    final @Left Node left;
    final @Right Node right;

    Node(String name, Node left, Node right) {
      this.name = name;
      this.left = left;
      this.right = right;
    }

    Node plusPrefix(String prefix) {
      return new Node(prefix + name, left, right);
    }

    Node minusPrefix(String prefix) {
      if (!name.startsWith(prefix)) throw new IllegalArgumentException();
      return new Node(name.substring(prefix.length()), left, right);
    }
  }

  /**
   * This factory uses extensive delegation. Each node delegates to this for the left and right
   * subtrees, and those delegate to the built-in class adapter to do most of the serialization
   * work.
   */
  static class PrefixingNodeFactory implements JsonAdapter.Factory {
    @Override public JsonAdapter<?> create(
        Type type, Set<? extends Annotation> annotations, Moshi moshi) {
      if (type != Node.class) return null;

      final String prefix;
      if (Util.isAnnotationPresent(annotations, Left.class)) {
        prefix = "L ";
      } else if (Util.isAnnotationPresent(annotations, Right.class)) {
        prefix = "R ";
      } else {
        return null;
      }

      final JsonAdapter<Node> delegate = moshi.nextAdapter(this, Node.class, Util.NO_ANNOTATIONS);

      return new JsonAdapter<Node>() {
        @Override public void toJson(JsonWriter writer, Node value) throws IOException {
          delegate.toJson(writer, value.plusPrefix(prefix));
        }

        @Override public Node fromJson(JsonReader reader) throws IOException {
          Node result = delegate.fromJson(reader);
          return result.minusPrefix(prefix);
        }
      }.nullSafe();
    }
  }

  @Test public void circularAdaptersAndAnnotations() throws Exception {
    Moshi moshi = new Moshi.Builder()
        .add(new PrefixingNodeFactory())
        .build();
    JsonAdapter<Node> nodeAdapter = moshi.adapter(Node.class);

    Node tree = new Node("C",
        new Node("A", null, new Node("B", null, null)),
        new Node("D", null, new Node("E", null, null)));
    assertThat(nodeAdapter.toJson(tree)).isEqualTo("{"
        + "\"left\":{\"name\":\"L A\",\"right\":{\"name\":\"R B\"}},"
        + "\"name\":\"C\","
        + "\"right\":{\"name\":\"R D\",\"right\":{\"name\":\"R E\"}}"
        + "}");

    Node fromJson = nodeAdapter.fromJson("{"
        + "\"left\":{\"name\":\"L A\",\"right\":{\"name\":\"R B\"}},"
        + "\"name\":\"C\","
        + "\"right\":{\"name\":\"R D\",\"right\":{\"name\":\"R E\"}}"
        + "}");
    assertThat(fromJson.name).isEqualTo("C");
    assertThat(fromJson.left.name).isEqualTo("A");
    assertThat(fromJson.left.right.name).isEqualTo("B");
    assertThat(fromJson.right.name).isEqualTo("D");
    assertThat(fromJson.right.right.name).isEqualTo("E");
  }
}
