package com.squareup.moshi

import com.squareup.moshi.LinkedHashTreeMap.Node
import com.squareup.moshi.internal.knownNotNull
import java.io.Serializable
import kotlin.math.max

@Suppress("UNCHECKED_CAST")
private val NATURAL_ORDER = Comparator<Any> { o1, o2 -> (o1 as Comparable<Any>).compareTo(o2) }

/**
 * A map of comparable keys to values. Unlike TreeMap, this class uses insertion order for
 * iteration order. Comparison order is only used as an optimization for efficient insertion and
 * removal.
 *
 * This implementation was derived from Android 4.1's TreeMap and LinkedHashMap classes.
 */
internal class LinkedHashTreeMap<K, V>
/**
 * Create a tree map ordered by [comparator]. This map's keys may only be null if [comparator] permits.
 *
 * @param comparator the comparator to order elements with, or null to use the natural ordering.
 */
constructor(
  comparator: Comparator<Any?>? = null
) : AbstractMutableMap<K, V>(), Serializable {
  @Suppress("UNCHECKED_CAST")
  private val comparator: Comparator<Any?> = (comparator ?: NATURAL_ORDER) as Comparator<Any?>
  private var table: Array<Node<K, V>?> = arrayOfNulls(16) // TODO: sizing/resizing policies
  private val header: Node<K, V> = Node()
  override var size = 0
  private var modCount = 0
  private var threshold = table.size / 2 + table.size / 4 // 3/4 capacity
  private var entrySet: EntrySet? = null
  private var keySet: KeySet? = null

  override val keys: MutableSet<K>
    get() = keySet ?: KeySet().also { keySet = it }

  override fun put(key: K, value: V): V? {
    val created = findOrCreate(key)
    val result = created.value
    created.mutableValue = value
    return result
  }

  override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    get() = entrySet ?: EntrySet().also { entrySet = it }

  override fun get(key: K) = findByObject(key)?.value

  override fun containsKey(key: K) = findByObject(key) != null

  override fun clear() {
    table.fill(null)
    size = 0
    modCount++

    // Clear all links to help GC
    val header = header
    var e = header.next
    while (e !== header) {
      val next = e!!.next
      e.prev = null
      e.next = null
      e = next
    }
    header.prev = header
    header.next = header.prev
  }

  override fun remove(key: K) = removeInternalByKey(key)?.value

  class Node<K, V> : MutableMap.MutableEntry<K, V?> {
    @JvmField
    var parent: Node<K, V>? = null

    @JvmField
    var left: Node<K, V>? = null

    @JvmField
    var right: Node<K, V>? = null

    @JvmField
    var next: Node<K, V>?
    @JvmField
    var prev: Node<K, V>?

    private var realKey: K? = null

    override val key: K get() = knownNotNull(realKey)

    @JvmField
    val hash: Int

    @JvmField
    var mutableValue: V? = null

    override val value: V?
      get() = mutableValue

    @JvmField
    var height = 0

    /** Create the header entry. */
    constructor() {
      realKey = null
      hash = -1
      prev = this
      next = prev
    }

    /** Create a regular entry. */
    constructor(parent: Node<K, V>?, key: K, hash: Int, next: Node<K, V>, prev: Node<K, V>) {
      this.parent = parent
      this.realKey = key
      this.hash = hash
      height = 1
      this.next = next
      this.prev = prev
      prev.next = this
      next.prev = this
    }

    override fun setValue(newValue: V?): V? {
      val oldValue = this.value
      this.mutableValue = newValue
      return oldValue
    }

    override fun equals(other: Any?): Boolean {
      if (other is Map.Entry<*, *>) {
        val (key1, value1) = other
        return (
          (if (realKey == null) key1 == null else realKey == key1) &&
            if (value == null) value1 == null else value == value1
          )
      }
      return false
    }

    override fun hashCode(): Int {
      return (realKey?.hashCode() ?: 0) xor if (value == null) 0 else value.hashCode()
    }

    override fun toString() = "$key=$value"

    /** Returns the first node in this subtree. */
    fun first(): Node<K, V> {
      var node = this
      var child = node.left
      while (child != null) {
        node = child
        child = node.left
      }
      return node
    }

    /** Returns the last node in this subtree. */
    fun last(): Node<K, V> {
      var node = this
      var child = node.right
      while (child != null) {
        node = child
        child = node.right
      }
      return node
    }
  }

  private fun doubleCapacity() {
    table = doubleCapacity(table)
    threshold = table.size / 2 + table.size / 4 // 3/4 capacity
  }

  /**
   * Returns the node at or adjacent to the given key, creating it if requested.
   *
   * @throws ClassCastException if `key` and the tree's keys aren't mutually comparable.
   */
  private fun findOrCreate(key: K): Node<K, V> {
    return knownNotNull(find(key, create = true))
  }

  /**
   * Returns the node at or adjacent to the given key, creating it if requested.
   *
   * @throws ClassCastException if `key` and the tree's keys aren't mutually comparable.
   */
  fun find(key: K, create: Boolean): Node<K, V>? {
    val comparator: Comparator<in K?> = comparator
    val table = table
    val hash = secondaryHash(key.hashCode())
    val index = hash and table.size - 1
    var nearest = table[index]
    var comparison = 0
    if (nearest != null) {
      // Micro-optimization: avoid polymorphic calls to Comparator.compare().
      // Throws a ClassCastException below if there's trouble.
      @Suppress("UNCHECKED_CAST")
      val comparableKey =
        if (comparator === NATURAL_ORDER) key as Comparable<Any?> else null
      while (true) {
        comparison = comparableKey?.compareTo(knownNotNull(nearest).key) ?: comparator.compare(key, knownNotNull(nearest).key)

        // We found the requested key.
        if (comparison == 0) {
          return nearest
        }

        // If it exists, the key is in a subtree. Go deeper.
        val child = (if (comparison < 0) knownNotNull(nearest).left else knownNotNull(nearest).right) ?: break
        nearest = child
      }
    }

    // The key doesn't exist in this tree.
    if (!create) {
      return null
    }

    // Create the node and add it to the tree or the table.
    val header = header
    val created: Node<K, V>
    if (nearest == null) {
      // Check that the value is comparable if we didn't do any comparisons.
      if (comparator === NATURAL_ORDER && key !is Comparable<*>) {
        throw ClassCastException("${(key as Any).javaClass.name} is not Comparable")
      }
      created = Node(null, key, hash, header, knownNotNull(header.prev))
      table[index] = created
    } else {
      created = Node(nearest, key, hash, header, knownNotNull(header.prev))
      if (comparison < 0) { // nearest.key is higher
        nearest.left = created
      } else { // comparison > 0, nearest.key is lower
        nearest.right = created
      }
      rebalance(nearest, true)
    }
    if (size++ > threshold) {
      doubleCapacity()
    }
    modCount++
    return created
  }

  private fun findByObject(key: Any?): Node<K, V>? {
    return try {
      @Suppress("UNCHECKED_CAST")
      if (key != null) find(key as K, false) else null
    } catch (e: ClassCastException) {
      null
    }
  }

  /**
   * Returns this map's entry that has the same key and value as `entry`, or null if this map
   * has no such entry.
   *
   * This method uses the comparator for key equality rather than `equals`. If this map's
   * comparator isn't consistent with equals (such as `String.CASE_INSENSITIVE_ORDER`), then
   * `remove()` and `contains()` will violate the collections API.
   */
  fun findByEntry(entry: Map.Entry<*, *>): Node<K, V>? {
    val mine = findByObject(entry.key)
    val valuesEqual = mine != null && equal(mine.value, entry.value)
    return if (valuesEqual) mine else null
  }

  private fun equal(a: Any?, b: Any?): Boolean {
    @Suppress("SuspiciousEqualsCombination")
    return a === b || a != null && a == b
  }

  /**
   * Applies a supplemental hash function to a given hashCode, which defends against poor quality
   * hash functions. This is critical because HashMap uses power-of-two length hash tables, that
   * otherwise encounter collisions for hashCodes that do not differ in lower or upper bits.
   */
  private fun secondaryHash(seed: Int): Int {
    // Doug Lea's supplemental hash function
    var h = seed
    h = h xor (h ushr 20 xor (h ushr 12))
    return h xor (h ushr 7) xor (h ushr 4)
  }

  /**
   * Removes `node` from this tree, rearranging the tree's structure as necessary.
   *
   * @param unlink true to also unlink this node from the iteration linked list.
   */
  fun removeInternal(node: Node<K, V>, unlink: Boolean) {
    if (unlink) {
      knownNotNull(node.prev).next = node.next
      knownNotNull(node.next).prev = node.prev
      node.prev = null
      node.next = null // Help the GC (for performance)
    }
    var left = node.left
    var right = node.right
    val originalParent = node.parent
    if (left != null && right != null) {
      /*
       * To remove a node with both left and right subtrees, move an
       * adjacent node from one of those subtrees into this node's place.
       *
       * Removing the adjacent node may change this node's subtrees. This
       * node may no longer have two subtrees once the adjacent node is
       * gone!
       */
      val adjacent = if (left.height > right.height) left.last() else right.first()
      removeInternal(adjacent, false) // takes care of rebalance and size--
      var leftHeight = 0
      left = node.left
      if (left != null) {
        leftHeight = left.height
        adjacent.left = left
        left.parent = adjacent
        node.left = null
      }
      var rightHeight = 0
      right = node.right
      if (right != null) {
        rightHeight = right.height
        adjacent.right = right
        right.parent = adjacent
        node.right = null
      }
      adjacent.height = max(leftHeight, rightHeight) + 1
      replaceInParent(node, adjacent)
      return
    } else if (left != null) {
      replaceInParent(node, left)
      node.left = null
    } else if (right != null) {
      replaceInParent(node, right)
      node.right = null
    } else {
      replaceInParent(node, null)
    }
    rebalance(originalParent, false)
    size--
    modCount++
  }

  fun removeInternalByKey(key: Any?): Node<K, V>? {
    val node = findByObject(key)
    if (node != null) {
      removeInternal(node, true)
    }
    return node
  }

  private fun replaceInParent(node: Node<K, V>, replacement: Node<K, V>?) {
    val parent = node.parent
    node.parent = null
    if (replacement != null) {
      replacement.parent = parent
    }
    if (parent != null) {
      if (parent.left === node) {
        parent.left = replacement
      } else {
        assert(parent.right === node)
        parent.right = replacement
      }
    } else {
      val index = node.hash and table.size - 1
      table[index] = replacement
    }
  }

  /**
   * Rebalances the tree by making any AVL rotations necessary between the newly-unbalanced node and
   * the tree's root.
   *
   * @param insert true if the node was unbalanced by an insert; false if it was by a removal.
   */
  private fun rebalance(unbalanced: Node<K, V>?, insert: Boolean) {
    var node = unbalanced
    while (node != null) {
      val left = node.left
      val right = node.right
      val leftHeight = left?.height ?: 0
      val rightHeight = right?.height ?: 0
      val delta = leftHeight - rightHeight
      when (delta) {
        -2 -> {
          val rightLeft = right!!.left
          val rightRight = right.right
          val rightRightHeight = rightRight?.height ?: 0
          val rightLeftHeight = rightLeft?.height ?: 0
          val rightDelta = rightLeftHeight - rightRightHeight
          if (rightDelta != -1 && (rightDelta != 0 || insert)) {
            assert(rightDelta == 1)
            rotateRight(right) // AVL right left
          }
          rotateLeft(node) // AVL right right
          if (insert) {
            break // no further rotations will be necessary
          }
        }
        2 -> {
          val leftLeft = left!!.left
          val leftRight = left.right
          val leftRightHeight = leftRight?.height ?: 0
          val leftLeftHeight = leftLeft?.height ?: 0
          val leftDelta = leftLeftHeight - leftRightHeight
          if (leftDelta != 1 && (leftDelta != 0 || insert)) {
            assert(leftDelta == -1)
            rotateLeft(left) // AVL left right
          }
          rotateRight(node) // AVL left left
          if (insert) {
            break // no further rotations will be necessary
          }
        }
        0 -> {
          node.height = leftHeight + 1 // leftHeight == rightHeight
          if (insert) {
            break // the insert caused balance, so rebalancing is done!
          }
        }
        else -> {
          assert(delta == -1 || delta == 1)
          node.height = max(leftHeight, rightHeight) + 1
          if (!insert) {
            break // the height hasn't changed, so rebalancing is done!
          }
        }
      }
      node = node.parent
    }
  }

  /** Rotates the subtree so that its root's right child is the new root.  */
  private fun rotateLeft(root: Node<K, V>) {
    val left = root.left
    val pivot = root.right
    val pivotLeft = pivot!!.left
    val pivotRight = pivot.right

    // move the pivot's left child to the root's right
    root.right = pivotLeft
    if (pivotLeft != null) {
      pivotLeft.parent = root
    }
    replaceInParent(root, pivot)

    // move the root to the pivot's left
    pivot.left = root
    root.parent = pivot

    // fix heights
    root.height = max(left?.height ?: 0, pivotLeft?.height ?: 0) + 1
    pivot.height = max(root.height, pivotRight?.height ?: 0) + 1
  }

  /** Rotates the subtree so that its root's left child is the new root.  */
  private fun rotateRight(root: Node<K, V>) {
    val pivot = root.left
    val right = root.right
    val pivotLeft = pivot!!.left
    val pivotRight = pivot.right

    // move the pivot's right child to the root's left
    root.left = pivotRight
    if (pivotRight != null) {
      pivotRight.parent = root
    }
    replaceInParent(root, pivot)

    // move the root to the pivot's right
    pivot.right = root
    root.parent = pivot

    // fixup heights
    root.height = max(right?.height ?: 0, pivotRight?.height ?: 0) + 1
    pivot.height = max(root.height, pivotLeft?.height ?: 0) + 1
  }

  abstract inner class LinkedTreeMapIterator<T> : MutableIterator<T> {
    var next = header.next
    private var lastReturned: Node<K, V>? = null
    private var expectedModCount: Int = modCount
    override fun hasNext(): Boolean = next !== header

    fun nextNode(): Node<K, V> {
      val e = next
      if (e === header) {
        throw NoSuchElementException()
      }
      if (modCount != expectedModCount) {
        throw ConcurrentModificationException()
      }
      next = e!!.next
      return e.also { lastReturned = it }
    }

    override fun remove() {
      removeInternal(checkNotNull(lastReturned), true)
      lastReturned = null
      expectedModCount = modCount
    }
  }

  inner class EntrySet : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
    override val size: Int
      get() = this@LinkedHashTreeMap.size

    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
      return object : LinkedTreeMapIterator<MutableMap.MutableEntry<K, V>>() {
        override fun next(): MutableMap.MutableEntry<K, V> {
          @Suppress("UNCHECKED_CAST")
          return nextNode() as MutableMap.MutableEntry<K, V>
        }
      }
    }

    override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
      return findByEntry(element) != null
    }

    override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
      if (element !is Node<*, *>) {
        return false
      }
      val node: Node<K, V> = findByEntry(element) ?: return false
      removeInternal(node, true)
      return true
    }

    override fun clear() {
      this@LinkedHashTreeMap.clear()
    }

    override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
      throw NotImplementedError()
    }
  }

  inner class KeySet : AbstractMutableSet<K>() {
    override val size: Int
      get() = this@LinkedHashTreeMap.size

    override fun iterator(): MutableIterator<K> {
      return object : LinkedTreeMapIterator<K>() {
        override fun next(): K {
          return nextNode().key ?: throw NoSuchElementException()
        }
      }
    }

    override fun contains(element: K): Boolean {
      return containsKey(element)
    }

    override fun remove(element: K): Boolean {
      return removeInternalByKey(element) != null
    }

    override fun clear() {
      this@LinkedHashTreeMap.clear()
    }

    override fun add(element: K): Boolean {
      throw NotImplementedError()
    }
  }

  /**
   * If somebody is unlucky enough to have to serialize one of these, serialize it as a
   * LinkedHashMap so that they won't need Gson on the other side to deserialize it. Using
   * serialization defeats our DoS defence, so most apps shouldn't use it.
   */
  private fun writeReplace(): Any = LinkedHashMap(this)
}

