package org.langgraphkt.demo.workflows

import org.langgraphkt.demo.llm.ChatModel
import org.langgraphkt.demo.llm.ScriptedChatModel

/** The offline model: every workflow's scripted answers, with a delay so the UI shows progress. */
fun scriptedDemoModel(delayMillis: Long = 0): ChatModel =
    ScriptedChatModel(listOf(ToolAgent.script, EmailApproval.script, Research.script), delayMillis)
