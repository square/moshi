/*
 * Copyright (C) 2012 Google Inc.
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

import com.squareup.moshi.LinkedHashTreeMap.AvlBuilder;
import com.squareup.moshi.LinkedHashTreeMap.AvlIterator;
import com.squareup.moshi.LinkedHashTreeMap.Node;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public final class LinkedHashTreeMapTest {
  @Test public void iterationOrder() {
    LinkedHashTreeMap<String, String> map = new LinkedHashTreeMap<>();
    map.put("a", "android");
    map.put("c", "cola");
    map.put("b", "bbq");
    assertThat(map.keySet()).containsExactly("a", "c", "b");
    assertThat(map.values()).containsExactly("android", "cola", "bbq");
  }

  @Test public void removeRootDoesNotDoubleUnlink() {
    LinkedHashTreeMap<String, String> map = new LinkedHashTreeMap<>();
    map.put("a", "android");
    map.put("c", "cola");
    map.put("b", "bbq");
    Iterator<Map.Entry<String,String>> it = map.entrySet().iterator();
    it.next();
    it.next();
    it.next();
    it.remove();
    assertThat(map.keySet()).containsExactly("a", "c");
  }

  @Test public void putNullKeyFails() {
    LinkedHashTreeMap<String, String> map = new LinkedHashTreeMap<>();
    try {
      map.put(null, "android");
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void putNonComparableKeyFails() {
    LinkedHashTreeMap<Object, String> map = new LinkedHashTreeMap<>();
    try {
      map.put(new Object(), "android");
      fail();
    } catch (ClassCastException expected) {}
  }

  @Test public void ContainsNonComparableKeyReturnsFalse() {
    LinkedHashTreeMap<Object, String> map = new LinkedHashTreeMap<>();
    map.put("a", "android");
    assertThat(map).doesNotContainKey(new Object());
  }

  @Test public void containsNullKeyIsAlwaysFalse() {
    LinkedHashTreeMap<String, String> map = new LinkedHashTreeMap<>();
    map.put("a", "android");
    assertThat(map).doesNotContainKey(null);
  }

  @Test public void putOverrides() throws Exception {
    LinkedHashTreeMap<String, String> map = new LinkedHashTreeMap<>();
    assertThat(map.put("d", "donut")).isNull();
    assertThat(map.put("e", "eclair")).isNull();
    assertThat(map.put("f", "froyo")).isNull();
    assertThat(map.size()).isEqualTo(3);

    assertThat(map.get("d")).isEqualTo("donut");
    assertThat(map.put("d", "done")).isEqualTo("donut");
    assertThat(map).hasSize(3);
  }

  @Test public void emptyStringValues() {
    LinkedHashTreeMap<String, String> map = new LinkedHashTreeMap<>();
    map.put("a", "");
    assertThat(map.containsKey("a")).isTrue();
    assertThat(map.get("a")).isEqualTo("");
  }

  // NOTE that this does not happen every time, but given the below predictable random,
  // this test will consistently fail (assuming the initial size is 16 and rehashing
  // size remains at 3/4)
  @Test public void forceDoublingAndRehash() throws Exception {
    Random random = new Random(1367593214724L);
    LinkedHashTreeMap<String, String> map = new LinkedHashTreeMap<>();
    String[] keys = new String[1000];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = Integer.toString(Math.abs(random.nextInt()), 36) + "-" + i;
      map.put(keys[i], "" + i);
    }

    for (int i = 0; i < keys.length; i++) {
      String key = keys[i];
      assertThat(map.containsKey(key)).isTrue();
      assertThat(map.get(key)).isEqualTo("" + i);
    }
  }

  @Test public void clear() {
    LinkedHashTreeMap<String, String> map = new LinkedHashTreeMap<>();
    map.put("a", "android");
    map.put("c", "cola");
    map.put("b", "bbq");
    map.clear();
    assertThat(map.keySet()).containsExactly();
    assertThat(map).isEmpty();
  }

  @Test public void equalsAndHashCode() throws Exception {
    LinkedHashTreeMap<String, Integer> map1 = new LinkedHashTreeMap<>();
    map1.put("A", 1);
    map1.put("B", 2);
    map1.put("C", 3);
    map1.put("D", 4);

    LinkedHashTreeMap<String, Integer> map2 = new LinkedHashTreeMap<>();
    map2.put("C", 3);
    map2.put("B", 2);
    map2.put("D", 4);
    map2.put("A", 1);

    assertThat(map2).isEqualTo(map1);
    assertThat(map2.hashCode()).isEqualTo(map1.hashCode());
  }

  @Test public void avlWalker() {
    assertAvlWalker(node(node("a"), "b", node("c")),
        "a", "b", "c");
    assertAvlWalker(node(node(node("a"), "b", node("c")), "d", node(node("e"), "f", node("g"))),
        "a", "b", "c", "d", "e", "f", "g");
    assertAvlWalker(node(node(null, "a", node("b")), "c", node(node("d"), "e", null)),
        "a", "b", "c", "d", "e");
    assertAvlWalker(node(null, "a", node(null, "b", node(null, "c", node("d")))),
        "a", "b", "c", "d");
    assertAvlWalker(node(node(node(node("a"), "b", null), "c", null), "d", null),
        "a", "b", "c", "d");
  }

  private void assertAvlWalker(Node<String, String> root, String... values) {
    AvlIterator<String, String> iterator = new AvlIterator<>();
    iterator.reset(root);
    for (String value : values) {
      assertThat(iterator.next().getKey()).isEqualTo(value);
    }
    assertThat(iterator.next()).isNull();
  }

  @Test public void avlBuilder() {
    assertAvlBuilder(1, "a");
    assertAvlBuilder(2, "(. a b)");
    assertAvlBuilder(3, "(a b c)");
    assertAvlBuilder(4, "(a b (. c d))");
    assertAvlBuilder(5, "(a b (c d e))");
    assertAvlBuilder(6, "((. a b) c (d e f))");
    assertAvlBuilder(7, "((a b c) d (e f g))");
    assertAvlBuilder(8, "((a b c) d (e f (. g h)))");
    assertAvlBuilder(9, "((a b c) d (e f (g h i)))");
    assertAvlBuilder(10, "((a b c) d ((. e f) g (h i j)))");
    assertAvlBuilder(11, "((a b c) d ((e f g) h (i j k)))");
    assertAvlBuilder(12, "((a b (. c d)) e ((f g h) i (j k l)))");
    assertAvlBuilder(13, "((a b (c d e)) f ((g h i) j (k l m)))");
    assertAvlBuilder(14, "(((. a b) c (d e f)) g ((h i j) k (l m n)))");
    assertAvlBuilder(15, "(((a b c) d (e f g)) h ((i j k) l (m n o)))");
    assertAvlBuilder(16, "(((a b c) d (e f g)) h ((i j k) l (m n (. o p))))");
    assertAvlBuilder(30, "((((. a b) c (d e f)) g ((h i j) k (l m n))) o "
        + "(((p q r) s (t u v)) w ((x y z) A (B C D))))");
    assertAvlBuilder(31, "((((a b c) d (e f g)) h ((i j k) l (m n o))) p "
        + "(((q r s) t (u v w)) x ((y z A) B (C D E))))");
  }

  private void assertAvlBuilder(int size, String expected) {
    char[] values = "abcdefghijklmnopqrstuvwxyzABCDE".toCharArray();
    AvlBuilder<String, String> avlBuilder = new AvlBuilder<>();
    avlBuilder.reset(size);
    for (int i = 0; i < size; i++) {
      avlBuilder.add(node(Character.toString(values[i])));
    }
    assertTree(expected, avlBuilder.root());
  }

  @Test public void doubleCapacity() {
    @SuppressWarnings("unchecked") // Arrays and generics don't get along.
    Node<String, String>[] oldTable = new Node[1];
    oldTable[0] = node(node(node("a"), "b", node("c")), "d", node(node("e"), "f", node("g")));

    Node<String, String>[] newTable = LinkedHashTreeMap.doubleCapacity(oldTable);
    assertTree("(b d f)", newTable[0]); // Even hash codes!
    assertTree("(a c (. e g))", newTable[1]); // Odd hash codes!
  }

  @Test public void doubleCapacityAllNodesOnLeft() {
    @SuppressWarnings("unchecked") // Arrays and generics don't get along.
        Node<String, String>[] oldTable = new Node[1];
    oldTable[0] = node(node("b"), "d", node("f"));

    Node<String, String>[] newTable = LinkedHashTreeMap.doubleCapacity(oldTable);
    assertTree("(b d f)", newTable[0]); // Even hash codes!
    assertThat(newTable[1]).isNull();

    for (Node<?, ?> node : newTable) {
      if (node != null) {
        assertConsistent(node);
      }
    }
  }

  private static final Node<String, String> head = new Node<>();

  private Node<String, String> node(String value) {
    return new Node<>(null, value, value.hashCode(), head, head);
  }

  private Node<String, String> node(Node<String, String> left, String value,
      Node<String, String> right) {
    Node<String, String> result = node(value);
    if (left != null) {
      result.left = left;
      left.parent = result;
    }
    if (right != null) {
      result.right = right;
      right.parent = result;
    }
    return result;
  }

  private void assertTree(String expected, Node<?, ?> root) {
    assertThat(toString(root)).isEqualTo(expected);
    assertConsistent(root);
  }

  private void assertConsistent(Node<?, ?> node) {
    int leftHeight = 0;
    if (node.left != null) {
      assertConsistent(node.left);
      assertThat(node.left.parent).isSameAs(node);
      leftHeight = node.left.height;
    }
    int rightHeight = 0;
    if (node.right != null) {
      assertConsistent(node.right);
      assertThat(node.right.parent).isSameAs(node);
      rightHeight = node.right.height;
    }
    if (node.parent != null) {
      assertThat(node.parent.left == node || node.parent.right == node).isTrue();
    }
    if (Math.max(leftHeight, rightHeight) + 1 != node.height) {
      fail();
    }
  }

  private String toString(Node<?, ?> root) {
    if (root == null) {
      return ".";
    } else if (root.left == null && root.right == null) {
      return String.valueOf(root.key);
    } else {
      return String.format("(%s %s %s)", toString(root.left), root.key, toString(root.right));
    }
  }
}