/**
 * Returns a new array containing the same nodes as `oldTable`, but with twice as many
 * trees, each of (approximately) half the previous size.
 */
internal fun <K, V> doubleCapacity(oldTable: Array<Node<K, V>?>): Array<Node<K, V>?> {
  // TODO: don't do anything if we're already at MAX_CAPACITY
  val oldCapacity = oldTable.size
  // Arrays and generics don't get along.
  val newTable: Array<Node<K, V>?> = arrayOfNulls<Node<K, V>?>(oldCapacity * 2)
  val iterator = AvlIterator<K, V>()
  val leftBuilder = AvlBuilder<K, V>()
  val rightBuilder = AvlBuilder<K, V>()

  // Split each tree into two trees.
  for (i in 0 until oldCapacity) {
    val root = oldTable[i] ?: continue

    // Compute the sizes of the left and right trees.
    iterator.reset(root)
    var leftSize = 0
    var rightSize = 0
    run {
      var node: Node<K, V>?
      while (iterator.next().also { node = it } != null) {
        if (knownNotNull(node).hash and oldCapacity == 0) {
          leftSize++
        } else {
          rightSize++
        }
      }
    }

    // Split the tree into two.
    leftBuilder.reset(leftSize)
    rightBuilder.reset(rightSize)
    iterator.reset(root)
    var node: Node<K, V>?
    while (iterator.next().also { node = it } != null) {
      if (knownNotNull(node).hash and oldCapacity == 0) {
        leftBuilder.add(knownNotNull(node))
      } else {
        rightBuilder.add(knownNotNull(node))
      }
    }

    // Populate the enlarged array with these new roots.
    newTable[i] = if (leftSize > 0) leftBuilder.root() else null
    newTable[i + oldCapacity] = if (rightSize > 0) rightBuilder.root() else null
  }
  return newTable
}

