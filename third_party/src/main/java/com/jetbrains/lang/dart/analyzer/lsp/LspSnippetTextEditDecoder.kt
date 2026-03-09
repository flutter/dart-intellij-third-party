// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.analyzer.lsp

internal data class DecodedSnippetTextEdit(
    val text: String,
    val placeholders: List<DecodedSnippetPlaceholder>,
)

internal data class DecodedSnippetPlaceholder(
    val number: Int,
    val offset: Int,
    val length: Int,
    val choices: List<String>?,
)

internal object LspSnippetTextEditDecoder {
    fun decode(text: String): DecodedSnippetTextEdit? {
        if ('$' !in text && '\\' !in text) return null

        val output = StringBuilder()
        val placeholders = mutableListOf<DecodedSnippetPlaceholder>()
        var index = 0
        while (index < text.length) {
            when (val char = text[index]) {
                '\\' -> {
                    if (index + 1 >= text.length) return null
                    output.append(text[index + 1])
                    index += 2
                }

                '$' -> {
                    val snippetElement = parseSnippetElement(text, index, output.length) ?: return null
                    output.append(snippetElement.insertedText)
                    placeholders.add(
                        DecodedSnippetPlaceholder(
                            snippetElement.number,
                            snippetElement.offset,
                            snippetElement.insertedText.length,
                            snippetElement.choices,
                        ),
                    )
                    index = snippetElement.nextIndex
                }

                else -> {
                    output.append(char)
                    index++
                }
            }
        }

        if (placeholders.isEmpty() && output.toString() == text) {
            return null
        }
        return DecodedSnippetTextEdit(output.toString(), placeholders)
    }

    private fun parseSnippetElement(
        text: String,
        startIndex: Int,
        outputOffset: Int,
    ): ParsedSnippetElement? {
        if (startIndex + 1 >= text.length) return null

        val next = text[startIndex + 1]
        if (next.isDigit()) {
            val (number, nextIndex) = parseNumber(text, startIndex + 1) ?: return null
            return ParsedSnippetElement(number, "", null, outputOffset, nextIndex)
        }
        if (next != '{') return null

        val (number, suffixIndex) = parseNumber(text, startIndex + 2) ?: return null
        if (suffixIndex >= text.length) return null

        return when (text[suffixIndex]) {
            '}' -> ParsedSnippetElement(number, "", null, outputOffset, suffixIndex + 1)
            ':' -> {
                val parsedText = parseUntilTerminator(text, suffixIndex + 1, '}') ?: return null
                ParsedSnippetElement(number, parsedText.value, null, outputOffset, parsedText.nextIndex)
            }

            '|' -> {
                val parsedChoices = parseChoices(text, suffixIndex + 1) ?: return null
                val insertedText = parsedChoices.choices.firstOrNull().orEmpty()
                ParsedSnippetElement(number, insertedText, parsedChoices.choices, outputOffset, parsedChoices.nextIndex)
            }

            else -> null
        }
    }

    private fun parseNumber(
        text: String,
        startIndex: Int,
    ): Pair<Int, Int>? {
        var index = startIndex
        while (index < text.length && text[index].isDigit()) {
            index++
        }
        if (index == startIndex) return null
        return text.substring(startIndex, index).toIntOrNull()?.let { it to index }
    }

    private fun parseUntilTerminator(
        text: String,
        startIndex: Int,
        terminator: Char,
    ): ParsedText? {
        val output = StringBuilder()
        var index = startIndex
        while (index < text.length) {
            val char = text[index]
            if (char == '\\') {
                if (index + 1 >= text.length) return null
                output.append(text[index + 1])
                index += 2
                continue
            }
            if (char == terminator) {
                return ParsedText(output.toString(), index + 1)
            }
            output.append(char)
            index++
        }
        return null
    }

    private fun parseChoices(
        text: String,
        startIndex: Int,
    ): ParsedChoices? {
        val choices = mutableListOf<String>()
        val currentChoice = StringBuilder()
        var index = startIndex
        while (index < text.length) {
            val char = text[index]
            if (char == '\\') {
                if (index + 1 >= text.length) return null
                currentChoice.append(text[index + 1])
                index += 2
                continue
            }
            if (char == ',') {
                choices.add(currentChoice.toString())
                currentChoice.setLength(0)
                index++
                continue
            }
            if (char == '|') {
                if (index + 1 >= text.length || text[index + 1] != '}') return null
                choices.add(currentChoice.toString())
                return ParsedChoices(choices, index + 2)
            }
            currentChoice.append(char)
            index++
        }
        return null
    }

    private data class ParsedSnippetElement(
        val number: Int,
        val insertedText: String,
        val choices: List<String>?,
        val offset: Int,
        val nextIndex: Int,
    )

    private data class ParsedText(
        val value: String,
        val nextIndex: Int,
    )

    private data class ParsedChoices(
        val choices: List<String>,
        val nextIndex: Int,
    )
}
