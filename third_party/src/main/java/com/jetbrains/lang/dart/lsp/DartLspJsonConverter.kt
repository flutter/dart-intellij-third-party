package com.jetbrains.lang.dart.lsp

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.lang.reflect.ParameterizedType

/**
 * Dedicated JSON serialization and deserialization layer for [DartBridgeLspServer].
 *
 * ## 1. Why does this JSON conversion layer exist? (Bridge vs. Out-of-Process LSP)
 * If the Dart Analysis Server (DAS) ran as a standalone, out-of-process LSP server connected over `stdin/stdout`,
 * JetBrains Platform (`com.intellij.platform.dartlsp`) would handle 100% of JSON serialization/deserialization internally.
 *
 * However, because we run an in-process bridge ([DartBridgeLspServer]) to reuse the single running DAS instance
 * ([com.jetbrains.lang.dart.analyzer.DartAnalysisServerService]), JetBrains Platform calls our Kotlin/Java methods
 * directly with in-memory `lsp4j` DTO objects (e.g., [org.eclipse.lsp4j.CodeActionParams]).
 *
 * Because the existing DAS instance communicates over a legacy protocol using `lsp.handle` envelopes
 * (`{"method": "lsp.handle", "params": {"lspMessage": <json tree>}}`), our bridge must convert between in-memory `lsp4j`
 * objects and JSON trees at this boundary.
 *
 * ## 2. Why can't we use JetBrains Platform's own internal serializer?
 * JetBrains Platform encapsulates its [MessageJsonHandler] inside its private process/stream handling layer.
 * Furthermore, even if JetBrains' internal serializer were public, it uses standard `lsp4j`. Standard `lsp4j`'s default
 * `EitherTypeAdapter` crashes when deserializing [org.eclipse.lsp4j.CodeAction] lists because both [org.eclipse.lsp4j.Command]
 * and [org.eclipse.lsp4j.CodeAction] start with `{...}` (a JSON Object), throwing:
 * `Ambiguous Either type: token BEGIN_OBJECT matches both alternatives`.
 *
 * ## 3. Why does `textDocument/codeAction` return `Either<Command, CodeAction>`?
 * The official Microsoft LSP 3.17 Specification defines the return type of `textDocument/codeAction` as a union array:
 * `(Command | CodeAction)[]`. Because Java and Kotlin lack native union types, the Eclipse `lsp4j` library (used by both
 * JetBrains Platform and our bridge) models `Command | CodeAction` as [org.eclipse.lsp4j.jsonrpc.messages.Either].
 */
