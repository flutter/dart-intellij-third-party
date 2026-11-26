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
        src_lsp_structure_view_code = os.path.join(intellij_community_path, "platform/lsp-impl/structureView/src")
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
            if os.path.isdir(src_lsp_structure_view_code):
                shutil.copytree(src_lsp_structure_view_code, dst_lsp_code, dirs_exist_ok=True)
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

        structure_view_exts = (
            '    <postStartupActivity implementation="com.intellij.platform.dartlsp.impl.structureView.LspStructureViewProjectActivity"/>'
        )
        if "LspStructureViewProjectActivity" not in xml_content:
            xml_content = xml_content.replace(
                'order="last, after PsiFileBreadcrumbsCollector"/>',
                'order="last, after PsiFileBreadcrumbsCollector"/>\n' + structure_view_exts
            )

        xml_content = re.sub(
            r'\s*<lang\.psiStructureViewFactory\s+language=""\s+implementationClass="com\.intellij\.platform\.dartlsp\.impl\.structureView\.LspStructureViewFactory"/>',
            '',
            xml_content
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

    # 12. Make LspStructureViewFactory public so DartStructureViewFactory (Java) can delegate to it,
    # and load/cache documentSymbols in LspStructureViewRoot.getChildren() off the EDT.
    structure_view_factory_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/structureView/LspStructureViewFactory.kt")
    if os.path.exists(structure_view_factory_path):
        with open(structure_view_factory_path, "r", encoding="utf-8") as f:
            factory_content = f.read()
        app_manager_import = "import com.intellij.openapi.application.ApplicationManager"
        if app_manager_import not in factory_content:
            factory_content = factory_content.replace(
                "import com.intellij.navigation.ItemPresentation\n",
                f"import com.intellij.navigation.ItemPresentation\n{app_manager_import}\n"
            )
        factory_content = factory_content.replace(
            "internal class LspStructureViewFactory",
            "class LspStructureViewFactory"
        )
        factory_content = factory_content.replace(
            "root = LspStructureViewRoot(support, psiFile, support.getDocumentSymbols())",
            "root = LspStructureViewRoot(support, psiFile)"
        )
        old_root_block = """private class LspStructureViewRoot(
  private val support: LspStructureViewSupport,
  private val psiFile: PsiFile,
  private val documentSymbols: List<DocumentSymbol>,
) : StructureViewTreeElement {
  override fun getValue(): PsiFile = psiFile

  override fun getChildren(): Array<LspDocumentSymbolTreeElement> {
    return documentSymbols.map { LspDocumentSymbolTreeElement(support, it) }.toTypedArray()
  }"""
        new_root_block = """private class LspStructureViewRoot(
  private val support: LspStructureViewSupport,
  private val psiFile: PsiFile,
) : StructureViewTreeElement {
  @Volatile
  private var cachedChildren: Array<LspDocumentSymbolTreeElement>? = null

  override fun getValue(): PsiFile = psiFile

  override fun getChildren(): Array<LspDocumentSymbolTreeElement> {
    cachedChildren?.let { return it }
    if (ApplicationManager.getApplication().isDispatchThread) {
      return emptyArray()
    }

    val symbols = support.getDocumentSymbols()
    val children = symbols.map { LspDocumentSymbolTreeElement(support, it) }.toTypedArray()
    if (symbols.isNotEmpty()) {
      cachedChildren = children
    }
    return children
  }"""
        if old_root_block in factory_content:
            factory_content = factory_content.replace(old_root_block, new_root_block)
        with open(structure_view_factory_path, "w", encoding="utf-8") as f:
            f.write(factory_content)
    # 13. Make CurrentActionHolder internal in LspImplicitReferenceProvider.kt for tests
    implicit_ref_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/features/navigation/LspImplicitReferenceProvider.kt")
    if os.path.exists(implicit_ref_path):
        with open(implicit_ref_path, "r", encoding="utf-8") as f:
            implicit_ref_content = f.read()
        if "private class CurrentActionHolder" in implicit_ref_content:
            implicit_ref_content = implicit_ref_content.replace(
                "private class CurrentActionHolder",
                "internal class CurrentActionHolder"
            )
            with open(implicit_ref_path, "w", encoding="utf-8") as f:
                f.write(implicit_ref_content)
    # 14. Guard StickyLineInfo construction in LspFileBreadcrumbsCollector (#734 / IJPL-260061) against
    # incomplete declarations where selectionRange.start >= range.end (textOffset >= endOffset).
    breadcrumbs_collector_path = os.path.join(
        base_dir,
        "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/features/documentSymbol/LspFileBreadcrumbsCollector.kt"
    )
    if os.path.exists(breadcrumbs_collector_path):
        with open(breadcrumbs_collector_path, "r", encoding="utf-8") as f:
            breadcrumbs_content = f.read()
        old_sticky_block = """      val endOffset = getOffsetInDocument(document, symbol.range.end)
                      ?: return@mapNotNull null
      StickyLineInfo(textOffset, endOffset, null)"""
        new_sticky_block = """      val endOffset = getOffsetInDocument(document, symbol.range.end)
                      ?: return@mapNotNull null
      // Dart patch: skip incomplete declarations (#734 / IJPL-260061)
      if (textOffset >= endOffset) return@mapNotNull null
      StickyLineInfo(textOffset, endOffset, null)"""
        if old_sticky_block in breadcrumbs_content:
            breadcrumbs_content = breadcrumbs_content.replace(old_sticky_block, new_sticky_block)
            with open(breadcrumbs_collector_path, "w", encoding="utf-8") as f:
                f.write(breadcrumbs_content)

    # 15. Make Lsp4jClient.publishDiagnostics open for subclassing in DartLspServerDescriptor
    # See https://github.com/flutter/flutter-intellij/issues/9239
    lsp4j_client_path = os.path.join(base_dir, "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/api/Lsp4jClient.kt")
    if os.path.exists(lsp4j_client_path):
        with open(lsp4j_client_path, "r", encoding="utf-8") as f:
            lsp4j_client_content = f.read()
        old_pub_diag = "  final override fun publishDiagnostics(params: PublishDiagnosticsParams): Unit ="
        new_pub_diag = "  // Dart patch: make publishDiagnostics open for DartLsp4jClient (https://github.com/flutter/flutter-intellij/issues/9239)\n  override fun publishDiagnostics(params: PublishDiagnosticsParams): Unit ="
        if old_pub_diag in lsp4j_client_content:
            lsp4j_client_content = lsp4j_client_content.replace(old_pub_diag, new_pub_diag)
            with open(lsp4j_client_path, "w", encoding="utf-8") as f:
                f.write(lsp4j_client_content)

    # 16. Backport IJPL-235332 (47bd3c0a) so LspImportOptimizer supports command-based source.organizeImports
    # 16a. LspServerImpl.kt
    if os.path.exists(lsp_server_impl_path):
        with open(lsp_server_impl_path, "r", encoding="utf-8") as f:
            server_content = f.read()
        ijpl_235332_imports = [
            "import com.intellij.openapi.progress.ProgressManager",
            "import com.intellij.openapi.progress.util.ProgressIndicatorUtils",
            "import com.intellij.util.concurrency.Semaphore",
            "import kotlinx.coroutines.launch",
            "import org.eclipse.lsp4j.Command",
            "import org.eclipse.lsp4j.ExecuteCommandParams",
            "import org.eclipse.lsp4j.WorkspaceEdit",
        ]
        for imp in ijpl_235332_imports:
            if imp not in server_content:
                server_content = server_content.replace(
                    "import com.intellij.openapi.project.Project\n",
                    f"import com.intellij.openapi.project.Project\n{imp}\n"
                )
        server_content = server_content.replace(
            "internal val serverNotificationsHandler: LspServerNotificationsHandler = LspServerNotificationsHandlerImpl(this)",
            "internal val serverNotificationsHandler: LspServerNotificationsHandlerImpl = LspServerNotificationsHandlerImpl(this)"
        )
        old_is_supported = """    return descriptor.isSupportedFile(file)
      .also { if (!it) unsupportedFilePaths.add(file.path) }
  }"""
        new_is_supported = """    return descriptor.isSupportedFile(file)
      .also { if (!it) unsupportedFilePaths.add(file.path) }
  }

  /**
   * Sends a [workspace/executeCommand](https://microsoft.github.io/language-server-protocol/specification/#workspace_executeCommand)
   * request to the server and waits for whichever comes first:
   *  - a `workspace/applyEdit` request from the server - in this case this function returns the corresponding [WorkspaceEdit],
   *  and it is up to the caller to apply it;
   *  - a response to the `executeCommand` request (any response, including an error) - in this case this function returns `null`.
   *
   * Waiting is cancellable, so this function is safe to call inside a cancellable read action.
   *
   * Backported from IJPL-235332 (commit 47bd3c0a).
   */
  @RequiresBackgroundThread
  internal fun executeCommandExpectingWorkspaceEdit(command: Command): WorkspaceEdit? {
    // Released as soon as either the server sends a `workspace/applyEdit` request or the `executeCommand` request gets a response.
    val semaphore = Semaphore(1)
    var workspaceEdit: WorkspaceEdit? = null

    try {
      serverNotificationsHandler.nextApplyEditHandler = { edit ->
        workspaceEdit = edit
        semaphore.up()
      }

      LspServerManagerImpl.getInstanceImpl(project).cs.launch {
        try {
          sendRequest { it.workspaceService.executeCommand(ExecuteCommandParams(command.command, command.arguments)) }
        }
        finally {
          semaphore.up()
        }
      }

      @Suppress("UsagesOfObsoleteApi")
      ProgressIndicatorUtils.awaitWithCheckCanceled(semaphore, ProgressManager.getInstance().progressIndicator)
    }
    finally {
      serverNotificationsHandler.nextApplyEditHandler = null
    }

    return workspaceEdit
  }"""
        if "executeCommandExpectingWorkspaceEdit" not in server_content and old_is_supported in server_content:
            server_content = server_content.replace(old_is_supported, new_is_supported)
        with open(lsp_server_impl_path, "w", encoding="utf-8") as f:
            f.write(server_content)

    # 16b. LspServerNotificationsHandlerImpl.kt
    if os.path.exists(handler_path):
        with open(handler_path, "r", encoding="utf-8") as f:
            handler_content = f.read()
        if "import org.eclipse.lsp4j.WorkspaceEdit\n" not in handler_content:
            handler_content = handler_content.replace(
                "import org.eclipse.lsp4j.WorkspaceFolder\n",
                "import org.eclipse.lsp4j.WorkspaceEdit\nimport org.eclipse.lsp4j.WorkspaceFolder\n"
            )
        old_apply_edit = """  private val ansiDecoder = AnsiEscapeDecoder()

  override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> {
    val future = CompletableFuture<ApplyWorkspaceEditResponse>()"""
        new_apply_edit = """  private val ansiDecoder = AnsiEscapeDecoder()

  /**
   * When set (by [LspServerImpl.executeCommandExpectingWorkspaceEdit]), incoming `workspace/applyEdit` requests are handled by
   * this handler instead of being applied in the usual way. Written and read on different threads, hence `@Volatile`.
   *
   * Backported from IJPL-235332 (commit 47bd3c0a).
   */
  @Volatile
  internal var nextApplyEditHandler: ((WorkspaceEdit) -> Unit)? = null

  override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> {
    nextApplyEditHandler?.let { handler ->
      nextApplyEditHandler = null
      handler(params.edit)
      // a bit of cheating here: at this moment the edit is not yet applied; there are no known issues because of that
      return completedFuture(ApplyWorkspaceEditResponse(true))
    }

    val future = CompletableFuture<ApplyWorkspaceEditResponse>()"""
        if "nextApplyEditHandler" not in handler_content and old_apply_edit in handler_content:
            handler_content = handler_content.replace(old_apply_edit, new_apply_edit)
        with open(handler_path, "w", encoding="utf-8") as f:
            f.write(handler_content)

    # 16c. LspImportOptimizer.kt
    import_optimizer_path = os.path.join(
        base_dir,
        "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/features/formatter/LspImportOptimizer.kt"
    )
    if os.path.exists(import_optimizer_path):
        with open(import_optimizer_path, "r", encoding="utf-8") as f:
            optimizer_content = f.read()
        optimizer_imports = [
            "import com.intellij.platform.dartlsp.impl.LspServerImpl",
            "import com.intellij.platform.dartlsp.impl.features.intention.toCodeAction",
            "import com.intellij.platform.dartlsp.impl.util.LspWorkspaceEditApplier",
            "import org.eclipse.lsp4j.WorkspaceEdit",
        ]
        for imp in optimizer_imports:
            if imp not in optimizer_content:
                optimizer_content = optimizer_content.replace(
                    "import com.intellij.psi.PsiFile\n",
                    f"{imp}\nimport com.intellij.psi.PsiFile\n"
                )
        old_optimizer_body = """    val codeAction = requestCodeAction(lspServer, virtualFile) ?: return noResult
    val intentionAction = LspIntentionAction(lspServer, codeAction)
    if (!intentionAction.isAvailable { isReasonableOrganizeImportsAction(lspServer, virtualFile, it) }) return noResult

    return Runnable {
      intentionAction.invoke(virtualFile)
    }
  }

  private fun requestCodeAction(lspServer: LspServer, virtualFile: VirtualFile): CodeAction? {
    val codeActionContext = CodeActionContext().apply {
      diagnostics = emptyList()
      triggerKind = CodeActionTriggerKind.Invoked
      only = listOf(SourceOrganizeImports)
    }

    // Send code action request per document (cell). For regular files, there's one document.
    val results = lspServer.documentMapping.getDocumentsInFileSync(virtualFile).mapNotNull { lspDocument ->
      val params = CodeActionParams(
        lspDocument.id,
        Range(Position(0, 0), Position(0, 0)), // doesn't matter
        codeActionContext,
      )

      // Only one code action supported as UI does not suppose selection of the one import
      // option from the list (like it is processed in VS Code)
      lspServer.sendRequestSync { it.textDocumentService.codeAction(params) }?.singleOrNull()?.right
    }

    return results.firstOrNull()
  }

  private fun isReasonableOrganizeImportsAction(lspServer: LspServer, virtualFile: VirtualFile, codeAction: CodeAction): Boolean {
    val currentFileUri = lspServer.descriptor.getFileUri(virtualFile)
    if (codeAction.kind != SourceOrganizeImports) return false
    if (codeAction.command != null) return false // not expected for the Optimize Imports action
    val edit = codeAction.edit ?: return false
    if (edit.changes != null && edit.documentChanges != null) return false
    if (edit.changes.isNullOrEmpty() && edit.documentChanges.isNullOrEmpty()) return false
    if (edit.changes?.any { it.key != currentFileUri } == true) return false // other files editing is not expected
    if (edit.documentChanges?.any { it.isRight } == true) return false // ResourceOperation is not expected
    if (edit.documentChanges?.any { it.left?.textDocument?.uri != currentFileUri } == true) return false // other files editing is not expected
    return true
  }"""
        new_optimizer_body = """    val codeAction = requestCodeAction(lspServer, virtualFile) ?: return noResult

    val intentionAction = LspIntentionAction(lspServer, codeAction)
    if (!intentionAction.isAvailable()) return noResult

    val command = codeAction.command
    // If codeAction.edit is null or effectively a no-op, handle Command in a synchronous way (see IJPL-235332)
    if ((codeAction.edit == null || codeAction.edit == WorkspaceEdit()) && command != null) {
      val edit = (lspServer as LspServerImpl).executeCommandExpectingWorkspaceEdit(command) ?: return noResult
      val applier = LspWorkspaceEditApplier.create(lspServer, edit) ?: return noResult
      return Runnable {
        applier.applyWorkspaceEdit()
      }
    }

    return Runnable {
      intentionAction.invoke(virtualFile)
    }
  }

  private fun requestCodeAction(lspServer: LspServer, virtualFile: VirtualFile): CodeAction? {
    val codeActionContext = CodeActionContext().apply {
      diagnostics = emptyList()
      triggerKind = CodeActionTriggerKind.Invoked
      only = listOf(SourceOrganizeImports)
    }

    // Send code action request per document (cell). For regular files, there's one document.
    val results = lspServer.documentMapping.getDocumentsInFileSync(virtualFile).mapNotNull { lspDocument ->
      val params = CodeActionParams(
        lspDocument.id,
        Range(Position(0, 0), Position(0, 0)), // doesn't matter
        codeActionContext,
      )

      // Only one code action supported as UI does not suppose selection of the one import
      // option from the list (like it is processed in VS Code)
      lspServer.sendRequestSync { it.textDocumentService.codeAction(params) }?.singleOrNull()?.toCodeAction()
    }

    return results.firstOrNull()
  }"""
        if old_optimizer_body in optimizer_content:
            optimizer_content = optimizer_content.replace(old_optimizer_body, new_optimizer_body)
        with open(import_optimizer_path, "w", encoding="utf-8") as f:
            f.write(optimizer_content)

    # 16d. LspIntentionAction.kt
    intention_action_path = os.path.join(
        base_dir,
        "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/api/customization/LspIntentionAction.kt"
    )
    if os.path.exists(intention_action_path):
        with open(intention_action_path, "r", encoding="utf-8") as f:
            intention_content = f.read()
        old_is_available = """  @RequiresReadLock
  fun isAvailable(): Boolean = isAvailable { true }

  @RequiresReadLock
  fun isAvailable(codeActionValidator: (CodeAction) -> Boolean): Boolean {
    codeAction.disabled?.let { return false }

    resolveCodeAction()

    val workspaceEdit = codeAction.edit
    if (!uriToDocumentMapInitialized) {
      uriToDocumentMap = if (workspaceEdit != null) getUriToDocumentMap(workspaceEdit) else emptyMap()
      uriToDocumentMapInitialized = true
    }

    return uriToDocumentMap != null &&
           (workspaceEdit != null || codeAction.command != null) &&
           codeActionValidator(codeAction)
  }"""
        new_is_available = """  @RequiresReadLock
  fun isAvailable(): Boolean {
    codeAction.disabled?.let { return false }

    resolveCodeAction()

    val workspaceEdit = codeAction.edit
    if (workspaceEdit?.changes?.isNotEmpty() == true && workspaceEdit.documentChanges?.isNotEmpty() == true) return false

    if (!uriToDocumentMapInitialized) {
      uriToDocumentMap = if (workspaceEdit != null) getUriToDocumentMap(workspaceEdit) else emptyMap()
      uriToDocumentMapInitialized = true
    }

    return uriToDocumentMap != null &&
           (workspaceEdit != null || codeAction.command != null)
  }"""
        if old_is_available in intention_content:
            intention_content = intention_content.replace(old_is_available, new_is_available)
            with open(intention_action_path, "w", encoding="utf-8") as f:
                f.write(intention_content)

    # 17. LspRenameHandler: keep the caret and the selection like IntelliJ's in-place rename instead of selecting the
    # whole name with the caret at its end, and make startInlineRename internal for DartLspRenameHandlersTest.
    # 18. LspRenameHandler: ask for the new name in a dialog when the in-place template can't show the server's
    # placeholder (an empty range, or a placeholder that differs from the text at the range).
    rename_handler_path = os.path.join(
        base_dir,
        "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/features/rename/LspRenameHandler.kt"
    )
    if os.path.exists(rename_handler_path):
        with open(rename_handler_path, "r", encoding="utf-8") as f:
            rename_content = f.read()
        rename_replacements = [
            # 17a. internal for tests
            ("private fun startInlineRename(",
             "// Dart patch: internal for DartLspRenameHandlersTest\ninternal fun startInlineRename("),
            # 17b. remember the caret and the selection
            ("""  val document = editor.document
  val commandName = RefactoringBundle.message("renaming.command.name", initialName)
""",
             """  val document = editor.document
  val commandName = RefactoringBundle.message("renaming.command.name", initialName)
  // Dart patch: remember the caret and the selection, restored after the template starts
  val caretOffset = editor.caretModel.offset
  val selectedRange = editor.selectionModel.takeIf { it.hasSelection() }?.let { TextRange(it.selectionStart, it.selectionEnd) }
"""),
            # 17c. restore them after the template starts
            ("""    val templateState = TemplateManager.getInstance(project).runTemplate(editor, template)
""",
             """    val templateState = TemplateManager.getInstance(project).runTemplate(editor, template)
    // Dart patch: see restoreCaretAndSelection
    restoreCaretAndSelection(editor, textRange, caretOffset, selectedRange)
"""),
            # 17d. the function that restores them, before startInlineRename (marked by 17a)
            ("// Dart patch: internal for DartLspRenameHandlersTest\ninternal fun startInlineRename(",
             """// Dart patch: like IntelliJ's in-place rename (VariableInplaceRenamer.restoreSelection), keep the caret where it was
// and select only what the user had selected, or the whole name with "Preselect old name", so that typing edits the name.
private fun restoreCaretAndSelection(editor: Editor, textRange: TextRange, caretOffset: Int, selectedRange: TextRange?) {
  if (!textRange.containsOffset(caretOffset)) return
  if (selectedRange == null && editor.settings.isPreselectRename) return
  editor.caretModel.moveToOffset(caretOffset)
  if (selectedRange != null && textRange.contains(selectedRange)) {
    editor.selectionModel.setSelection(selectedRange.startOffset, selectedRange.endOffset)
  }
  else {
    editor.selectionModel.removeSelection()
  }
}

// Dart patch: internal for DartLspRenameHandlersTest
internal fun startInlineRename("""),
            # 18a. imports for the dialog
            ("import com.intellij.openapi.project.Project\n",
             "import com.intellij.openapi.project.Project\nimport com.intellij.openapi.ui.Messages\n"),
            ("import com.intellij.openapi.util.NlsSafe\n",
             "import com.intellij.openapi.util.NlsContexts\nimport com.intellij.openapi.util.NlsSafe\n"),
            # 18b. dialog instead of the in-place template, with the texts of LspRenameSupport (18d)
            ("""    val initialName = placeholder ?: editor.document.getText(range)

    startInlineRename(""",
             """    val initialName = placeholder ?: editor.document.getText(range)

    // Dart patch: the in-place template edits only the text at the range. If the range is empty or the server's placeholder
    // isn't that text (naming an unnamed Dart constructor, adding an import prefix), ask for the new name in a dialog.
    if (range.isEmpty || initialName != editor.document.getText(range)) {
      val renameSupport = lspServer.descriptor.lspCustomization.renameCustomizer as LspRenameSupport
      val offset = editor.caretModel.offset
      renameWithInputDialog(project, renameSupport.getInputDialogTitle(file, offset), renameSupport.getInputDialogLabel(file, offset),
                            initialName) { newName ->
        performRename(editor, lspServer, RenameParams(textDocumentIdentifier, position, newName))
      }
      return
    }

    startInlineRename("""),
            # 18c. the dialog itself (after 17a, which marks startInlineRename)
            ("// Dart patch: internal for DartLspRenameHandlersTest\ninternal fun startInlineRename(",
             """// Dart patch: asks for the new name, starting with the server's placeholder. Applying the edit changes documents, which
// must happen inside a command; the in-place rename gets one from the template. Internal for DartLspRenameHandlersTest.
internal fun renameWithInputDialog(
  project: Project,
  title: @NlsContexts.DialogTitle String,
  label: @NlsContexts.Label String,
  initialName: String,
  onRename: (String) -> Unit,
) {
  val newName = Messages.showInputDialog(project, label, title, null, initialName, null)
  if (newName.isNullOrBlank() || newName == initialName) return
  CommandProcessor.getInstance().executeCommand(project, { onRename(newName) }, title, null)
}

// Dart patch: internal for DartLspRenameHandlersTest
internal fun startInlineRename("""),
        ]
        if "// Dart patch: internal for DartLspRenameHandlersTest" not in rename_content:
            for old, new in rename_replacements:
                if old not in rename_content:
                    raise RuntimeError(f"LspRenameHandler.kt: patch anchor not found: {old.splitlines()[0]}")
                rename_content = rename_content.replace(old, new, 1)
            with open(rename_handler_path, "w", encoding="utf-8") as f:
                f.write(rename_content)

    # 18d. LspRenameSupport: title and label of that dialog, so that the plugin can name what is renamed there.
    rename_customizer_path = os.path.join(
        base_dir,
        "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/api/customization/LspRenameCustomizer.kt"
    )
    if os.path.exists(rename_customizer_path):
        with open(rename_customizer_path, "r", encoding="utf-8") as f:
            customizer_content = f.read()
        customizer_replacements = [
            ("import com.intellij.psi.PsiFile\n",
             "import com.intellij.openapi.util.NlsContexts\nimport com.intellij.psi.PsiFile\n"
             "import com.intellij.refactoring.RefactoringBundle\nimport com.intellij.util.ui.UIUtil\n"),
            ("\n}\n\nobject LspRenameDisabled : LspRenameCustomizer()",
             """

  // Dart patch: title and label of the dialog that asks for a name the in-place rename can't edit
  // (LspRenameHandler.renameWithInputDialog), at [offset] in [psiFile]. Plugins can name there what is renamed.
  open fun getInputDialogTitle(psiFile: PsiFile, offset: Int): @NlsContexts.DialogTitle String =
    RefactoringBundle.message("rename.title")

  open fun getInputDialogLabel(psiFile: PsiFile, offset: Int): @NlsContexts.Label String =
    UIUtil.removeMnemonic(RefactoringBundle.message("rename.dialog.new.name.label"))
}

object LspRenameDisabled : LspRenameCustomizer()"""),
        ]
        if "open fun getInputDialogTitle(" not in customizer_content:
            for old, new in customizer_replacements:
                if old not in customizer_content:
                    raise RuntimeError(f"LspRenameCustomizer.kt: patch anchor not found: {old.strip().splitlines()[0]}")
                customizer_content = customizer_content.replace(old, new, 1)
            with open(rename_customizer_path, "w", encoding="utf-8") as f:
                f.write(customizer_content)

    print("Patch applied successfully!")

if __name__ == "__main__":
    main()

