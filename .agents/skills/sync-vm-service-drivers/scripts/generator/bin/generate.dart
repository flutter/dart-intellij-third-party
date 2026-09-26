// This orchestration wrapper is maintained by the Dart IntelliJ plugin.
// The retired Dart SDK generator it invokes is licensed in ../LICENSE.

import 'dart:convert';
import 'dart:io';

import 'package:markdown/markdown.dart';
import 'package:path/path.dart' as path;

import '../lib/common/generate_common.dart';
import '../lib/java/generate_java.dart' as java;

void main(List<String> arguments) {
  if (arguments.length != 2) {
    stderr.writeln('Usage: generate.dart <spec-manifest.json> <output-dir>');
    exitCode = 64;
    return;
  }

  final manifest = jsonDecode(File(arguments[0]).readAsStringSync()) as List;
  final outputRoot = Directory(arguments[1])..createSync(recursive: true);
  final result = <Map<String, Object>>[];

  for (final untypedEntry in manifest) {
    final entry = (untypedEntry as Map).cast<String, Object>();
    final id = entry['id']! as String;
    if (!RegExp(r'^[a-zA-Z0-9._-]+$').hasMatch(id)) {
      throw FormatException('Unsafe specification id: $id');
    }
    final spec = File(entry['path']! as String);
    final nodes = Document().parseLines(spec.readAsLinesSync());
    final output = Directory(path.join(outputRoot.path, id));
    final generator = java.JavaGenerator(output.path);

    // Keep this location stable: it is emitted in generated source headers.
    java.api = java.Api('pkg/vm_service/tool/generate.dart');
    java.api.parse(nodes);
    java.api.generate(generator);

    result.add({
      'id': id,
      'version': ApiParseUtil.parseVersionString(nodes),
      'files': generator.allWrittenFiles.length,
    });
  }

  stdout.writeln(jsonEncode(result));
}
