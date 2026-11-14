/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.dartlsp.api.LspServer
import com.intellij.platform.dartlsp.api.customization.LspCommandsSupport
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.jetbrains.lang.dart.DartBundle
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.util.regex.PatternSyntaxException
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/**
 * Custom [LspCommandsSupport] that resolves interactive commands via `command/resolve`
 * before executing `workspace/executeCommand`.
 *
 * When `command/resolve` returns non-empty [InteractiveExecuteCommandParams.formFields],
 * a modal [DartInteractiveFormDialog] is presented to collect and validate user answers
 * (both client-side via [RegexValidator] / type rules and server-side via follow-up
 * `command/resolve` rounds) before dispatching `workspace/executeCommand`.
 */
class DartLspCommandsSupport(private val project: Project) : LspCommandsSupport() {

    override fun executeCommand(server: LspServer, contextFile: VirtualFile, command: Command) {
        val initialParams = InteractiveExecuteCommandParams(
            command = command.command,
            arguments = command.arguments,
        )
        val dialogTitle = command.title.takeIf { it.isNotBlank() } ?: DartBundle.message("dart.title")
        val resolveResult = resolveCommandWithProgress(project, server, dialogTitle, initialParams)
        if (resolveResult.cancelled) return
        val resolved = resolveResult.params ?: return

        if (resolved.formFields.isNullOrEmpty()) {
            sendExecuteCommand(server, resolved)
            return
        }

        val dialog = DartInteractiveFormDialog(project, server, dialogTitle, resolved)
        if (dialog.showAndGet()) {
            val finalResolved = dialog.finalResolvedParams ?: return
            sendExecuteCommand(server, finalResolved)
        }
    }

    internal data class CommandResolveResult(
        val params: InteractiveExecuteCommandParams?,
        val cancelled: Boolean,
    )

    companion object {
        @VisibleForTesting
        internal fun sendExecuteCommand(server: LspServer, resolved: InteractiveExecuteCommandParams) {
            server.sendNotification {
                it.workspaceService.executeCommand(ExecuteCommandParams(resolved.command, resolved.arguments))
            }
        }

        @VisibleForTesting
        internal fun resolveCommandWithProgress(
            project: Project,
            server: LspServer,
            @NlsContexts.DialogTitle progressTitle: String,
            params: InteractiveExecuteCommandParams,
        ): CommandResolveResult {
            var resolved: InteractiveExecuteCommandParams? = null
            val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                {
                    resolved = server.sendRequestSync { (it as DartLanguageServer).resolveCommand(params) }
                },
                progressTitle,
                true,
                project,
            )
            return CommandResolveResult(params = if (completed) resolved else null, cancelled = !completed)
        }

        @VisibleForTesting
        internal fun uriStringToFilePath(uriString: String?): String {
            if (uriString.isNullOrBlank()) return ""
            return try {
                val uri = URI(uriString)
                if ("file".equals(uri.scheme, ignoreCase = true)) {
                    FileUtil.toSystemDependentName(Paths.get(uri).toString())
                } else {
                    uriString
                }
            } catch (_: Exception) {
                uriString
            }
        }

        @VisibleForTesting
        internal fun resolveLocalFilePath(pathOrUri: String, basePath: String? = null): String {
            val trimmed = pathOrUri.trim()
            if (trimmed.isEmpty()) return ""
            val localPath = if (trimmed.startsWith("file://", ignoreCase = true)) {
                uriStringToFilePath(trimmed)
            } else {
                trimmed
            }
            val normalized = FileUtil.toSystemIndependentName(localPath)
            val file = File(normalized)
            val resolvedFile = if (!file.isAbsolute && !basePath.isNullOrBlank()) {
                File(FileUtil.toSystemIndependentName(basePath), normalized)
            } else {
                file
            }
            return FileUtil.toSystemIndependentName(resolvedFile.path)
        }

        @VisibleForTesting
        internal fun filePathToUriString(filePath: String, basePath: String? = null): String {
            val trimmed = filePath.trim()
            if (trimmed.isEmpty()) return ""
            if (trimmed.startsWith("file://", ignoreCase = true)) {
                return DartLspServerDescriptor.normalizeWindowsDriveInFileUri(trimmed)
            }
            val normalizedPath = resolveLocalFilePath(trimmed, basePath)
            val rawUri = VfsUtil.toUri(File(normalizedPath))?.toString() ?: return trimmed
            return DartLspServerDescriptor.normalizeWindowsDriveInFileUri(rawUri)
        }