/**
 * Walks an AVL tree in iteration order. Once a node has been returned, its left, right and parent
 * links are **no longer used**. For this reason it is safe to transform these links
 * as you walk a tree.
 *
 * **Warning:** this iterator is destructive. It clears the parent node of all
 * nodes in the tree. It is an error to make a partial iteration of a tree.
 */
internal class AvlIterator<K, V> {
  /** This stack is a singly linked list, linked by the 'parent' field.  */
  private var stackTop: Node<K, V>? = null
  fun reset(root: Node<K, V>?) {
    var stackTop: Node<K, V>? = null
    var n = root
    while (n != null) {
      n.parent = stackTop
      stackTop = n // Stack push.
      n = n.left
    }
    this.stackTop = stackTop
  }

  operator fun next(): Node<K, V>? {
    var stackTop: Node<K, V>? = stackTop ?: return null
    val result = stackTop
    stackTop = result!!.parent
    result.parent = null
    var n = result.right
    while (n != null) {
      n.parent = stackTop
      stackTop = n // Stack push.
      n = n.left
    }
    this.stackTop = stackTop
    return result
  }
}

/**
 * Builds AVL trees of a predetermined size by accepting nodes of increasing value. To use:
 *  1. Call [reset] to initialize the target size *size*.
 *  2. Call [add] *size* times with increasing values.
 *  3. Call [root] to get the root of the balanced tree.
 *
 * The returned tree will satisfy the AVL constraint: for every node *N*, the height of
 * *N.left* and *N.right* is different by at most 1. It accomplishes this by omitting
 * deepest-level leaf nodes when building trees whose size isn't a power of 2 minus 1.
 *
 * Unlike rebuilding a tree from scratch, this approach requires no value comparisons. Using
 * this class to create a tree of size *S* is `O(S)`.
 */
