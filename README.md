# Tyylikas

A linter for Clojure.

> Adjective<br>
> **Tyylikäs**<br>
> tyyli + käs<br>
>
> 1. smart
> 2. stylish
> 3. elegant
> 4. distingue

## Why

[Cljfmt](https://github.com/weavejester/cljfmt) already exists, and it's
implementation looks good. However, it is not configurable enough for
my use and I need tool where reporting the problems is the main use,
and automatically fixing them is secondary. I think reporting
the problems is important so the users are able to learn from their mistakes,
and to fix their editor configuration etc.

## Usage

FIXME


## Example

```
Found 7 problems:

test-resources/core.clj:

Missing whitespace between form elements, line 2, column 20:
(missing-whitespace(foo(bar)))
                   ^

Missing whitespace between form elements, line 2, column 24:
(missing-whitespace(foo(bar)))
                       ^

Bad whitespace on start or end of a list, line 3, column 18:
(bad-whitespace (   foo ( bar ))   )
                 ^

Bad whitespace on start or end of a list, line 3, column 26:
(bad-whitespace (   foo ( bar ))   )
                         ^

Bad whitespace on start or end of a list, line 3, column 30:
(bad-whitespace (   foo ( bar ))   )
                             ^

Bad whitespace on start or end of a list, line 3, column 33:
(bad-whitespace (   foo ( bar ))   )
                                ^

Missing line break between toplevel forms, line 7, column 1:
(defn top-level2 [b]
^
```

## License

Copyright © 2017 Metosin

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
