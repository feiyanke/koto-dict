package io.koto.dict

/**
 * Created by Administrator on 2017/7/26.
 */
class Dictionary : Map<String, Set<Any>> {

    class Builder {
        val list = mutableListOf<Pair<String, Any>>()
        fun add(word:String, value:Any=word):Builder {
            list.add(word to value)
            return this
        }
        fun build() : Dictionary = Dictionary(list)
    }

    private constructor(list:List<Pair<String, Any>>) {
        list.forEach { add(it.first, it.second) }
        build()
    }

    private class EntryPair(pair:Pair<String, Set<Any>>) : Map.Entry<String, Set<Any>> {
        override val key = pair.first
        override val value = pair.second
    }

    override val entries: Set<Map.Entry<String, Set<Any>>> by lazy {
        mutableSetOf<Map.Entry<String, Set<Any>>>().apply {
            trie.traverseBreadth {
                it.entry?.also {
                    add(EntryPair(it))
                }
            }
        }
    }
    override val keys: Set<String> by lazy {
        mutableSetOf<String>().apply {
            entries.forEach {
                add(it.key)
            }
        }
    }
    override val size: Int by lazy { entries.size }

    override val values: Collection<Set<Any>> by lazy {
        mutableListOf<Set<Any>>().apply {
            entries.forEach {
                add(it.value)
            }
        }
    }
    override fun containsKey(key: String) = keys.contains(key)

    override fun containsValue(value: Set<Any>) = values.contains(value)

    override fun get(key: String): Set<Any>? = trie.contains(key)

    override fun isEmpty() = entries.isEmpty()

    private val trie : Node = Node()

    private fun add(word:String, vararg values:Any) = trie.add(word, *values)
    private fun build() = trie.build()

    fun find(s: String, exclusive:Boolean = true) = Emitter(s).find(exclusive).flatMap { it.objects }.toSet()

    inner private class Emitter(val text:String) {
        var node = trie
        var position = 0

        fun find(exclusive:Boolean=true) = if (exclusive) findExclusive() else findAll()

        fun findAll():List<Emit> {
            return mutableListOf<Emit>().apply {
                text.forEach {
                    position++
                    node = node.go(it)
                    addAll(emitAll(node))
                }
            }
        }

        fun findExclusive():List<Emit> {
            val emits = Array<Emit?>(text.length) {null}
            text.forEach {
                position++
                node = node.go(it)
                emitExclusive(node)?.let {
                    var start = it.position - it.name.length
                    emits[start] = it
                    for (i in start+1 until position) {
                        emits[i] = null
                    }
                }
            }
            return emits.filterNotNull()
        }

        fun emitExclusive(n:Node):Emit? {
            var node = n
            while (true) {
                val entry = node.entry
                if (entry != null) {
                    return Emit(position, entry.first, entry.second)
                }
                node = node.back()?:return null
            }
        }

        fun emitAll(n:Node):List<Emit> {
            return mutableListOf<Emit>().apply {
                var node = n
                while (true) {
                    val entry = node.entry
                    if (entry != null) {
                        add(Emit(position, entry.first, entry.second))
                    }
                    node = node.back()?:break
                }
            }
        }

//        fun back(exclusive: Boolean, emit: ((Emit) -> Unit)) {
//            while (true) {
//                val entry = node.entry
//                if (entry != null) {
//                    emit.invoke(Emit(position, entry.first, entry.second))
//                    if (exclusive) return
//                }
//                node = node.back()?:return
//            }
//        }
    }

//    private class Emitter(var node: Node) {
//
//        fun go(c: Char, i:Int,  emit: ((Node, Int) -> Unit)? = null) : Node {
//            node = node.go(c)
//            emit?.invoke(node, i)
//            return node
//        }
//
//        fun go(s:String, emit: ((Node, Int) -> Unit)? = null) {
//            s.forEachIndexed { i,it->
//                go(it, i, emit) }
//        }
//
//        fun go(c: Char,  emit: ((Node) -> Unit)? = null) : Node {
//            node = node.go(c)
//            emit?.invoke(node)
//            return node
//        }
//
//        fun go(s:String, emit: ((Node) -> Unit)? = null) {
//            s.forEach { go(it, emit) }
//        }
//
//        fun back(exclusive: Boolean, i:Int, emit: ((Emit) -> Unit)) {
//            while (true) {
//                val entry = node.entry
//                if (entry != null) {
//                    emit.invoke(Emit(i, entry.first, entry.second))
//                    if (exclusive) return
//                }
//                node = node.back()?:return
//            }
//        }
//    }

    class Emit(val position:Int, val name:String, val objects:MutableSet<Any>)

    private open class Node {

        override fun toString() = "Node"
        val children: MutableMap<Char, State> = mutableMapOf()
        open fun getChildFailure(c: Char) : Node = this
        fun getState(c: Char) : State = children[c]?: State(c, this).also { children[c] = it  }
        var entry : Pair<String, MutableSet<Any>>? = null
        fun terminal(word: String) : MutableSet<Any> =
                entry?.second?:mutableSetOf<Any>().also { entry = word to it }

        open fun back() : Node? = null

        open fun go(c: Char): Node = children[c]?:this

        fun add(word:String, vararg values:Any){
            var state = this
            word.toCharArray().forEach {
                state = state.getState(it)
            }
            state.terminal(word).addAll(values)
        }

        fun contains(word:String) : Set<Any>? {
            var state = this
            word.toCharArray().forEach {
                state = state.children[it]?:return null
            }
            return state.entry?.second
        }

        fun build() {
            traverseBreadth { it.failure/*println("$it@${it.c}:${it.failure}")*/}
        }

        inline fun traverseBreadth(visit:(State)->Unit) {
            val queue = mutableListOf<State>()
            queue.addAll(children.values)
            while (queue.isNotEmpty()) {
                queue.removeAt(0).let {
                    visit(it)
                    queue.addAll(it.children.values)
                }
            }
        }

        inline fun traverseDepth(visit:(State)->Unit) {
            children.values.forEach {
                visit(it)
                it.traverseBreadth(visit)
            }
        }

    }
    private class State(val c: Char, val parent: Node) : Node() {

        override fun toString() = "State@$c"

        val failure: Node by lazy { parent.getChildFailure(c) }
        override fun getChildFailure(c: Char): Node = failure.children[c]?:failure.getChildFailure(c)
        override fun go(c: Char): Node = children[c]?:failure.go(c)
        override fun back() = failure
    }
}