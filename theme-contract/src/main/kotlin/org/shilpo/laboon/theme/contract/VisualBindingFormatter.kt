package org.shilpo.laboon.theme.contract

object VisualBindingFormatter {
    // Escape both braces: Android's ICU regex engine rejects unescaped closing braces.
    private val bindingPattern = Regex("\\{\\{([A-Za-z][A-Za-z0-9_.-]{0,127})\\}\\}")

    fun format(template: String?, bindings: Map<String, String>): String =
        template.orEmpty()
            .replace(bindingPattern) { match -> bindings[match.groupValues[1]].orEmpty() }
}
