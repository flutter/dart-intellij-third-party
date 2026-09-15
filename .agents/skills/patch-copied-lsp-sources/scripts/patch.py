#!/usr/bin/env python3
import argparse
import os
import re
import shutil
import sys

def main():
    parser = argparse.ArgumentParser(description="Copy and patch JetBrains LSP sources into the Dart plugin.")
    parser.add_argument(
        "intellij_path",
        nargs="?",
        help="Path to the local clone of the 'intellij-community' repository. If provided, sources will be copied first."
    )
    parser.add_argument(
        "--repo-root",
        help="Path to the dart-intellij-third-party repository root (defaults to resolving relative to this script)."
    )
    args = parser.parse_args()

    # Resolve base_dir
    if args.repo_root:
        base_dir = os.path.abspath(args.repo_root)
    else:
        # Default to the repository root relative to this script's location
        script_dir = os.path.dirname(os.path.abspath(__file__))
        repo_root = os.path.abspath(os.path.join(script_dir, "../../../../"))
        if os.path.isdir(os.path.join(repo_root, "third_party")):
            base_dir = repo_root
        else:
            base_dir = os.getcwd()

    if not os.path.isdir(os.path.join(base_dir, "third_party")):
        print(f"Error: {base_dir} does not contain a 'third_party' folder.", file=sys.stderr)
        sys.exit(1)

    # 1. Copy sources if intellij_path is provided
    if args.intellij_path:
        intellij_community_path = os.path.abspath(args.intellij_path)
        print(f"Copying sources from: {intellij_community_path}")

        src_lsp_code_old = os.path.join(intellij_community_path, "platform/lsp/src/com/intellij/platform/lsp")
        src_lsp_code_new = os.path.join(intellij_community_path, "platform/lsp/src")
        src_lsp_impl_code_new = os.path.join(intellij_community_path, "platform/lsp-impl/src")
        src_lsp_resources = os.path.join(intellij_community_path, "platform/lsp/resources")
        src_lsp_impl_resources = os.path.join(intellij_community_path, "platform/lsp-impl/resources")

        dst_lsp_code = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp")
        dst_lsp_resources = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/resources")

        if os.path.isdir(src_lsp_code_old) and os.path.isdir(src_lsp_resources):
            # Clean existing destination directories
            if os.path.exists(dst_lsp_code):
                shutil.rmtree(dst_lsp_code)
            if os.path.exists(dst_lsp_resources):
                shutil.rmtree(dst_lsp_resources)

            shutil.copytree(src_lsp_code_old, dst_lsp_code)
            shutil.copytree(src_lsp_resources, dst_lsp_resources)
        elif os.path.isdir(src_lsp_code_new) and os.path.isdir(src_lsp_impl_code_new):
            if os.path.exists(dst_lsp_code):
                shutil.rmtree(dst_lsp_code)
            if os.path.exists(dst_lsp_resources):
                shutil.rmtree(dst_lsp_resources)

            shutil.copytree(src_lsp_code_new, dst_lsp_code, dirs_exist_ok=True)
            shutil.copytree(src_lsp_impl_code_new, dst_lsp_code, dirs_exist_ok=True)
            if os.path.isdir(src_lsp_resources):
                shutil.copytree(src_lsp_resources, dst_lsp_resources, dirs_exist_ok=True)
            if os.path.isdir(src_lsp_impl_resources):
                shutil.copytree(src_lsp_impl_resources, dst_lsp_resources, dirs_exist_ok=True)
        else:
            print(f"Error: Could not find LSP sources in {intellij_community_path}.", file=sys.stderr)
            print(f"Expected to find:\n  {src_lsp_code_old} (or {src_lsp_code_new} + {src_lsp_impl_code_new})", file=sys.stderr)
            sys.exit(1)

        # Rename intellij.platform.lsp.xml to dart-lsp-impl.xml
        xml_old_path = os.path.join(dst_lsp_resources, "META-INF/intellij.platform.lsp.xml")
        if not os.path.exists(xml_old_path):
            xml_old_path = os.path.join(dst_lsp_resources, "intellij.platform.lsp.xml")

        xml_new_path = os.path.join(dst_lsp_resources, "dart-lsp-impl.xml")
        if os.path.exists(xml_old_path):
            os.rename(xml_old_path, xml_new_path)
            # Clean up META-INF if empty
            meta_inf_dir = os.path.dirname(xml_old_path)
            if os.path.exists(meta_inf_dir) and not os.listdir(meta_inf_dir):
                os.rmdir(meta_inf_dir)


    print(f"Applying JetBrains LSP patches and renames in: {base_dir}")

    # 2. Rename package references in all files under platform-lsp
    walk_dir = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp")
    for root, dirs, files in os.walk(walk_dir):
        for file in files:
            if file.endswith((".kt", ".java", ".xml")):
                file_path = os.path.join(root, file)
                with open(file_path, "r", encoding="utf-8") as f:
                    content = f.read()
                
                # Check for the original platform package name
                modified = False
                if "com.intellij.platform.lsp" in content:
                    content = content.replace("com.intellij.platform.lsp", "com.intellij.platform.dartlsp")
                    modified = True
                
                # Also fallback to replacing any residual com.intellij.platform.lsp.dart just in case
                if "com.intellij.platform.lsp.dart" in content:
                    content = content.replace("com.intellij.platform.lsp.dart", "com.intellij.platform.dartlsp")
                    modified = True

                if modified:
                    with open(file_path, "w", encoding="utf-8") as f:
                        f.write(content)

    # 3. Modify dart-lsp-impl.xml
    xml_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/resources/dart-lsp-impl.xml")
    if os.path.exists(xml_path):
        with open(xml_path, "r", encoding="utf-8") as f:
            xml_content = f.read()

        xml_content = xml_content.replace(
            'key="lsp.server.connect.timeout"',
            'key="dart.lsp.server.connect.timeout"'
        )
        xml_content = re.sub(
            r'<notificationGroup id="LSP window/',
            r'<notificationGroup id="Dart: LSP window/',
            xml_content
        )
        xml_content = xml_content.replace(
            'bundle="messages.LspBundle"',
            'bundle="messages.DartLspBundle"'
        )
        xml_content = xml_content.replace(
            '<bundleName>messages.LspBundle</bundleName>',
            '<bundleName>messages.DartLspBundle</bundleName>'
        )
        xml_content = xml_content.replace(
            'key="lsp.communication.standard.log.file" defaultValue="true"',
            'key="lsp.communication.standard.log.file" defaultValue="false"'
        )
        xml_content = xml_content.replace(
            '<descriptionDirectoryName>LspIntention</descriptionDirectoryName>',
            '<descriptionDirectoryName>DartLspIntention</descriptionDirectoryName>'
        )

        def remove_ids(match):
            tag_content = match.group(0)
            if tag_content.startswith("<notificationGroup"):
                return tag_content
            return re.sub(r'\s+id="[^"]*"', '', tag_content)

        xml_content = re.sub(r'<[a-zA-Z0-9_\.]+(?:\s+[^>]*?)?\s+id="[^"]*"[^>]*>', remove_ids, xml_content)

        with open(xml_path, "w", encoding="utf-8") as f:
            f.write(xml_content)

    # 4. Modify Lsp4jServerConnector.kt
    connector_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/connector/Lsp4jServerConnector.kt")
    if os.path.exists(connector_path):
        with open(connector_path, "r", encoding="utf-8") as f:
            connector_content = f.read()
        connector_content = connector_content.replace(
            '"lsp.server.connect.timeout"',
            '"dart.lsp.server.connect.timeout"'
        )
        with open(connector_path, "w", encoding="utf-8") as f:
            f.write(connector_content)

    # 5. Modify LspServerNotificationsHandlerImpl.kt
    handler_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/LspServerNotificationsHandlerImpl.kt")
    if os.path.exists(handler_path):
        with open(handler_path, "r", encoding="utf-8") as f:
            handler_content = f.read()
        handler_content = handler_content.replace(
            '"LSP window/showMessage"',
            '"Dart: LSP window/showMessage"'
        )
        handler_content = handler_content.replace(
            '"LSP window/logMessage: errors, warnings"',
            '"Dart: LSP window/logMessage: errors, warnings"'
        )
        handler_content = handler_content.replace(
            '"LSP window/logMessage: info, log; $/logTrace"',
            '"Dart: LSP window/logMessage: info, log; $/logTrace"'
        )
        with open(handler_path, "w", encoding="utf-8") as f:
            f.write(handler_content)

    # 6. Modify LspBundle.kt
    bundle_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/api/LspBundle.kt")
    if os.path.exists(bundle_path):
        with open(bundle_path, "r", encoding="utf-8") as f:
            bundle_content = f.read()
        bundle_content = bundle_content.replace(
            '"messages.LspBundle"',
            '"messages.DartLspBundle"'
        )
        with open(bundle_path, "w", encoding="utf-8") as f:
            f.write(bundle_content)

    # 7. Rename intentionDescriptions directory
    src_intention_dir = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/resources/intentionDescriptions/LspIntention")
    dst_intention_dir = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/resources/intentionDescriptions/DartLspIntention")
    if os.path.exists(src_intention_dir):
        if os.path.exists(dst_intention_dir):
            shutil.rmtree(dst_intention_dir)
        os.rename(src_intention_dir, dst_intention_dir)

    # 8. Write messages/DartLspBundle.properties
    properties_dir = os.path.join(base_dir, "third_party/src/main/resources/messages")
    os.makedirs(properties_dir, exist_ok=True)
    properties_path = os.path.join(properties_dir, "DartLspBundle.properties")

    properties_content = '''notification.group.lsp.message=LSP messages
notification.group.lsp.log.errors.warnings=LSP log: errors, warnings
notification.group.lsp.log.info.trace=LSP log: info, trace

lsp.based.formatter=LSP-based formatter
command.name.lsp.on.type.formatting=On-Type Formatting

intention.group.name=LSP
intention.family.name=Apply code action

code.change.from.server={0}: code change from the LSP server

action.OpenLspErrorOutputAction.text=Open Server Error Output
server.error.output.editor.tab.name={0} Server Error Output
action.StopLspServerAction.text=Stop and Autorun Server as Needed
action.RestartLspServerAction.text=Restart Server

# Example: "Dart: Find References (foo.dart:12:3)"
0.find.references.1={0}: Find References ({1})
follow.link.tooltip=Follow link
progress.title.progress={0}: progress
codeLens.LspCodeVisionProvider.name=LSP Code Lens
codeLens.LspCodeVisionProvider.description=Displays code lens information from LSP servers

lsp.rename.prepare.progress.title=Preparing Rename\\u2026
lsp.rename.progress.title=Renaming\\u2026
lsp.rename.action.text=LSP-Based Rename
'''

    with open(properties_path, "w", encoding="utf-8") as f:
        f.write(properties_content)

    # 9. Modify LspCodeVisionProvider.kt to explicitly override handleExtraAction and use unique provider ID
    code_vision_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/features/codeLens/LspCodeVisionProvider.kt")
    if os.path.exists(code_vision_path):
        with open(code_vision_path, "r", encoding="utf-8") as f:
            code_vision_content = f.read()
        modified = False
        if "handleExtraAction" not in code_vision_content:
            code_vision_content = code_vision_content.replace(
                "override val singleEntryPerLine: Boolean = false\n}",
                "override val singleEntryPerLine: Boolean = false\n\n  override fun handleExtraAction(editor: Editor, textRange: TextRange, entry: CodeVisionEntry, actionId: String) {}\n}"
            )
            modified = True
        if 'LSP_CODE_VISION_PROVIDER_ID: String = "LspCodeVisionProvider"' in code_vision_content:
            code_vision_content = code_vision_content.replace(
                'LSP_CODE_VISION_PROVIDER_ID: String = "LspCodeVisionProvider"',
                'LSP_CODE_VISION_PROVIDER_ID: String = "DartLspCodeVisionProvider"'
            )
            modified = True
        if modified:
            with open(code_vision_path, "w", encoding="utf-8") as f:
                f.write(code_vision_content)

    # 10. Modify LspServerImpl.kt to remove ProjectFileIndex.isInContent check so external library files (pub-cache, SDK) are supported
    lsp_server_impl_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/LspServerImpl.kt")
    if os.path.exists(lsp_server_impl_path):
        with open(lsp_server_impl_path, "r", encoding="utf-8") as f:
            lsp_server_impl_content = f.read()
        target_line = "    if (!ProjectFileIndex.getInstance(project).isInContent(file)) return false\n"
        import_line = "import com.intellij.openapi.roots.ProjectFileIndex\n"
        modified = False
        if target_line in lsp_server_impl_content:
            lsp_server_impl_content = lsp_server_impl_content.replace(target_line, "")
            modified = True
        if import_line in lsp_server_impl_content:
            lsp_server_impl_content = lsp_server_impl_content.replace(import_line, "")
            modified = True
        if modified:
            with open(lsp_server_impl_path, "w", encoding="utf-8") as f:
                f.write(lsp_server_impl_content)

    # 11. Apply lsp4j 0.x / 1.0.0 cross-version compatibility patches
    # 11a. Lsp4jUtil.kt
    lsp4j_util_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/util/Lsp4jUtil.kt")
    if os.path.exists(lsp4j_util_path):
        with open(lsp4j_util_path, "r", encoding="utf-8") as f:
            util_content = f.read()

        required_imports = [
            "import com.intellij.openapi.util.NlsSafe",
            "import java.lang.reflect.Method",
            "import org.eclipse.lsp4j.Diagnostic",
            "import org.eclipse.lsp4j.DocumentFilter",
            "import org.eclipse.lsp4j.jsonrpc.messages.Either",
        ]
        for imp in required_imports:
            if imp not in util_content:
                util_content = util_content.replace(
                    "import com.intellij.openapi.editor.Document\n",
                    f"import com.intellij.openapi.editor.Document\n{imp}\n"
                )

        # Remove upstream 263 SnippetTextEdit import/usage if present
        util_content = util_content.replace("import org.eclipse.lsp4j.SnippetTextEdit\n", "")
        util_content = util_content.replace("import org.eclipse.lsp4j.MarkupContent\n", "")

        # Update applyTextEdits to safely unwrap Either<TextEdit, SnippetTextEdit> at runtime on lsp4j 1.0.0
        old_apply_edits = """fun applyTextEdits(document: Document, textEdits: List<TextEdit>): Boolean {
  // Spec:
  // > All text edits ranges refer to positions in the document they are computed on. Text edits ranges must never overlap.
  // > However, it is possible that multiple edits have the same start position: multiple inserts, ...
  // > If multiple inserts have the same position, the order in the array defines the order in which the inserted strings appear in the resulting text.
  //
  // The edits must be applied from bottom to top.
  // Edits that have the same position must be applied in the reversed order - this way the resulting text will get inserted strings in the original order.
  textEdits
    .sortedWith"""
        new_apply_edits = """fun applyTextEdits(document: Document, textEdits: List<TextEdit>): Boolean {
  val unwrappedEdits = ArrayList<TextEdit>(textEdits.size)
  for (item in textEdits as List<*>) {
    when (item) {
      is TextEdit -> unwrappedEdits.add(item)
      is Either<*, *> -> {
        val textEdit = item.left as? TextEdit
        if (textEdit == null) {
          fileLogger().warn("Ignoring SnippetTextEdit, the IDE does not support it: ${item.right}")
          return false
        }
        unwrappedEdits.add(textEdit)
      }
    }
  }
  // Spec:
  // > All text edits ranges refer to positions in the document they are computed on. Text edits ranges must never overlap.
  // > However, it is possible that multiple edits have the same start position: multiple inserts, ...
  // > If multiple inserts have the same position, the order in the array defines the order in which the inserted strings appear in the resulting text.
  //
  // The edits must be applied from bottom to top.
  // Edits that have the same position must be applied in the reversed order - this way the resulting text will get inserted strings in the original order.
  unwrappedEdits
    .sortedWith"""
        if old_apply_edits in util_content:
            util_content = util_content.replace(old_apply_edits, new_apply_edits)

        # Remove upstream 263 non-reflective messageIfStringOrEmpty if present
        upstream_message_prop = """val Diagnostic.messageIfStringOrEmpty: @NlsSafe String
  get() = message.map({ it }, { "" })"""
        if upstream_message_prop in util_content:
            util_content = util_content.replace(upstream_message_prop, "")

        compat_helpers = """
private val diagnosticGetMessageMethod: Method by lazy {
  Diagnostic::class.java.getMethod("getMessage")
}

private val documentFilterGetPatternMethod: Method by lazy {
  DocumentFilter::class.java.getMethod("getPattern")
}

/**
 * Compatible accessor for [Diagnostic.getMessage] across lsp4j 0.x (returns `String`)
 * and lsp4j 1.0.0+ (returns `Either<String, MarkupContent>`).
 */
val Diagnostic.messageIfStringOrEmpty: @NlsSafe String
  get() = when (val raw = diagnosticGetMessageMethod.invoke(this)) {
    is String -> raw
    is Either<*, *> -> (raw.left as? String) ?: ""
    else -> ""
  }

/**
 * Compatible accessor for [DocumentFilter.getPattern] across lsp4j 0.x (returns `String?`)
 * and lsp4j 1.0.0+ (returns `Either<String, RelativePattern>?`).
 */
fun getDocumentFilterPattern(filter: DocumentFilter): Either<String, Any>? {
  return when (val raw = documentFilterGetPatternMethod.invoke(filter)) {
    null -> null
    is String -> Either.forLeft(raw)
    is Either<*, *> -> {
      if (raw.isLeft) {
        (raw.left as? String)?.let { Either.forLeft(it) }
      }
      else {
        raw.right?.let { Either.forRight(it) }
      }
    }
    else -> null
  }
}
"""
        if "diagnosticGetMessageMethod" not in util_content:
            util_content = util_content.rstrip() + "\n" + compat_helpers
        with open(lsp4j_util_path, "w", encoding="utf-8") as f:
            f.write(util_content)

    # 11b. LspDiagnosticsCustomizer.kt
    diag_customizer_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/api/customization/LspDiagnosticsCustomizer.kt")
    if os.path.exists(diag_customizer_path):
        with open(diag_customizer_path, "r", encoding="utf-8") as f:
            dc_content = f.read()
        imp = "import com.intellij.platform.dartlsp.util.messageIfStringOrEmpty"
        if imp not in dc_content:
            dc_content = dc_content.replace(
                "import com.intellij.openapi.vfs.VirtualFile\n",
                f"import com.intellij.openapi.vfs.VirtualFile\n{imp}\n"
            )
        dc_content = dc_content.replace(
            "open fun getMessage(diagnostic: Diagnostic): String = diagnostic.message\n",
            "open fun getMessage(diagnostic: Diagnostic): String = diagnostic.messageIfStringOrEmpty\n"
        )
        dc_content = dc_content.replace(
            "open fun getTooltip(diagnostic: Diagnostic): String = diagnostic.message\n",
            "open fun getTooltip(diagnostic: Diagnostic): String = diagnostic.messageIfStringOrEmpty\n"
        )
        with open(diag_customizer_path, "w", encoding="utf-8") as f:
            f.write(dc_content)

    # 11c. LspDiagnosticAndLazyQuickFixes.kt
    diag_qf_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/features/highlighting/LspDiagnosticAndLazyQuickFixes.kt")
    if os.path.exists(diag_qf_path):
        with open(diag_qf_path, "r", encoding="utf-8") as f:
            qf_content = f.read()
        imp = "import com.intellij.platform.dartlsp.util.messageIfStringOrEmpty"
        if imp not in qf_content:
            qf_content = qf_content.replace(
                "import com.intellij.openapi.vfs.VirtualFile\n",
                f"import com.intellij.openapi.vfs.VirtualFile\n{imp}\n"
            )
        qf_content = qf_content.replace(
            "this.message = diagnostic.message\n",
            "this.message = diagnostic.messageIfStringOrEmpty\n"
        )
        with open(diag_qf_path, "w", encoding="utf-8") as f:
            f.write(qf_content)

    # 11d. LspServerImpl.kt
    if os.path.exists(lsp_server_impl_path):
        with open(lsp_server_impl_path, "r", encoding="utf-8") as f:
            server_content = f.read()
        imp = "import com.intellij.platform.dartlsp.util.getDocumentFilterPattern"
        if imp not in server_content:
            server_content = server_content.replace(
                "import com.intellij.openapi.vfs.VirtualFile\n",
                f"import com.intellij.openapi.vfs.VirtualFile\n{imp}\n"
            )
        old_pattern_block = """        val language = filter.language
        val pattern = filter.pattern"""
        new_pattern_block = """        val language = filter.language
        val filterPattern = getDocumentFilterPattern(filter)
        if (filterPattern != null && filterPattern.isRight) {
          // A RelativePattern needs its baseUri resolved against the workspace folders. The IDE does not support it yet.
          logWarn("Ignoring the document filter, its pattern is relative: ${filterPattern.right}")
          continue
        }
        val pattern = filterPattern?.left"""
        if old_pattern_block in server_content:
            server_content = server_content.replace(old_pattern_block, new_pattern_block)
        with open(lsp_server_impl_path, "w", encoding="utf-8") as f:
            f.write(server_content)
    # 12. Modify LspStructureViewSupport.kt to return nullable List<DocumentSymbol>? so callers can distinguish failure (null) from empty list
    structure_view_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/features/documentSymbol/LspStructureViewSupport.kt")
    if os.path.exists(structure_view_path):
        with open(structure_view_path, "r", encoding="utf-8") as f:
            structure_view_content = f.read()
        target_symbols_line = "fun getDocumentSymbols(): List<DocumentSymbol> = lspServer.requestExecutor.getDocumentSymbolsCaching(file).orEmpty()"
        replacement_symbols_line = "fun getDocumentSymbols(): List<DocumentSymbol>? = lspServer.requestExecutor.getDocumentSymbolsCaching(file)"
        if target_symbols_line in structure_view_content:
            structure_view_content = structure_view_content.replace(target_symbols_line, replacement_symbols_line)
            with open(structure_view_path, "w", encoding="utf-8") as f:
                f.write(structure_view_content)

    print("Patch applied successfully!")

if __name__ == "__main__":
    main()

