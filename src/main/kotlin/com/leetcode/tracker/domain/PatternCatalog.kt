package com.leetcode.tracker.domain

object PatternCatalog {
    private val entries = listOf(
        PatternDef("two-pointers", "Two Pointers", listOf("two-pointers", "two pointers")),
        PatternDef("sliding-window", "Sliding Window", listOf("sliding-window", "sliding window")),
        PatternDef("dynamic-programming", "Dynamic Programming", listOf("dynamic-programming", "dynamic programming", "dp")),
        PatternDef("hash-table", "Hash Table", listOf("hash-table", "hashtable", "hash table")),
        PatternDef("binary-search", "Binary Search", listOf("binary-search", "binary search")),
        PatternDef("breadth-first-search", "Breadth-First Search", listOf("breadth-first-search", "breadth first search", "bfs")),
        PatternDef("depth-first-search", "Depth-First Search", listOf("depth-first-search", "depth first search", "dfs")),
        PatternDef("greedy", "Greedy", listOf("greedy")),
        PatternDef("backtracking", "Backtracking", listOf("backtracking")),
        PatternDef("union-find", "Union Find", listOf("union-find", "union find")),
        PatternDef("heap-priority-queue", "Heap / Priority Queue", listOf("heap-priority-queue", "heap (priority queue)", "priority queue")),
        PatternDef("stack", "Stack", listOf("stack")),
        PatternDef("queue", "Queue", listOf("queue")),
        PatternDef("linked-list", "Linked List", listOf("linked-list", "linked list")),
        PatternDef("tree", "Tree", listOf("tree")),
        PatternDef("binary-tree", "Binary Tree", listOf("binary-tree", "binary tree")),
        PatternDef("trie", "Trie", listOf("trie", "prefix tree")),
        PatternDef("graph", "Graph", listOf("graph")),
        PatternDef("bit-manipulation", "Bit Manipulation", listOf("bit-manipulation", "bit manipulation")),
        PatternDef("math", "Math", listOf("math")),
        PatternDef("sorting", "Sorting", listOf("sorting")),
        PatternDef("recursion", "Recursion", listOf("recursion")),
        PatternDef("string", "String", listOf("string")),
        PatternDef("array", "Array", listOf("array")),
    )

    private val byKey = entries.associateBy { it.key }

    fun canonicalKeys(): Set<String> = byKey.keys

    fun displayName(key: String): String = byKey[key]?.display ?: key.replace('-', ' ').replaceFirstChar { it.uppercase() }

    fun normalizeInput(raw: String): String {
        val slug = raw.trim().lowercase().replace(' ', '-').replace('_', '-')
        if (byKey.containsKey(slug)) return slug
        val found = entries.firstOrNull { e ->
            e.aliases.any { it.equals(raw.trim(), ignoreCase = true) } ||
                e.display.equals(raw.trim(), ignoreCase = true)
        }
        return found?.key ?: slug
    }

    fun leetcodeAliasIndex(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (e in entries) {
            map[e.key] = e.key
            for (a in e.aliases) {
                map[a.lowercase()] = e.key
            }
        }
        return map
    }

    /**
     * Maps LeetCode tag slugs (from GraphQL) to canonical pattern keys. Unknown tags are ignored.
     */
    fun mapLeetCodeTags(tagSolvedBySlug: Map<String, Int>): Map<String, Int> {
        val index = leetcodeAliasIndex()
        val merged = mutableMapOf<String, Int>()
        for ((rawSlug, count) in tagSolvedBySlug) {
            val slug = rawSlug.lowercase()
            val key = index[slug]
                ?: canonicalKeys().find { ck -> ck == slug }
                ?: continue
            merged[key] = maxOf(merged[key] ?: 0, count)
        }
        return merged
    }

    private data class PatternDef(
        val key: String,
        val display: String,
        val aliases: List<String>,
    )
}
