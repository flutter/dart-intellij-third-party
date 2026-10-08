@Annotation()
part of 'parent.dart';

import 'dart:async';
import 'package:foo/foo.dart' as foo show A, B hide C;
import 'deferred.dart' deferred as d;
import 'config.dart'
    if (dart.library.io) 'config_io.dart'
    if (dart.library.js_interop) 'config_web.dart';
export 'src/exported.dart' show E;
export 'src/conditional.dart' if (dart.library.io) 'src/conditional_io.dart';

part 'sub_part.dart';
@meta
part 'other_sub_part.dart';

class C {
  Future<void> m() async {}
}
