package ponticello.impl

import kotlin.math.max

class IntervalTree<V> {
    data class Interval<V>(val start: Decimal, val end: Decimal, val value: V) {
        fun overlaps(qStart: Decimal, qEnd: Decimal): Boolean {
            val strictlyBefore = end < qStart
            val strictlyAfter = start > qEnd
            return !strictlyBefore && !strictlyAfter
        }

        fun contains(time: Decimal): Boolean {
            val leftOk = time >= start
            val rightOk = time <= end
            return leftOk && rightOk
        }
    }

    private inner class Node(
        val keyStart: Decimal,
        val keyEnd: Decimal,
        intervals: MutableList<Interval<V>>,
    ) {
        var height: Int = 1
        var left: Node? = null
        var right: Node? = null
        val bucket: MutableList<Interval<V>> = intervals // intervals sharing identical bounds
        var maxEnd: Decimal? = computeLocalMaxEnd()

        private fun computeLocalMaxEnd(): Decimal? {
            // Max of ends within bucket (finite only)
            var cur: Decimal? = null
            for (iv in bucket) {
                val ek = iv.end
                if (cur == null || ek > cur) cur = ek
            }
            return cur
        }

        fun recompute(): Node {
            height = 1 + max(left?.height ?: 0, right?.height ?: 0)
            maxEnd = mergeMaxEnd(
                mergeMaxEnd(left?.maxEnd, right?.maxEnd),
                computeLocalMaxEnd()
            )
            return this
        }

        fun keyCompare(start: Decimal, end: Decimal): Int {
            // Order nodes by (start, startInclusive[closed > open], end, endInclusive[closed > open]) with null=-∞/+∞
            val cs = keyStart.compareTo(start)
            if (cs != 0) return cs
            val ce = keyEnd.compareTo(end)
            if (ce != 0) return ce
            // Equal
            return 0
        }
    }

    private var root: Node? = null

    /** Insert an interval. */
    fun add(value: V, start: Decimal, end: Decimal) {
        val interval = Interval(start, end, value)
        root = insert(root, interval)
    }

    /** Remove a single matching interval instance (by equals). Returns true if removed. */
    fun remove(value: V, start: Decimal, end: Decimal): Boolean {
        val interval = Interval(start, end, value)
        var removed = false
        root = delete(root, interval) { removed = true }
        return removed
    }

    /** Query all intervals containing [point]. */
    fun queryAt(point: Decimal): List<Interval<V>> {
        val result = mutableListOf<Interval<V>>()
        queryPoint(root, point, result)
        return result
    }

    /** Query all intervals overlapping the given range. Supports open-ended bounds via nulls. */
    fun queryOverlapping(qStart: Decimal, qEnd: Decimal): List<Interval<V>> {
        val out = mutableListOf<Interval<V>>()
        queryOverlap(root, qStart, qEnd, out)
        return out
    }

    // ------------------------ Internal helpers ------------------------

    private fun endAtLeast(endKey: Decimal?, point: Decimal): Boolean {
        // null maxEnd denotes +∞ present, which is always >= point
        if (endKey == null) return true
        val cmp = endKey.compareTo(point)
        return cmp >= 0
    }

    private fun insert(h: Node?, iv: Interval<V>): Node {
        if (h == null) {
            return Node(iv.start, iv.end, mutableListOf(iv))
        }
        val cmp = h.keyCompare(iv.start, iv.end)
        when {
            cmp == 0 -> {
                h.bucket.add(iv)
                return h.recompute()
            }

            cmp > 0 -> h.left = insert(h.left, iv)
            else -> h.right = insert(h.right, iv)
        }
        return balance(h.recompute())
    }

    private fun delete(h: Node?, iv: Interval<V>, onRemoved: () -> Unit): Node? {
        if (h == null) return null
        val cmp = h.keyCompare(iv.start, iv.end)
        when {
            cmp == 0 -> {
                val removed = h.bucket.remove(iv)
                if (removed) onRemoved()
                if (h.bucket.isNotEmpty()) return h.recompute()
                // remove node
                return when {
                    h.left == null -> h.right
                    h.right == null -> h.left
                    else -> {
                        // Replace with smallest in right subtree
                        val min = findMin(h.right!!)
                        val newNode =
                            Node(min.keyStart, min.keyEnd, min.bucket)
                        newNode.left = h.left
                        newNode.right = deleteMin(h.right!!)
                        balance(newNode.recompute())
                    }
                }
            }

            cmp > 0 -> h.left = delete(h.left, iv, onRemoved)
            else -> h.right = delete(h.right, iv, onRemoved)
        }
        return balance(h.recompute())
    }

    private fun findMin(h: Node): Node {
        var x = h
        while (x.left != null) x = x.left!!
        return x
    }

    private fun deleteMin(h: Node): Node? {
        if (h.left == null) return h.right
        h.left = deleteMin(h.left!!)
        return balance(h.recompute())
    }

    private fun height(x: Node?): Int = x?.height ?: 0

    private fun balanceFactor(x: Node?): Int = (x?.let { height(it.left) - height(it.right) }) ?: 0

    private fun balance(x: Node): Node {
        val bf = balanceFactor(x)
        return when {
            bf > 1 && balanceFactor(x.left) >= 0 -> rotateRight(x)
            bf > 1 -> {
                x.left = rotateLeft(x.left!!); rotateRight(x)
            }

            bf < -1 && balanceFactor(x.right) <= 0 -> rotateLeft(x)
            bf < -1 -> {
                x.right = rotateRight(x.right!!); rotateLeft(x)
            }

            else -> x
        }
    }

    private fun rotateRight(y: Node): Node {
        val x = y.left!!
        val t2 = x.right
        x.right = y
        y.left = t2
        y.recompute()
        x.recompute()
        return x
    }

    private fun rotateLeft(x: Node): Node {
        val y = x.right!!
        val t2 = y.left
        y.left = x
        x.right = t2
        x.recompute()
        y.recompute()
        return y
    }

    private fun mergeMaxEnd(a: Decimal?, b: Decimal?): Decimal? {
        return when {
            a == null || b == null -> null // null means +∞ dominates
            a >= b -> a
            else -> b
        }
    }

    private fun queryPoint(h: Node?, point: Decimal, out: MutableList<Interval<V>>) {
        h ?: return
        // If left subtree could contain intervals that extend to or past the point, search left first
        if (h.left != null && endAtLeast(h.left!!.maxEnd, point)) {
            queryPoint(h.left, point, out)
        }
        // Check bucket
        for (iv in h.bucket) if (iv.contains(point)) out.add(iv)
        // If right subtree might still have intervals starting after this node's start, we still need to explore.
        // Prune if right subtree's maxEnd < point
        if (h.right != null && endAtLeast(h.right!!.maxEnd, point)) {
            queryPoint(h.right, point, out)
        }
    }

    private fun queryOverlap(
        h: Node?, qStart: Decimal, qEnd: Decimal, out: MutableList<Interval<V>>,
    ) {
        h ?: return
        // If left subtree has any interval whose end >= qStart, we need to visit it
        if (h.left != null) {
            val canHave = endAtLeast(h.left!!.maxEnd, qStart)
            if (canHave) queryOverlap(h.left, qStart, qEnd, out)
        }
        // Check current bucket
        for (iv in h.bucket) if (iv.overlaps(qStart, qEnd)) out.add(iv)
        // Right subtree: still relevant if it can contain intervals with end >= qStart
        if (h.right != null) {
            val canHave = endAtLeast(h.right!!.maxEnd, qStart)
            if (canHave) queryOverlap(h.right, qStart, qEnd, out)
        }
    }
}

