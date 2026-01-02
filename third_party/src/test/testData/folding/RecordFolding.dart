var r = <fold text='(...)' expand='true'>(
  1,
  2,
)</fold>;

var r2 = <fold text='(...)' expand='true'>(
  a: 1,
  b: 2,
)</fold>;

var r3 = <fold text='(...)' expand='true'>(
  1,
  b: 2,
)</fold>;

var nested = <fold text='(...)' expand='true'>(
  1,
  <fold text='(...)' expand='true'>(
    2,
    3,
  )</fold>,
)</fold>;

var mixed = <fold text='(...)' expand='true'>(
  1,
  a: 2,
  3,
  b: 4,
)</fold>;

var withComments = <fold text='(...)' expand='true'>(
  1, // comment
  2,
)</fold>;
