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

lsp.will.rename.files.progress.title=Collecting references\\u2026
lsp.will.rename.files.skip=Skip
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

    # 17. Backport IJPL-249183 (752ad55221cc): workspace/willRenameFiles support.
    # Can be simplified once the intellij-community sync source includes 752ad55221cc and
    # pluginSinceBuild >= 263 (keep the 10s timeout adjustment and trailing-slash directory glob fix
    # until resolved upstream).

    # 17a. Register LspRefactoringEventListener and 253-262 fallback extensions in dart-lsp-impl.xml
    if os.path.exists(xml_path):
        with open(xml_path, "r", encoding="utf-8") as f:
            xml_content = f.read()
        xml_modified = False
        rename_handler_anchor = (
            '    <renameHandler implementation="com.intellij.platform.dartlsp.impl.features.rename.LspRenameHandler" order="last"/>'
        )
        fallback_extensions = (
            '    <renamePsiElementProcessor implementation="com.intellij.platform.dartlsp.impl.platformListeners.LspRenameEventDataFallback" order="last"/>\n'
            '    <moveFileHandler implementation="com.intellij.platform.dartlsp.impl.platformListeners.LspMoveEventDataFallback" order="last"/>'
        )
        if "LspRenameEventDataFallback" not in xml_content and rename_handler_anchor in xml_content:
            xml_content = xml_content.replace(rename_handler_anchor, rename_handler_anchor + "\n" + fallback_extensions)
            xml_modified = True
        listener_anchor = (
            '    <listener topic="com.intellij.openapi.fileEditor.FileEditorManagerListener"\n'
            '              class="com.intellij.platform.dartlsp.impl.platformListeners.LspFileEditorManagerListener"/>'
        )
        refactoring_listener = (
            '    <listener topic="com.intellij.refactoring.listeners.RefactoringEventListener"\n'
            '              class="com.intellij.platform.dartlsp.impl.platformListeners.LspRefactoringEventListener"/>'
        )
        if "LspRefactoringEventListener" not in xml_content and listener_anchor in xml_content:
            xml_content = xml_content.replace(listener_anchor, listener_anchor + "\n" + refactoring_listener)
            xml_modified = True
        if xml_modified:
            with open(xml_path, "w", encoding="utf-8") as f:
                f.write(xml_content)

    # 17b. Expose getFilePath and advertise workspace.fileOperations.willRename in LspServerDescriptor.kt
    descriptor_path = os.path.join(
        base_dir,
        "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/api/LspServerDescriptor.kt"
    )
    if os.path.exists(descriptor_path):
        with open(descriptor_path, "r", encoding="utf-8") as f:
            descriptor_content = f.read()
        modified = False
        file_ops_import = "import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities"
        if file_ops_import not in descriptor_content:
            descriptor_content = descriptor_content.replace(
                "import org.eclipse.lsp4j.FailureHandlingKind\n",
                f"import org.eclipse.lsp4j.FailureHandlingKind\n{file_ops_import}\n"
            )
            modified = True
        old_get_file_path = "  protected open fun getFilePath(file: VirtualFile): String = file.path"
        new_get_file_path = "  @ApiStatus.Internal\n  open fun getFilePath(file: VirtualFile): String = file.path"
        if old_get_file_path in descriptor_content:
            descriptor_content = descriptor_content.replace(old_get_file_path, new_get_file_path)
            modified = True
        old_symbol_caps = """        symbol = SymbolCapabilities(true).apply {
          symbolKind = SymbolKindCapabilities().apply {
            valueSet = lspCustomization.symbolKindCustomizer.supportedKinds
          }
        }
      }"""
        new_symbol_caps = """        symbol = SymbolCapabilities(true).apply {
          symbolKind = SymbolKindCapabilities().apply {
            valueSet = lspCustomization.symbolKindCustomizer.supportedKinds
          }
        }
        fileOperations = FileOperationsWorkspaceCapabilities().apply {
          dynamicRegistration = true
          willRename = true
        }
      }"""
        if "FileOperationsWorkspaceCapabilities()" not in descriptor_content and old_symbol_caps in descriptor_content:
            descriptor_content = descriptor_content.replace(old_symbol_caps, new_symbol_caps)
            modified = True
        if modified:
            with open(descriptor_path, "w", encoding="utf-8") as f:
                f.write(descriptor_content)

    # 17c. Pass InitializeParams alongside InitializeResult in Lsp4jServerConnector.kt
    if os.path.exists(connector_path):
        with open(connector_path, "r", encoding="utf-8") as f:
            connector_content = f.read()
        modified = False
        init_params_import = "import org.eclipse.lsp4j.InitializeParams"
        if init_params_import not in connector_content:
            connector_content = connector_content.replace(
                "import org.eclipse.lsp4j.InitializeResult\n",
                f"{init_params_import}\nimport org.eclipse.lsp4j.InitializeResult\n"
            )
            modified = True
        old_connect_sig = "  internal fun connect(onSuccess: (InitializeResult) -> Unit) {"
        new_connect_sig = "  internal fun connect(onSuccess: (InitializeParams, InitializeResult) -> Unit) {"
        if old_connect_sig in connector_content:
            connector_content = connector_content.replace(old_connect_sig, new_connect_sig)
            modified = True
        old_init_server = """  private fun initializeServer(onSuccess: (InitializeResult) -> Unit) {
    logger.debug("$descriptor: initializing LSP server")

    var error: Throwable? = null
    val countDownLatch = CountDownLatch(1)

    lsp4jServer.initialize(descriptor.createInitializeParams())
      .whenComplete { result: InitializeResult?, th: Throwable? ->
        if (result != null) {
          lsp4jServer.initialized(InitializedParams())
          onSuccess(result)"""
        new_init_server = """  private fun initializeServer(onSuccess: (InitializeParams, InitializeResult) -> Unit) {
    logger.debug("$descriptor: initializing LSP server")

    var error: Throwable? = null
    val countDownLatch = CountDownLatch(1)

    val initializeParams = descriptor.createInitializeParams()

    lsp4jServer.initialize(initializeParams)
      .whenComplete { result: InitializeResult?, th: Throwable? ->
        if (result != null) {
          lsp4jServer.initialized(InitializedParams())
          onSuccess(initializeParams, result)"""
        if old_init_server in connector_content:
            connector_content = connector_content.replace(old_init_server, new_init_server)
            modified = True
        if modified:
            with open(connector_path, "w", encoding="utf-8") as f:
                f.write(connector_content)

    # 17d. Store effective clientCapabilities on LspServerImpl.kt
    if os.path.exists(lsp_server_impl_path):
        with open(lsp_server_impl_path, "r", encoding="utf-8") as f:
            server_content = f.read()
        modified = False
        client_caps_import = "import org.eclipse.lsp4j.ClientCapabilities"
        if client_caps_import not in server_content:
            server_content = server_content.replace(
                "import org.eclipse.lsp4j.CodeLens\n",
                f"{client_caps_import}\nimport org.eclipse.lsp4j.CodeLens\n"
            )
            modified = True
        old_server_caps = "  internal val serverCapabilities: ServerCapabilities?"
        new_server_caps = """  /**
   * Holds the effective [ClientCapabilities] sent to the LSP server in [org.eclipse.lsp4j.InitializeParams].
   *
   * Unlike [LspServerDescriptor.clientCapabilities] or [LspServerDescriptor.createInitializeParams],
   * this property guarantees the same instance across multiple reads.
   *
   * The value is `null` before the server initializes.
   */
  internal var clientCapabilities: ClientCapabilities? = null
    private set

  internal val serverCapabilities: ServerCapabilities?"""
        if "var clientCapabilities: ClientCapabilities?" not in server_content and old_server_caps in server_content:
            server_content = server_content.replace(old_server_caps, new_server_caps)
            modified = True
        old_connect_call = """          lsp4jServerConnector.connect { initializeResult ->
            this.initializeResult = initializeResult"""
        new_connect_call = """          lsp4jServerConnector.connect { initializeParams, initializeResult ->
            this.initializeResult = initializeResult
            this.clientCapabilities = initializeParams.capabilities"""
        if old_connect_call in server_content:
            server_content = server_content.replace(old_connect_call, new_connect_call)
            modified = True
        if modified:
            with open(lsp_server_impl_path, "w", encoding="utf-8") as f:
                f.write(server_content)

    # 17e. Register workspace/willRenameFiles dynamic capability in LspDynamicCapabilities.kt
    dynamic_caps_path = os.path.join(
        base_dir,
        "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/LspDynamicCapabilities.kt"
    )
    if os.path.exists(dynamic_caps_path):
        with open(dynamic_caps_path, "r", encoding="utf-8") as f:
            dyn_content = f.read()
        modified = False
        file_op_options_import = "import org.eclipse.lsp4j.FileOperationOptions"
        if file_op_options_import not in dyn_content:
            dyn_content = dyn_content.replace(
                "import org.eclipse.lsp4j.ExecuteCommandRegistrationOptions\n",
                f"import org.eclipse.lsp4j.ExecuteCommandRegistrationOptions\n{file_op_options_import}\n"
            )
            modified = True
        symbol_reg = '    val symbol = registration("workspace/symbol", WorkspaceSymbolRegistrationOptions::class.java)'
        will_rename_reg = '    val willRenameFiles = registration("workspace/willRenameFiles", FileOperationOptions::class.java)'
        if "val willRenameFiles =" not in dyn_content and symbol_reg in dyn_content:
            dyn_content = dyn_content.replace(symbol_reg, f"{symbol_reg}\n{will_rename_reg}")
            modified = True
        if modified:
            with open(dynamic_caps_path, "w", encoding="utf-8") as f:
                f.write(dyn_content)

    # 17f. Add pathMatchesFileOperationFilter (with trailing-slash folder glob fix) to LspGlobMatcher.kt
    glob_matcher_path = os.path.join(
        base_dir,
        "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/LspGlobMatcher.kt"
    )
    if os.path.exists(glob_matcher_path):
        with open(glob_matcher_path, "r", encoding="utf-8") as f:
            glob_content = f.read()
        modified = False
        if "fun pathMatchesFileOperationFilter(" not in glob_content:
            glob_content = """package com.intellij.platform.dartlsp.impl

import com.intellij.openapi.util.getPathMatcher
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileOperationPatternKind
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal class LspGlobMatcher {
  private val patternToPathMatcherCache: MutableMap<String, PathMatcher> = ConcurrentHashMap()

  fun pathMatches(path: String, isDirectory: Boolean, globPattern: String, basePath: String?): Boolean {
    val pathToMatch = when {
      basePath == null -> path
      path == basePath -> ""
      path.startsWith("$basePath/") -> path.substring(basePath.length + 1)
      else -> return false // base URI doesn't match this path
    }

    if (isDirectory) {
      // Any directory may contain files that match the glob pattern.
      // Per-file events are not generated, so we need to inform the server about all directory events.
      return true
    }

    val pathMatcher = patternToPathMatcherCache.getOrPut(globPattern) { getPathMatcher(globPattern) }
    return pathMatcher.matches(Path.of(pathToMatch))
  }

  /**
   * @param path file path as seen by server; to get a file path, use [com.intellij.platform.dartlsp.api.LspServerDescriptor.getFilePath]
   */
  fun pathMatchesFileOperationFilter(
    path: String,
    uriScheme: String,
    isDirectory: Boolean,
    filter: FileOperationFilter,
  ): Boolean {
    if (filter.scheme != null && !filter.scheme.equals(uriScheme, ignoreCase = true)) return false

    val pattern = filter.pattern ?: return false
    when (pattern.matches) {
      null -> {}
      FileOperationPatternKind.File -> if (isDirectory) return false
      FileOperationPatternKind.Folder -> if (!isDirectory) return false
      else -> return false
    }

    val rawGlobPattern = pattern.glob ?: return false
    // Dart patch (IJPL-249183): trim trailing slashes on directory globs (such as Dart's "**/" folder filter),
    // because getPathMatcher("**/") compiles to "glob:{**/,}" while Path.of(path) strips trailing slashes.
    val globPattern = if (isDirectory && rawGlobPattern.length > 1) rawGlobPattern.trimEnd('/') else rawGlobPattern
    val ignoreCase = pattern.options?.ignoreCase == true
    val glob = if (ignoreCase) globPattern.lowercase(Locale.ROOT) else globPattern
    val pathToMatch = if (ignoreCase) path.lowercase(Locale.ROOT) else path
    val pathMatcher = patternToPathMatcherCache.getOrPut(glob) { getPathMatcher(glob) }
    return try {
      pathMatcher.matches(Path.of(pathToMatch))
    }
    catch (_: InvalidPathException) {
      false
    }
  }
}
"""
            modified = True
        elif "    val globPattern = pattern.glob ?: return false\n" in glob_content:
            glob_content = glob_content.replace(
                "    val globPattern = pattern.glob ?: return false\n",
                """    val rawGlobPattern = pattern.glob ?: return false
    // Dart patch (IJPL-249183): trim trailing slashes on directory globs (such as Dart's "**/" folder filter),
    // because getPathMatcher("**/") compiles to "glob:{**/,}" while Path.of(path) strips trailing slashes.
    val globPattern = if (isDirectory && rawGlobPattern.length > 1) rawGlobPattern.trimEnd('/') else rawGlobPattern
"""
            )
            modified = True
        if modified:
            with open(glob_matcher_path, "w", encoding="utf-8") as f:
                f.write(glob_content)

    # 17g. Write or patch LspRefactoringEventListener.kt (with 253-262 RefactoringEventData fallbacks)
    refactoring_listener_path = os.path.join(
        base_dir,
        "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/platformListeners/LspRefactoringEventListener.kt"
    )
    refactoring_listener_content = """// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.dartlsp.impl.platformListeners

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.dartlsp.impl.features.workspaceFileOperations.LspWillRenameFilesHandler
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap

/** The ID of the refactoring in [com.intellij.refactoring.rename.RenameProcessor]. */
internal const val RENAME_REFACTORING_ID = "refactoring.rename"

/** The ID of the refactoring in [com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor]. */
internal const val MOVE_REFACTORING_ID = "refactoring.move"

internal class LspRefactoringEventListener(private val project: Project) : RefactoringEventListener {
  override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
    if (beforeData == null) return
    if (refactoringId == RENAME_REFACTORING_ID || refactoringId == MOVE_REFACTORING_ID) {
      LspWillRenameFilesHandler.renameOrMoveRefactoringStarted(project, refactoringId, beforeData)
    }
  }

  override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
    if (refactoringId == RENAME_REFACTORING_ID || refactoringId == MOVE_REFACTORING_ID) {
      LspWillRenameFilesHandler.renameOrMoveRefactoringDone(project, refactoringId, afterData)
    }
  }
}

/**
 * Dart patch (IJPL-249183): compatibility fallback for IntelliJ 2025.3-2026.2 (`pluginSinceBuild < 263`),
 * where `RefactoringEventData.RENAMES_KEY` does not yet exist.
 *
 * Inactive when `RefactoringEventData.RENAMES_KEY` is present at runtime (2026.3+).
 * Implements [RenamePsiElementProcessorBase.DefaultRenamePsiElementProcessor] with `order="last"` so
 * platform default-processor checks behave identically for directories and files without a custom processor.
 */
internal class LspRenameEventDataFallback :
  RenamePsiElementProcessor(),
  RenamePsiElementProcessorBase.DefaultRenamePsiElementProcessor,
  DumbAware {

  override fun canProcessElement(element: PsiElement): Boolean {
    if (LspWillRenameFilesHandler.hasPlatformRenamesKey) return false
    if (element !is PsiFileSystemItem) return false
    val virtualFile = element.virtualFile ?: return false
    return LspWillRenameFilesHandler.hasWillRenameCapableServerForFile(element.project, virtualFile)
  }

  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
    if (!LspWillRenameFilesHandler.hasPlatformRenamesKey) {
      LspWillRenameFilesHandler.recordFallbackRename(element, allRenames)
    }
  }
}

/**
 * Dart patch (IJPL-249183): compatibility fallback for IntelliJ 2025.3-2026.2 (`pluginSinceBuild < 263`),
 * where `RefactoringEventData.TARGET_ELEMENT_KEY` does not yet exist.
 *
 * [canProcessElement] always returns `false` so [MoveFileHandler.forElement] never selects this handler;
 * [MoveFileHandler.detectConflicts] invokes all registered handlers regardless of [canProcessElement].
 */
internal class LspMoveEventDataFallback : MoveFileHandler() {
  override fun canProcessElement(element: PsiFile?): Boolean = false

  override fun prepareMovedFile(file: PsiFile?, moveDestination: PsiDirectory?, oldToNewMap: MutableMap<PsiElement, PsiElement>?) {}

  override fun findUsages(
    psiFile: PsiFile,
    newParent: PsiDirectory,
    searchInComments: Boolean,
    searchInNonJavaFiles: Boolean,
  ): List<UsageInfo>? = null

  override fun retargetUsages(usageInfos: List<UsageInfo>, oldToNewMap: Map<PsiElement, PsiElement>) {}

  override fun updateMovedFile(file: PsiFile?) {}

  override fun detectConflicts(
    conflicts: MultiMap<PsiElement, String>?,
    elementsToMove: Array<out PsiElement>?,
    usages: Array<out UsageInfo>?,
    targetDirectory: PsiDirectory?,
  ) {
    if (LspWillRenameFilesHandler.hasPlatformTargetElementKey) return
    if (elementsToMove == null || targetDirectory == null) return
    if (!LspWillRenameFilesHandler.hasWillRenameCapableServer(targetDirectory.project)) return
    LspWillRenameFilesHandler.recordFallbackMove(elementsToMove, targetDirectory)
  }
}
"""
    existing_listener_content = None
    if os.path.exists(refactoring_listener_path):
        with open(refactoring_listener_path, "r", encoding="utf-8") as f:
            existing_listener_content = f.read()
    if existing_listener_content != refactoring_listener_content and (
        existing_listener_content is None or "LspRenameEventDataFallback" not in existing_listener_content
    ):
        os.makedirs(os.path.dirname(refactoring_listener_path), exist_ok=True)
        with open(refactoring_listener_path, "w", encoding="utf-8") as f:
            f.write(refactoring_listener_content)

    # 17h. Write or patch LspWillRenameFilesHandler.kt (backported from IJPL-249183 / 752ad55221cc
    # with 10s timeout and 253-262 RefactoringEventData compatibility)
    will_rename_handler_path = os.path.join(
        base_dir,
        "third_party/thirdPartySrc/platform-lsp/src/com/intellij/platform/dartlsp/impl/features/workspaceFileOperations/LspWillRenameFilesHandler.kt"
    )
    will_rename_handler_content = """// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.dartlsp.impl.features.workspaceFileOperations

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.platform.dartlsp.api.LspBundle
import com.intellij.platform.dartlsp.impl.LspDynamicCapabilities
import com.intellij.platform.dartlsp.impl.LspServerImpl
import com.intellij.platform.dartlsp.impl.LspServerManagerImpl
import com.intellij.platform.dartlsp.impl.platformListeners.MOVE_REFACTORING_ID
import com.intellij.platform.dartlsp.impl.platformListeners.RENAME_REFACTORING_ID
import com.intellij.platform.dartlsp.impl.util.LspWorkspaceEditApplier
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.URLUtil
import java.lang.ref.WeakReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.WorkspaceEdit
import kotlin.time.Duration.Companion.milliseconds

private val LOG = fileLogger()

// The spec allows a client to drop a response that takes too long.
// Dart patch (IJPL-249183): use a 10s timeout (instead of upstream's 1000ms) to accommodate
// Dart Analysis Server cross-package import resolution; users can still click "Skip" in the progress dialog.
private val WILL_RENAME_FILES_TIMEOUT = 10_000.milliseconds

internal object LspWillRenameFilesHandler {
  // Dart patch (IJPL-249183): RefactoringEventData.RENAMES_KEY and TARGET_ELEMENT_KEY were added in
  // IntelliJ 2026.3. Resolve them reflectively when running on 263+, and fall back to pre-refactoring
  // listeners (LspRenameEventDataFallback / LspMoveEventDataFallback) on 2025.3-2026.2.
  @Suppress("UNCHECKED_CAST")
  private val RENAMES_KEY: Key<Map<PsiElement, String>>? = runCatching {
    RefactoringEventData::class.java.getField("RENAMES_KEY").get(null) as? Key<Map<PsiElement, String>>
  }.getOrNull()

  @Suppress("UNCHECKED_CAST")
  private val TARGET_ELEMENT_KEY: Key<PsiElement>? = runCatching {
    RefactoringEventData::class.java.getField("TARGET_ELEMENT_KEY").get(null) as? Key<PsiElement>
  }.getOrNull()

  internal val hasPlatformRenamesKey: Boolean
    get() = RENAMES_KEY != null

  internal val hasPlatformTargetElementKey: Boolean
    get() = TARGET_ELEMENT_KEY != null

  private const val MAX_FALLBACK_ENTRIES = 16
  private val fallbackLock = Any()
  private val capturedRenames = ArrayDeque<CapturedRename>()
  private val capturedMoves = ArrayDeque<CapturedMove>()

  private class CapturedRename(
    val projectRef: WeakReference<Project>,
    val primaryElementRef: WeakReference<PsiElement>,
    val allRenamesRef: WeakReference<Map<PsiElement, String>>,
  )

  private class CapturedMove(
    val projectRef: WeakReference<Project>,
    val elementsRef: WeakReference<Array<out PsiElement>>,
    val targetDirRef: WeakReference<PsiDirectory>,
  )

  internal fun hasWillRenameCapableServer(project: Project): Boolean {
    if (project.isDisposed) return false
    return LspServerManagerImpl.getInstanceImpl(project).getAllRunningServers()
      .any { it.clientCapabilities?.workspace?.fileOperations?.willRename == true }
  }

  internal fun hasWillRenameCapableServerForFile(project: Project, fileOrDir: VirtualFile): Boolean {
    if (project.isDisposed) return false
    return LspServerManagerImpl.getInstanceImpl(project).getAllRunningServers()
      .any { server ->
        server.clientCapabilities?.workspace?.fileOperations?.willRename == true &&
        server.descriptor.roots.any { root -> VfsUtilCore.isAncestor(root, fileOrDir, false) }
      }
  }

  internal fun recordFallbackRename(primaryElement: PsiElement, allRenames: Map<PsiElement, String>) {
    synchronized(fallbackLock) {
      pruneFallbackEntriesLocked()
      capturedRenames.removeAll { it.allRenamesRef.get() === allRenames }
      capturedRenames.addFirst(
        CapturedRename(WeakReference(primaryElement.project), WeakReference(primaryElement), WeakReference(allRenames))
      )
      while (capturedRenames.size > MAX_FALLBACK_ENTRIES) {
        capturedRenames.removeLast()
      }
    }
  }

  internal fun recordFallbackMove(elementsToMove: Array<out PsiElement>, targetDirectory: PsiDirectory) {
    synchronized(fallbackLock) {
      pruneFallbackEntriesLocked()
      capturedMoves.removeAll { it.elementsRef.get() === elementsToMove }
      capturedMoves.addFirst(
        CapturedMove(WeakReference(targetDirectory.project), WeakReference(elementsToMove), WeakReference(targetDirectory))
      )
      while (capturedMoves.size > MAX_FALLBACK_ENTRIES) {
        capturedMoves.removeLast()
      }
    }
  }

  internal fun renameOrMoveRefactoringDone(project: Project, refactoringId: String, afterData: RefactoringEventData?) {
    if (RENAMES_KEY != null && TARGET_ELEMENT_KEY != null) return
    val element = afterData?.getUserData(RefactoringEventData.PSI_ELEMENT_KEY)
    synchronized(fallbackLock) {
      pruneFallbackEntriesLocked()
      when (refactoringId) {
        RENAME_REFACTORING_ID -> if (element != null) {
          capturedRenames.removeAll { entry ->
            entry.projectRef.get() == project &&
            (entry.primaryElementRef.get() == element || entry.allRenamesRef.get()?.containsKey(element) == true)
          }
        }
        MOVE_REFACTORING_ID -> if (element != null) {
          val index = capturedMoves.indexOfFirst { it.projectRef.get() == project && it.targetDirRef.get() == element }
          if (index >= 0) capturedMoves.removeAt(index)
        }
      }
    }
  }

  private fun pruneFallbackEntriesLocked() {
    capturedRenames.removeAll { it.projectRef.get()?.isDisposed != false || it.allRenamesRef.get() == null }
    capturedMoves.removeAll {
      it.projectRef.get()?.isDisposed != false || it.elementsRef.get() == null || it.targetDirRef.get() == null
    }
  }

  private fun findFallbackRename(project: Project, primaryElement: PsiElement?): Map<PsiElement, String>? {
    if (primaryElement == null) return null
    synchronized(fallbackLock) {
      pruneFallbackEntriesLocked()
      val match = capturedRenames.firstOrNull { entry ->
        val map = entry.allRenamesRef.get() ?: return@firstOrNull false
        entry.projectRef.get() == project && (entry.primaryElementRef.get() == primaryElement || map.containsKey(primaryElement))
      }
      return match?.allRenamesRef?.get()?.let { LinkedHashMap(it) }
    }
  }

  private fun findFallbackMoveTarget(project: Project, elements: Array<out PsiElement>): PsiDirectory? {
    synchronized(fallbackLock) {
      pruneFallbackEntriesLocked()
      val match = capturedMoves.firstOrNull { entry ->
        val capturedElements = entry.elementsRef.get() ?: return@firstOrNull false
        entry.projectRef.get() == project && (capturedElements === elements || capturedElements.contentEquals(elements))
      }
      return match?.targetDirRef?.get()
    }
  }

  /**
   * Sends the
   * [workspace/willRenameFiles](https://microsoft.github.io/language-server-protocol/specification/#workspace_willRenameFiles)
   * request before a Rename or Move refactoring changes the files, and applies the edits from the servers in the command of the refactoring.
   */
  internal fun renameOrMoveRefactoringStarted(project: Project, refactoringId: String, beforeData: RefactoringEventData) {
    val lspServers = LspServerManagerImpl.getInstanceImpl(project).getAllRunningServers()
      .filter { it.clientCapabilities?.workspace?.fileOperations?.willRename == true }
    if (lspServers.isEmpty()) return

    val application = ApplicationManager.getApplication()
    if (!application.isDispatchThread || application.isWriteAccessAllowed) {
      LOG.warn("Skipping workspace/willRenameFiles for '$refactoringId': the event came " +
               "${if (application.isDispatchThread) "on the EDT" else "off the EDT"}, " +
               "${if (application.isWriteAccessAllowed) "inside" else "outside"} a write action. " +
               "Expected: EDT outside a write action.")
      return
    }

    val plannedRenames = when (refactoringId) {
      RENAME_REFACTORING_ID -> getPlannedRenamesFromRenameRefactoring(project, beforeData)
      MOVE_REFACTORING_ID -> getPlannedRenamesFromMoveRefactoring(project, beforeData)
      else -> emptyList()
    }
    if (plannedRenames.isEmpty()) return

    try {
      sendWillRenameFilesRequestsAndApplyEdits(project, lspServers, plannedRenames)
    }
    catch (e: Throwable) {
      if (e is CancellationException || e is ControlFlowException) throw e
      LOG.error(e)
    }
  }

  private fun getPlannedRenamesFromRenameRefactoring(project: Project, beforeData: RefactoringEventData): List<LspPlannedRename> {
    val renames = (if (RENAMES_KEY != null) {
      beforeData.getUserData(RENAMES_KEY)
    }
    else {
      findFallbackRename(project, beforeData.getUserData(RefactoringEventData.PSI_ELEMENT_KEY))
    }) ?: return emptyList()
    return renames.mapNotNull { (element, newName) ->
      if (element !is PsiFileSystemItem || !element.isValid || element.name == newName) return@mapNotNull null
      val parentDir = element.parent as? PsiDirectory ?: return@mapNotNull null
      createPlannedRename(element, parentDir, newName)
    }
  }

  private fun getPlannedRenamesFromMoveRefactoring(project: Project, beforeData: RefactoringEventData): List<LspPlannedRename> {
    val elements = beforeData.getUserData(RefactoringEventData.PSI_ELEMENT_ARRAY_KEY) ?: return emptyList()
    val targetDir = (if (TARGET_ELEMENT_KEY != null) {
      beforeData.getUserData(TARGET_ELEMENT_KEY) as? PsiDirectory
    }
    else {
      findFallbackMoveTarget(project, elements)
    }) ?: return emptyList()
    return elements.mapNotNull { element ->
      // the move skips an item that is already in the target, and a file whose name already exists there
      if (element !is PsiFileSystemItem || !element.isValid || element.parent == targetDir) return@mapNotNull null
      if (element is PsiFile && targetDir.findFile(element.name) != null) return@mapNotNull null
      createPlannedRename(element, targetDir, element.name)
    }
  }

  private fun createPlannedRename(fileSystemItem: PsiFileSystemItem, newParentDir: PsiDirectory, newName: String): LspPlannedRename? {
    val fileOrDir = fileSystemItem.virtualFile
    if (fileOrDir == null || !fileOrDir.isValid) return null
    return LspPlannedRename(fileOrDir, newParentDir.virtualFile, newName)
  }

  private class LspPlannedRename(val fileOrDir: VirtualFile, val newParentDir: VirtualFile, val newName: String)

  @RequiresEdt
  private fun sendWillRenameFilesRequestsAndApplyEdits(
    project: Project,
    lspServers: List<LspServerImpl>,
    plannedRenames: List<LspPlannedRename>,
  ) {
    val lspServerToFilters = lspServers
      .mapNotNull { lspServer -> getWillRenameFilters(lspServer).takeIf { it.isNotEmpty() }?.let { lspServer to it } }
    if (lspServerToFilters.isEmpty()) return

    var workspaceEditAppliers = emptyList<LspWorkspaceEditApplier>()
    val completed = ApplicationManagerEx.getApplicationEx().runProcessWithProgressSynchronously(
      { workspaceEditAppliers = sendWillRenameFilesRequests(lspServerToFilters, plannedRenames) },
      LspBundle.message("lsp.will.rename.files.progress.title"),
      true,
      true,
      project,
      null,
      LspBundle.message("lsp.will.rename.files.skip"),
    )

    if (completed) {
      workspaceEditAppliers.forEach { it.applyWorkspaceEdit() }
    }
  }

  private fun getWillRenameFilters(lspServer: LspServerImpl): List<FileOperationFilter> {
    val staticFilters = lspServer.serverCapabilities?.workspace?.fileOperations?.willRename?.filters.orEmpty()
    val dynamicFilters = lspServer.dynamicCapabilities.getCapabilityRegistrationOptions(LspDynamicCapabilities.willRenameFiles)
      .flatMap { it.filters.orEmpty() }
    return staticFilters + dynamicFilters
  }

  @RequiresBackgroundThread
  private fun sendWillRenameFilesRequests(
    lspServerToFilters: List<Pair<LspServerImpl, List<FileOperationFilter>>>,
    plannedRenames: List<LspPlannedRename>,
  ): List<LspWorkspaceEditApplier> = runBlockingCancellable {
    lspServerToFilters.map { (lspServer, filters) ->
      async {
        val serverPlannedRenames = filterPlannedRenames(lspServer, filters, plannedRenames)
        if (serverPlannedRenames.isEmpty()) return@async null
        val edit = sendWillRenameFilesRequest(lspServer, serverPlannedRenames) ?: return@async null
        readAction { LspWorkspaceEditApplier.create(lspServer, edit) }
      }
    }.awaitAll().filterNotNull()
  }

  @RequiresBackgroundThread
  private fun filterPlannedRenames(
    lspServer: LspServerImpl,
    filters: List<FileOperationFilter>,
    plannedRenames: List<LspPlannedRename>,
  ): List<LspPlannedRename> {
    return plannedRenames.filter { plannedRename ->
      val filePath = lspServer.descriptor.getFilePath(plannedRename.fileOrDir)
      val uriScheme = plannedRename.fileOrDir.fileSystem.protocol
      val isDirectory = plannedRename.fileOrDir.isDirectory

      lspServer.descriptor.roots.any { root -> VfsUtilCore.isAncestor(root, plannedRename.fileOrDir, false) } &&
      filters.any { lspServer.globMatcher.pathMatchesFileOperationFilter(filePath, uriScheme, isDirectory, it) }
    }
  }

  private suspend fun sendWillRenameFilesRequest(lspServer: LspServerImpl, plannedRenames: List<LspPlannedRename>): WorkspaceEdit? {
    val fileRenames = plannedRenames.map {
      val oldUri = lspServer.descriptor.getFileUri(it.fileOrDir)
      val newUri = lspServer.descriptor.getFileUri(LspRenameTargetFile(it.newParentDir, it.newName, it.fileOrDir.isDirectory))
      FileRename(oldUri, newUri)
    }
    val params = RenameFilesParams(fileRenames)

    return try {
      withTimeoutOrNull(WILL_RENAME_FILES_TIMEOUT) {
        lspServer.sendRequest { it.workspaceService.willRenameFiles(params) }
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      // a failed server must not cancel the requests to other servers
      LOG.warn("${lspServer.descriptor}: workspace/willRenameFiles request failed", e)
      null
    }
  }
}

/**
 * The file or directory at the new location of a planned rename. It does not exist yet.
 * It has the file system and the path of the real file after the rename,
 * so [com.intellij.platform.dartlsp.api.LspServerDescriptor.getFileUri] and its overrides give the same URI for both.
 */
private class LspRenameTargetFile(
  newParentDir: VirtualFile,
  newName: String,
  private val isDirectory: Boolean,
) : FakeVirtualFile(newParentDir, newName) {

  override fun getUrl(): String = fileSystem.protocol + URLUtil.SCHEME_SEPARATOR + path
  override fun isDirectory(): Boolean = isDirectory
  override fun isValid(): Boolean = true
}
"""
    existing_handler_content = None
    if os.path.exists(will_rename_handler_path):
        with open(will_rename_handler_path, "r", encoding="utf-8") as f:
            existing_handler_content = f.read()
    if existing_handler_content != will_rename_handler_content and (
        existing_handler_content is None or "hasPlatformRenamesKey" not in existing_handler_content
    ):
        os.makedirs(os.path.dirname(will_rename_handler_path), exist_ok=True)
        with open(will_rename_handler_path, "w", encoding="utf-8") as f:
            f.write(will_rename_handler_content)

    print("Patch applied successfully!")

if __name__ == "__main__":
    main()

