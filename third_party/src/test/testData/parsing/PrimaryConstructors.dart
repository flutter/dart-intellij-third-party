class Point(int x, int y) {
  int x = x;
  int y = y;
}

class Vector(final int x, final int y) {
}

class ConstPoint(this.x, this.y);

class Empty();

class WithBody(int x) {
  int x = x;
}

class Base(var int x);

class WithSuper(super.x) extends Base;

interface class Interface {}

class WithInterfaces(int x) implements Interface;

mixin Mixin {}

class WithMixins(int x) with Mixin;

class Complex(super.x, [int y = 0]) extends Base with Mixin implements Interface {
  int z = x + y;
}

class Named.origin(int x, int y);

class Private._(int x);

enum MyEnum(final int value) {
  one(1),
  two(2);
}