        @VisibleForTesting
        internal fun parseNumberAnswer(text: String): Number? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            return trimmed.toLongOrNull() ?: trimmed.toDoubleOrNull()?.takeIf { it.isFinite() }
        }

        /**
         * Validates a single [FormField] against client-side rules and any active server-side error.
         *
         * Returns a [ValidationInfo] if invalid or if a warning should be displayed, or `null` if valid.
         */
        @VisibleForTesting
        internal fun validateFieldInput(
            field: FormField,
            rawValue: Any?,
            activeServerError: String? = field.error,
            component: JComponent? = null,
            basePath: String? = null,
        ): ValidationInfo? {
            if (!activeServerError.isNullOrBlank()) {
                return ValidationInfo(activeServerError, component)
            }

            return when (field.type.kind) {
                FormFieldType.KIND_BOOL -> null

                FormFieldType.KIND_STRING -> {
                    val text = (rawValue as? String).orEmpty()
                    if (text.isEmpty()) {
                        if (field.required) {
                            return ValidationInfo(DartBundle.message("dart.refactoring.interactive.form.required"), component)
                        }
                        return null
                    }
                    var highestNonErrorSeverity = 0
                    var highestNonErrorValidation: ValidationInfo? = null
                    for (validator in field.type.validators.orEmpty()) {
                        if (validator.kind != "regex") continue
                        val matches = try {
                            Regex(validator.pattern).containsMatchIn(text)
                        } catch (_: PatternSyntaxException) {
                            continue
                        }
                        if (matches != validator.matchIsValid) {
                            if (validator.severity >= RegexValidator.SEVERITY_ERROR) {
                                return ValidationInfo(validator.message, component)
                            } else if (validator.severity > highestNonErrorSeverity) {
                                highestNonErrorSeverity = validator.severity
                                highestNonErrorValidation = ValidationInfo(validator.message, component).asWarning().withOKEnabled()
                            }
                        }
                    }
                    highestNonErrorValidation
                }

                FormFieldType.KIND_NUMBER -> {
                    val text = (rawValue as? String)?.trim().orEmpty()
                    if (text.isEmpty()) {
                        if (field.required) {
                            return ValidationInfo(DartBundle.message("dart.refactoring.interactive.form.required"), component)
                        }
                        return null
                    }
                    if (parseNumberAnswer(text) == null) {
                        return ValidationInfo(DartBundle.message("dart.refactoring.interactive.form.invalid.number"), component)
                    }
                    null
                }

                FormFieldType.KIND_FILE -> {
                    val pathText = (rawValue as? String)?.trim().orEmpty()
                    if (pathText.isEmpty()) {
                        if (field.required) {
                            return ValidationInfo(DartBundle.message("dart.refactoring.interactive.form.required"), component)
                        }
                        return null
                    }
                    val resolvedPath = resolveLocalFilePath(pathText, basePath)
                    val filters = field.type.filters?.filter { it.isNotBlank() }.orEmpty()
                    if (filters.isNotEmpty()) {
                        val fileName = File(resolvedPath).name
                        val ext = fileName.substringAfterLast('.', "")
                        if (filters.none { it.equals(ext, ignoreCase = true) }) {
                            return ValidationInfo(
                                DartBundle.message("dart.refactoring.interactive.form.invalid.file.extension", filters.joinToString(", ") { ".$it" }),
                                component,
                            )
                        }
                    }
                    val existence = field.type.existence
                    val fileType = field.type.type
                    if (existence != null || fileType != null) {
                        val vFile = LocalFileSystem.getInstance().findFileByPath(resolvedPath)
                        val exists = vFile != null && vFile.isValid
                        if (existence != null) {
                            val allowsNew = (existence and FormFieldType.FILE_EXISTENCE_NEW) != 0
                            val allowsExisting = (existence and FormFieldType.FILE_EXISTENCE_EXISTING) != 0
                            if (!allowsNew && allowsExisting && !exists) {
                                return ValidationInfo(DartBundle.message("dart.refactoring.interactive.form.file.not.found"), component)
                            }
                            if (allowsNew && !allowsExisting && exists) {
                                return ValidationInfo(DartBundle.message("dart.refactoring.interactive.form.file.already.exists"), component)
                            }
                        }
                        if (exists && vFile != null && fileType != null) {
                            val allowsRegular = (fileType and FormFieldType.FILE_TYPE_REGULAR) != 0
                            val allowsDirectory = (fileType and FormFieldType.FILE_TYPE_DIRECTORY) != 0
                            if (allowsRegular && !allowsDirectory && vFile.isDirectory) {
                                return ValidationInfo(DartBundle.message("dart.refactoring.interactive.form.expected.regular.file"), component)
                            }
                            if (allowsDirectory && !allowsRegular && !vFile.isDirectory) {
                                return ValidationInfo(DartBundle.message("dart.refactoring.interactive.form.expected.directory"), component)
                            }
                        }
                    }
                    null
                }

                else -> null
            }
        }

        /**
         * Builds the list of [FormAnswer]s to submit in a follow-up `command/resolve` call.
         *
         * Optional fields with blank values are omitted because `FormAnswer.value` is non-nullable
         * in the Dart Analysis Server's generated protocol (`protocol_custom_generated.dart`).
         */
        @VisibleForTesting
        internal fun collectFormAnswers(
            fields: List<FormField>,
            rawValuesById: Map<String, Any?>,
            basePath: String? = null,
        ): List<FormAnswer> {
            val answers = mutableListOf<FormAnswer>()
            for (field in fields) {
                val rawValue = rawValuesById[field.id]
                when (field.type.kind) {
                    FormFieldType.KIND_BOOL -> {
                        val boolVal = rawValue as? Boolean ?: (field.defaultValue as? Boolean) ?: false
                        answers.add(FormAnswer(field.id, boolVal))
                    }
                    FormFieldType.KIND_NUMBER -> {
                        val text = (rawValue as? String)?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            val number = parseNumberAnswer(text) ?: continue
                            answers.add(FormAnswer(field.id, number))
                        }
                    }
                    FormFieldType.KIND_FILE -> {
                        val pathText = (rawValue as? String)?.trim().orEmpty()
                        if (pathText.isNotEmpty() || field.required) {
                            answers.add(FormAnswer(field.id, filePathToUriString(pathText, basePath)))
                        }
                    }
                    else -> {
                        val text = (rawValue as? String).orEmpty()
                        if (text.isNotEmpty() || field.required) {
                            answers.add(FormAnswer(field.id, text))
                        }
                    }
                }
            }
            return answers
        }
    }
}