internal object DartLspJsonConverter {
    // We initialize MessageJsonHandler with all supported LSP service endpoints so that
    // lsp4j pre-registers specialized type adapters (Ranges, Positions, Enums) for all LSP interfaces.
    private val JSON_HANDLER = run {
        val supportedMethods = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(LanguageServer::class.java))
        supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(TextDocumentService::class.java))
        supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(WorkspaceService::class.java))
        supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(LanguageClient::class.java))
        MessageJsonHandler(supportedMethods)
    }

    /**
     * Customized [Gson] instance derived from `lsp4j`'s [MessageJsonHandler.gson].
     *
     * - [serializeNulls]: By default, Gson drops `null` fields (`{"uri": "file://..."}`). When executing code actions
     *   (`dart.edit.codeAction.apply`), DAS's `OptionalVersionedTextDocumentIdentifier.canParse()` strictly demands:
     *   `if (!json.containsKey("version")) return false;`. Enabling [serializeNulls] ensures `version: null` is output.
     * - [SmartEitherTypeAdapterFactory]: Disambiguates `Either<Command, CodeAction>` lists.
     */
    @JvmField
    internal val GSON: Gson = JSON_HANDLER.gson.newBuilder()
        .serializeNulls()
        .registerTypeAdapterFactory(SmartEitherTypeAdapterFactory())
        .create()

    /**
     * Custom [TypeAdapterFactory] for [Either] (`Either<L, R>`) that inspects JSON Object field names to deterministically
     * disambiguate union types like `Either<Command, CodeAction>`.
     *
     * ### Why `leftAdapter` and `rightAdapter` live inside one `Either` adapter (instead of two separate top-level adapters)
     * When Gson deserializes `List<Either<Command, CodeAction>>`, the class it must construct for each element is `Either`,
     * not `Command` or `CodeAction`. This single `Either` adapter acts as a traffic controller: it reads the JSON token/fields,
     * decides which alternative it matches, and delegates to either [leftAdapter] (`TypeAdapter<Command>`) or
     * [rightAdapter] (`TypeAdapter<CodeAction>`) to convert the JSON tree before wrapping the result in [Either.forLeft]
     * or [Either.forRight].
     *
     * ### `create(...)` vs. `read(JsonReader)`
     * - [create]: Runs once per target type (`Either<L, R>`) during Gson's initialization/setup phase to construct the
     *   delegated `leftAdapter` and `rightAdapter` pipeline. The `gson` and `typeToken` parameters here describe the
     *   target Java/Kotlin class, NOT the runtime JSON payload.
     * - [read]: Runs at runtime whenever an incoming JSON message arrives across the wire. The [JsonReader] streams the
     *   live JSON tokens of that specific request or response.
     */
    private class SmartEitherTypeAdapterFactory : TypeAdapterFactory {
        override fun <T : Any?> create(gson: Gson, typeToken: TypeToken<T>): TypeAdapter<T>? {
            if (!Either::class.java.isAssignableFrom(typeToken.rawType)) {
                return null
            }
            val type = typeToken.type
            val (leftType, rightType) = if (type is ParameterizedType) {
                type.actualTypeArguments[0] to type.actualTypeArguments[1]
            } else {
                Any::class.java to Any::class.java
            }
            val leftAdapter = gson.getDelegateAdapter(this, TypeToken.get(leftType))
            val rightAdapter = gson.getDelegateAdapter(this, TypeToken.get(rightType))

            @Suppress("UNCHECKED_CAST")
            return object : TypeAdapter<Either<Any?, Any?>>() {
                override fun write(out: JsonWriter, value: Either<Any?, Any?>?) {
                    if (value == null) {
                        out.nullValue()
                        return
                    }
                    if (value.isLeft) {
                        (leftAdapter as TypeAdapter<Any?>).write(out, value.left)
                    } else {
                        (rightAdapter as TypeAdapter<Any?>).write(out, value.right)
                    }
                }

                override fun read(reader: JsonReader): Either<Any?, Any?>? {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                        return null
                    }
                    val element = JsonParser.parseReader(reader)
                    if (element.isJsonNull) return null

                    if (element.isJsonObject) {
                        val obj = element.asJsonObject
                        val leftRaw = TypeToken.get(leftType).rawType
                        val rightRaw = TypeToken.get(rightType).rawType

                        // Command has "command" and "title", but never "kind", "edit", or "diagnostics".
                        if (Command::class.java.isAssignableFrom(leftRaw) && CodeAction::class.java.isAssignableFrom(rightRaw)) {
                            if (obj.has("command") && !obj.has("kind") && !obj.has("edit") && !obj.has("diagnostics")) {
                                val leftVal = leftAdapter.fromJsonTree(element)
                                return Either.forLeft(leftVal)
                            }
                            val rightVal = rightAdapter.fromJsonTree(element)
                            return Either.forRight(rightVal)
                        }
                        if (CodeAction::class.java.isAssignableFrom(leftRaw) && Command::class.java.isAssignableFrom(rightRaw)) {
                            if (obj.has("command") && !obj.has("kind") && !obj.has("edit") && !obj.has("diagnostics")) {
                                val rightVal = rightAdapter.fromJsonTree(element)
                                return Either.forRight(rightVal)
                            }
                            val leftVal = leftAdapter.fromJsonTree(element)
                            return Either.forLeft(leftVal)
                        }
                    }

                    try {
                        val rightVal = rightAdapter.fromJsonTree(element)
                        if (rightVal != null) return Either.forRight(rightVal)
                    } catch (_: Exception) {}

                    try {
                        val leftVal = leftAdapter.fromJsonTree(element)
                        if (leftVal != null) return Either.forLeft(leftVal)
                    } catch (_: Exception) {}

                    throw JsonParseException("Could not deserialize Either from $element")
                }
            } as TypeAdapter<T>
        }
    }
}
