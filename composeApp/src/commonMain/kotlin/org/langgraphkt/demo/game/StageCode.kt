package org.langgraphkt.demo.game

import org.langgraphkt.END
import org.langgraphkt.START

/** A piece of code, with a few words that say what it is. */
data class CodePart(val title: String, val code: String)

/**
 * The code behind the tiles of a stage's board, cut out of the source of the stage's graph.
 *
 * The source is the text of the real files, which the build puts into the app. It is read by its
 * indentation: a statement is a line with every deeper line after it, up to the line that closes it.
 *
 * @param function the name of the function that builds the graph.
 * @param sources the text of the files to look for it in.
 */
class StageCode(function: String, sources: Collection<String>) {
    /** A declaration or a statement, without the indentation it had in its file. */
    private class Block(val text: String) {
        /** The first line that is not a comment. */
        val head: String = text.lineSequence().first { !it.isComment() }.trim()
        val name: String? = DECLARATION.find(head)?.groupValues?.get(1)

        fun mentions(word: String): Boolean = Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(text)
    }

    private val members: List<Block>
    private val graph: Block
    private val constants: Map<String, String>

    /** The statements of the graph's body that draw arrows. */
    private val arrows: List<Block>

    /** The statement of each node that is added with `node(...)`, and the name of its `val`, by node name. */
    private val nodes: Map<String, Pair<String, Block>>

    /** The names of the nodes that `toolLoop` adds, and its statement. */
    private val loop: Pair<Set<String>, Block>?

    init {
        val files = sources.map { blocks(it.lines(), MEMBER_INDENT) }
        members = files.firstOrNull { file -> file.any { it.builds(function) } }
            ?: throw IllegalArgumentException("No function '$function' builds a graph in the sources.")
        graph = members.first { it.builds(function) }
        constants = members.mapNotNull { CONSTANT.find(it.head) }.associate { it.groupValues[1] to it.groupValues[2] }

        val statements = blocks(graph.text.lines(), MEMBER_INDENT)
        arrows = statements.filter { !it.head.startsWith("val ") && TOOL_LOOP !in it.text }
        nodes = statements.mapNotNull { statement ->
            NODE.find(statement.head)?.let { resolve(it.groupValues[2]) to (it.groupValues[1] to statement) }
        }.toMap()
        loop = statements.firstOrNull { TOOL_LOOP in it.text }?.let { statement ->
            fun name(parameter: String, default: String) =
                Regex("$parameter = ([\\w\"]+)").find(statement.text)?.let { resolve(it.groupValues[1]) } ?: default
            setOf(name("modelNode", "model"), name("toolsNode", "tools")) to statement
        }
    }

    /** Returns the code to show for the tile of [node]: the node itself, its arrows and the functions it calls. */
    fun of(node: String): List<CodePart> {
        val parts = when {
            node == START -> listOf(CodePart("arrows from START", arrowsWith("START")))
            node == END -> listOf(CodePart("arrows to END", arrowsWith("END")))
            loop != null && node in loop.first ->
                listOf(CodePart("the agent loop adds this node", loop.second.text), CodePart("it uses", used(loop.second)))
            else -> nodes[node]?.let { (name, statement) ->
                listOf(CodePart("the node", statement.text), CodePart("its arrows", arrowsWith(name)), CodePart("it uses", used(statement)))
            }.orEmpty()
        }.filter { it.code.isNotEmpty() }
        return parts.ifEmpty { listOf(CodePart("the graph", graph.text)) }
    }

    private fun arrowsWith(word: String): String =
        (arrows + listOfNotNull(loop?.second)).filter { it.mentions(word) }.joinToString("\n") { it.text }

    /**
     * The functions and values of the file that [statement] mentions, and the ones those mention.
     * A constant with a short value is left out. A long one, such as a prompt, is shown.
     */
    private fun used(statement: Block): String {
        val helpers = members.filter { it.name != null && CONSTANT.find(it.head) == null && !it.builds(it.name) }
        val found = mutableListOf<Block>()
        // A parameter of the graph's function can stand for a helper: `tools: List<Tool> = deskTools`.
        val parameters = graph.head.substringAfter('(').split(", ").filter { parameter ->
            PARAMETER.find(parameter)?.let { statement.mentions(it.groupValues[1]) } == true
        }
        var look = listOf(Block(statement.text + "\n" + parameters.joinToString("\n")))
        while (look.isNotEmpty()) {
            val next = helpers.filter { helper -> helper !in found && look.any { it.mentions(helper.name!!) } }
            found += next
            look = next
        }
        return helpers.filter { it in found }.joinToString("\n\n") { it.text }
    }

    private fun Block.builds(function: String): Boolean = head.contains("fun $function(") && "StateGraph<" in head

    /** A node is named by a text or by a constant. */
    private fun resolve(argument: String): String =
        if (argument.startsWith('"')) argument.trim('"') else constants[argument] ?: argument

    private companion object {
        /** Members of an object are indented once, and so are the statements of a function. */
        const val MEMBER_INDENT = 4
        const val TOOL_LOOP = "toolLoop("
        val DECLARATION = Regex("""^(?:(?:private|internal|suspend|const) )*(?:fun|val|class) (\w+)""")
        val CONSTANT = Regex("""const val (\w+)(?:: String)? = "([^"]*)"""")
        val NODE = Regex("""^val (\w+) = node\(([^,)]+)""")
        val PARAMETER = Regex("""^(\w+):""")

        fun String.isComment(): Boolean = trimStart().let { it.startsWith("//") || it.startsWith("/*") || it.startsWith("*") }

        /**
         * Splits [lines] into the blocks that start at [indent]. A block is its comment, the line
         * it starts on, every deeper line after it, and the line at [indent] that closes it.
         */
        fun blocks(lines: List<String>, indent: Int): List<Block> {
            val result = mutableListOf<Block>()
            var current = mutableListOf<String>()
            var hasCode = false

            fun finish() {
                if (hasCode) result += Block(current.joinToString("\n") { it.drop(indent) }.trimEnd())
                current = mutableListOf()
                hasCode = false
            }

            for (line in lines) {
                val text = line.trim()
                val depth = line.length - line.trimStart().length
                when {
                    // An empty line is part of a block that has started, and ends a comment that stands alone.
                    text.isEmpty() -> if (hasCode) current += "" else current.clear()
                    depth < indent -> finish()
                    depth > indent || text.startsWith("}") || text.startsWith(")") -> if (current.isNotEmpty()) current += line
                    else -> {
                        if (hasCode) finish()
                        current += line
                        if (!text.isComment()) hasCode = true
                    }
                }
            }
            finish()
            return result
        }
    }
}