/**
 * Modal dialog that renders [FormField] controls for Dart LSP interactive commands (`command/resolve`).
 */
class DartInteractiveFormDialog(
    private val project: Project,
    private val server: LspServer,
    @NlsContexts.DialogTitle private val dialogTitle: String,
    initialParams: InteractiveExecuteCommandParams,
) : DialogWrapper(project, true) {

    @VisibleForTesting
    internal var currentParams: InteractiveExecuteCommandParams = initialParams
        private set
    private var fields: List<FormField> = initialParams.formFields.orEmpty()
    @VisibleForTesting
    internal val activeServerErrors: MutableMap<String, String?> = mutableMapOf()
    @VisibleForTesting
    internal val fieldComponents: MutableMap<String, JComponent> = linkedMapOf()
    private val focusTargets: MutableMap<String, JComponent> = linkedMapOf()

    internal var finalResolvedParams: InteractiveExecuteCommandParams? = null
        private set

    init {
        title = dialogTitle
        for (field in fields) {
            if (!field.error.isNullOrBlank()) {
                activeServerErrors[field.id] = field.error
            }
        }
        init()
        initValidation()
    }

    public override fun createCenterPanel(): JComponent {
        val builder = FormBuilder.createFormBuilder()
        for (field in fields) {
            when (field.type.kind) {
                FormFieldType.KIND_BOOL -> {
                    val defaultBool = field.defaultValue as? Boolean ?: false
                    val checkBox = JBCheckBox(field.description, defaultBool)
                    checkBox.addActionListener {
                        activeServerErrors.remove(field.id)
                    }
                    fieldComponents[field.id] = checkBox
                    focusTargets[field.id] = checkBox
                    builder.addComponent(checkBox)
                }

                FormFieldType.KIND_FILE -> {
                    val defaultUri = field.defaultValue as? String
                    val initialPath = DartLspCommandsSupport.uriStringToFilePath(defaultUri)
                    val fileTypeFlags = field.type.type ?: FormFieldType.FILE_TYPE_REGULAR
                    val chooseFiles = (fileTypeFlags and FormFieldType.FILE_TYPE_REGULAR) != 0
                    val chooseFolders = (fileTypeFlags and FormFieldType.FILE_TYPE_DIRECTORY) != 0
                    val descriptor = FileChooserDescriptor(chooseFiles, chooseFolders, false, false, false, false)
                        .withTitle(field.description)
                    val filters = field.type.filters?.filter { it.isNotBlank() }.orEmpty()
                    if (filters.isNotEmpty() && chooseFiles && !chooseFolders) {
                        val filterDescription = filters.joinToString(", ") { ".$it" }
                        descriptor.withExtensionFilter(
                            filterDescription,
                            *filters.toTypedArray(),
                        )
                    }
                    val browseField = TextFieldWithBrowseButton()
                    browseField.text = initialPath
                    browseField.addBrowseFolderListener(project, descriptor)
                    browseField.textField.document.addDocumentListener(object : DocumentAdapter() {
                        override fun textChanged(e: DocumentEvent) {
                            activeServerErrors.remove(field.id)
                        }
                    })
                    fieldComponents[field.id] = browseField
                    focusTargets[field.id] = browseField.textField
                    builder.addLabeledComponent("${field.description}:", browseField)
                }

                FormFieldType.KIND_NUMBER -> {
                    val defaultNumberText = when (val def = field.defaultValue) {
                        is Number -> if (def is Double && def == def.toLong().toDouble()) def.toLong().toString() else def.toString()
                        is String -> def
                        else -> ""
                    }
                    val textField = JBTextField(defaultNumberText, 25)
                    textField.document.addDocumentListener(object : DocumentAdapter() {
                        override fun textChanged(e: DocumentEvent) {
                            activeServerErrors.remove(field.id)
                        }
                    })
                    fieldComponents[field.id] = textField
                    focusTargets[field.id] = textField
                    builder.addLabeledComponent("${field.description}:", textField)
                }

                else -> {
                    val defaultText = (field.defaultValue as? String).orEmpty()
                    val textField = JBTextField(defaultText, 25)
                    textField.document.addDocumentListener(object : DocumentAdapter() {
                        override fun textChanged(e: DocumentEvent) {
                            activeServerErrors.remove(field.id)
                        }
                    })
                    fieldComponents[field.id] = textField
                    focusTargets[field.id] = textField
                    builder.addLabeledComponent("${field.description}:", textField)
                }
            }
        }
        return builder.panel
    }

    public override fun getPreferredFocusedComponent(): JComponent? {
        return focusTargets.values.firstOrNull()
    }

    @VisibleForTesting
    internal fun collectRawValues(): Map<String, Any?> {
        val values = linkedMapOf<String, Any?>()
        for (field in fields) {
            val component = fieldComponents[field.id]
            values[field.id] = when (component) {
                is JBCheckBox -> component.isSelected
                is TextFieldWithBrowseButton -> component.text
                is JBTextField -> component.text
                else -> null
            }
        }
        return values
    }

    public override fun doValidateAll(): List<ValidationInfo> {
        val rawValues = collectRawValues()
        val results = mutableListOf<ValidationInfo>()
        val basePath = project.basePath
        for (field in fields) {
            val focusTarget = focusTargets[field.id] ?: fieldComponents[field.id]
            val info = DartLspCommandsSupport.validateFieldInput(
                field = field,
                rawValue = rawValues[field.id],
                activeServerError = activeServerErrors[field.id],
                component = focusTarget,
                basePath = basePath,
            )
            if (info != null) {
                results.add(info)
            }
        }
        return results
    }

    public override fun doOKAction() {
        val blockingErrors = doValidateAll().filter { !it.warning }
        if (blockingErrors.isNotEmpty()) {
            updateErrorInfo(blockingErrors)
            return
        }

        val answers = DartLspCommandsSupport.collectFormAnswers(fields, collectRawValues(), project.basePath)
        val requestParams = currentParams.copy(formAnswers = answers)
        val resolveResult = DartLspCommandsSupport.resolveCommandWithProgress(
            project = project,
            server = server,
            progressTitle = dialogTitle,
            params = requestParams,
        )
        if (resolveResult.cancelled) {
            return
        }
        val nextResolved = resolveResult.params
        if (nextResolved == null) {
            setErrorText(DartBundle.message("dart.refactoring.lsp.no.response"))
            return
        }

        if (nextResolved.formFields.isNullOrEmpty()) {
            finalResolvedParams = nextResolved
            super.doOKAction()
            return
        }

        currentParams = nextResolved
        fields = nextResolved.formFields
        activeServerErrors.clear()
        for (returnedField in nextResolved.formFields) {
            if (!returnedField.error.isNullOrBlank()) {
                activeServerErrors[returnedField.id] = returnedField.error
            }
        }
        val updatedValidations = doValidateAll()
        if (updatedValidations.isNotEmpty()) {
            updateErrorInfo(updatedValidations)
        }
    }
}