internal class AvlBuilder<K, V> {
  /** This stack is a singly linked list, linked by the 'parent' field.  */
  private var stack: Node<K, V>? = null
  private var leavesToSkip = 0
  private var leavesSkipped = 0
  private var size = 0
  fun reset(targetSize: Int) {
    // compute the target tree size. This is a power of 2 minus one, like 15 or 31.
    val treeCapacity = Integer.highestOneBit(targetSize) * 2 - 1
    leavesToSkip = treeCapacity - targetSize
    size = 0
    leavesSkipped = 0
    stack = null
  }

  fun add(node: Node<K, V>) {
    node.right = null
    node.parent = null
    node.left = null
    node.height = 1

    // Skip a leaf if necessary.
    if (leavesToSkip > 0 && size and 1 == 0) {
      size++
      leavesToSkip--
      leavesSkipped++
    }
    node.parent = stack
    stack = node // Stack push.
    size++

    // Skip a leaf if necessary.
    if (leavesToSkip > 0 && size and 1 == 0) {
      size++
      leavesToSkip--
      leavesSkipped++
    }

    /*
     * Combine 3 nodes into subtrees whenever the size is one less than a
     * multiple of 4. For example, we combine the nodes A, B, C into a
     * 3-element tree with B as the root.
     *
     * Combine two subtrees and a spare single value whenever the size is one
     * less than a multiple of 8. For example at 8 we may combine subtrees
     * (A B C) and (E F G) with D as the root to form ((A B C) D (E F G)).
     *
     * Just as we combine single nodes when size nears a multiple of 4, and
     * 3-element trees when size nears a multiple of 8, we combine subtrees of
     * size (N-1) whenever the total size is 2N-1 whenever N is a power of 2.
     */
    var scale = 4
    while (size and scale - 1 == scale - 1) {
      when (leavesSkipped) {
        0 -> {
          // Pop right, center and left, then make center the top of the stack.
          val right = stack
          val center = right!!.parent
          val left = center!!.parent
          center.parent = left!!.parent
          stack = center
          // Construct a tree.
          center.left = left
          center.right = right
          center.height = right.height + 1
          left.parent = center
          right.parent = center
        }
        1 -> {
          // Pop right and center, then make center the top of the stack.
          val right = stack
          val center = right!!.parent
          stack = center!!
          // Construct a tree with no left child.
          center.right = right
          center.height = right.height + 1
          right.parent = center
          leavesSkipped = 0
        }
        2 -> {
          leavesSkipped = 0
        }
      }
      scale *= 2
    }
  }

  fun root(): Node<K, V> {
    val stackTop = stack
    check(stackTop!!.parent == null)
    return stackTop
  }
}
