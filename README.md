> [!WARNING]
> This is a University of Sussex compilers coursework from November 2025, and I haven't touched it since I handed it in: an interpreter for SimpleLang, a small expression-oriented language, written in Java on top of an ANTLR 4 grammar. All 16 bundled test programs pass, and eleven things in it are wrong anyway. The three worth knowing before you read any of it: chained comparisons like `(1 < 2 < 0)` silently ignore every operand past the second, the grammar accepts unparenthesized arithmetic that the language spec forbids, and a Javadoc comment claims short-circuit evaluation that the code underneath doesn't do. The full list is in [Known issues](#known-issues). I'm leaving them there, because what makes this worth keeping is the record of what I could build at the time.

<div align="center">

![Java](https://img.shields.io/badge/Java%2021-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![ANTLR](https://img.shields.io/badge/ANTLR%204.13.1-C22D40?style=for-the-badge)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)
![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-000000?style=for-the-badge&logo=intellijidea&logoColor=white)

![Dependencies](https://img.shields.io/badge/dependencies-1%2C%20vendored-6E6E6E?style=for-the-badge)
![License](https://img.shields.io/badge/license-MIT-3DA639?style=for-the-badge)

</div>

## About

The module handed out a deliberately broken skeleton project and asked for two files back: `SimpleLang.g4`, the ANTLR grammar, and `SimpleLangInterpreter.java`, a tree-walking visitor over the parse tree it produces. Everything else here came with the skeleton and I wasn't allowed to change it, including both `main` methods. The grade split was 45% grammar, 45% interpretation, 10% type checker.

SimpleLang has no statements. A program is a list of function declarations, a function body is a semicolon-separated sequence of expressions, and the function returns whatever the last one evaluated to. There's no `return` keyword. `while`, `repeat ... until`, `if ... then ... else`, `print` and `skip` are all expressions, the loops and `print` having type `unit`. Three types exist, `int`, `bool` and `unit`, and every one of them is a Java `Integer` at runtime: `bool` is 0 or 1, `unit` is 0.

The part I'd defend is the grammar rewrite. The spec writes every binary operation as fully parenthesized, `"(" EXP BINOP EXP ")"`, which makes precedence somebody else's problem. I threw that away and wrote a six-level cascade instead, logic to comparison to additive to multiplicative to unary to primary, so `1 + 2 * 3` parses to 7 without a single bracket. Every program the spec describes still parses. The cost is that the grammar now accepts programs the spec says are malformed, which is the wrong direction to be wrong in for a coursework marked on soundness.

The other grammar change was subtler and I only found the need for it by breaking a test. The skeleton lexed integers as `'0' | ('-'? [1-9][0-9]*)`, so `-1` was a single token. Feed that rule `(a -1)` and ANTLR's longest-match takes `a` and then the literal `-1`, giving two expressions with no operator between them and a parse error, purely because of a missing space. I dropped the `-` from the token and let unary minus handle negatives. `(a -1)` is now 9 when `a` is 10.

- A 164-line ANTLR 4.13.1 grammar with a six-level precedence cascade and no parentheses required
- A 1,173-line visitor implementing 42 `visit` methods over `SimpleLangVisitor<Integer>`
- Call frames as a `Stack<Map<String, Integer>>`, arguments passed by value, so recursion works
- A type checker that runs as a complete pass before any interpretation, not interleaved with it
- Function signatures collected up front, so a function can call one declared below it
- Checks for arity, argument types, operand types, matching `if` branch types, `unit`-typed loop bodies, duplicate names across functions, parameters and locals, and reads of undeclared variables
- 16 test programs with argument and expected-answer files, 5 of which I wrote

## Tech stack

| Layer | Technology | Why it's here |
| --- | --- | --- |
| Language | Java 21 | The module said it would mark on Oracle OpenJDK 21.0.2. I built and ran this on Microsoft OpenJDK 21.0.9. The source uses switch arrow-cases, `var` and pattern-matching `instanceof`, so 21 is close to a real floor |
| Parser generator | ANTLR 4.13.1 | Generates the lexer, parser and visitor interface from `SimpleLang.g4`. The 2.0MB complete jar is committed to the repo, so there's nothing to download |
| Interpreter | `AbstractParseTreeVisitor<Integer>` | ANTLR's own visitor base class. One `Integer` return type for all three SimpleLang types, which is the skeleton's design and not mine |
| Build | Gradle (declared only) | `task1/build.gradle` defines `run` and `runtests` tasks, but no wrapper is committed and the build has no ANTLR generation step, so neither task works from a fresh clone. The `javac` route under [Running](#running) is what I verified |
| Tests | `Task1Tester.java` | Supplied with the skeleton. No JUnit, no assertions. It diffs stdout against `.answers` files and prints to stderr when they disagree |
| IDE | IntelliJ IDEA with the ANTLR plugin | How I worked. The project files aren't committed, and you don't need the IDE to build this |

## What it looks like

It's a terminal program, so there's nothing to screenshot. Everything below is real output on OpenJDK 21.0.9, from the commands in [Running](#running).

`Task1Tester` walks `task1tests/`, runs each `.simp` program once per line of its `.args` file, and compares the collected stdout to the `.answers` file. Mismatches go to stderr. Here's the whole suite, and stderr stayed empty:

```text
Trying testcase 024.simp
Trying testcase 008.simp
Trying testcase 00000.simp
Trying testcase 00020.simp
Trying testcase 00021.simp
Trying testcase 00001.simp
Trying testcase 025.simp
Trying testcase 00026.simp
Trying testcase 022.simp
Trying testcase 00006.simp
Trying testcase 023.simp
Trying testcase 00027.simp
Trying testcase 00004.simp
Trying testcase 00005.simp
Trying testcase 00002.simp
Trying testcase 00003.simp
```

`022.simp` is trial division, and it's the test that exercises the most of the language at once: nested `while` loops, a `bool` local used as a flag, integer division standing in for a modulo operator the language doesn't have, and `print` of both an int and a `newline`. Each line of output is one prime factor repeated as many times as it divides in, so 13568541 comes out as 3 times 7 times 7 times 241 times 383. The trailing `0` is `main`'s return value, which `Task1` prints after `NORMAL_TERMINATION`:

```text
3 
7 7 
241 
383 

NORMAL_TERMINATION
0
```

`00026.simp` takes two `bool` arguments and prints `a`, `b`, `a & b`, `a | b`, `a ^ b`, converting each to an int through a helper function first, since `print` only accepts `int`, `space` and `newline`. With `true false` the five columns read 1, 0, 0, 1, 1, and the final `1` is `(convert(~a) + convert(~b))`:

```text
1 0 0 1 1
NORMAL_TERMINATION
1
```

The type checker runs before anything executes, so a program with a type error produces no program output at all, only the throw. Adding an `int` to a `bool`:

```text
Exception in thread "main" java.lang.RuntimeException: TYPE ERROR: Arithmetic operators (+, -) require int operands
```

Reading a variable that was never declared:

```text
Exception in thread "main" java.lang.RuntimeException: TYPE ERROR: Variable 'y' not declared
```

## Getting started

### Prerequisites

- **A JDK, 21 or newer.** The coursework was marked on Oracle OpenJDK 21.0.2 and I used Microsoft OpenJDK 21.0.9. Below 21 the source won't compile, because it uses `var` in enhanced `for` loops, arrow-form `switch` and pattern-matching `instanceof`.
- **Nothing else.** No package manager, no network access, no environment variables, no database, no ports. ANTLR is the only dependency and its jar is committed at the repo root.
- **Not Gradle, and not IntelliJ.** `task1/build.gradle` exists and declares tasks, but no wrapper is committed and the build never invokes ANTLR, so a fresh clone can't use it. Use `javac` as below.

### Installation

```bash
git clone https://github.com/saturncity/misc-uni-cca-coursework.git
cd misc-uni-cca-coursework
```

Generate the lexer, parser and visitor from the grammar. You have to do this before anything compiles, because `.gitignore` excludes all ten generated files and a fresh clone has none of them:

```bash
cd task1/src
java -jar ../../antlr-4.13.1-complete.jar -visitor -no-listener SimpleLang.g4
cd ../..
```

Then compile the generated files and the four hand-written ones together:

```bash
javac -cp antlr-4.13.1-complete.jar -d out task1/src/*.java
```

On Windows, swap the classpath separator from `:` to `;` in that command and in every one below.

### Running

Run the whole test suite. Test names go to stdout as it goes; any mismatch between actual and expected output goes to stderr, along with both texts:

```bash
java -cp out:antlr-4.13.1-complete.jar Task1Tester
```

`Task1` reads one program from stdin and takes that program's `main` arguments on the command line. Pipe a file in:

```bash
java -cp out:antlr-4.13.1-complete.jar Task1 13568541 < task1tests/022.simp
```

Or type a program straight into the terminal and press Ctrl+D to close stdin, which is what the coursework instructions assume:

```bash
java -cp out:antlr-4.13.1-complete.jar Task1
```

There's no server and no port. `out/` and every generated ANTLR file are in `.gitignore`, so neither shows up in `git status`.

## Project structure

```text
.
├── task1/
│   ├── src/
│   │   ├── SimpleLang.g4              # the grammar. One of the two files I wrote
│   │   ├── SimpleLangInterpreter.java # visitor + type checker. The other one. 1,173 lines
│   │   ├── Task1.java                 # supplied. Reads a program from stdin, runs it, prints the result
│   │   ├── Task1Tester.java           # supplied. Runs everything in task1tests/ and diffs the output
│   │   └── TypeChecker.txt            # a 100-word prose summary of the type checker, required at submission
│   └── build.gradle                   # declares run and runtests. No wrapper committed, no ANTLR step
├── task1tests/                        # 16 programs, each with a .simp.args and a .simp.answers alongside
├── instructions/                      # the coursework brief: syntax, semantics, marking, submission rules
├── antlr-4.13.1-complete.jar          # 2.0MB, vendored so the project builds with no network
├── ANTLR_LICENSE.txt                  # BSD 3-clause, covering the jar above
├── settings.gradle                    # supplied, and the brief said not to touch it
├── LICENSE
└── README.md
```

## Known issues

There are no `TODO` or `FIXME` comments anywhere in the source. I found everything below by reading the code and then confirming each one against a run. I'm not fixing any of it.

**Wrong answers**

1. **Chained comparisons drop every operand past the second.** `compareExp` is written as `additiveExp ( op additiveExp )*`, so three operands parse fine, but `visitCompareExp` reads `additiveExp(0)` and `additiveExp(1)` and returns. `inferCompareExpType` makes the same mistake, so the type checker doesn't catch it either. `(1 < 2 < 0)` and `(1 < 2 < 99999)` both take the `then` branch, because both reduce to `1 < 2`. `visitLogicExp` right above it loops over every operand properly, so `(true & true & false)` is correctly false. Chained comparisons aren't legal SimpleLang, so this only fires on input the grammar shouldn't have accepted in the first place, which is issue 3.
2. **`main`'s arguments are counted but never type-checked.** `checkSemanticConstraints` compares `args.length` to the parameter count and stops there, and `visitProgram` then parses anything that isn't `true` or `false` with `Integer.parseInt`. So `int main(bool b)` accepts `5`, binds 5 to a `bool`, and `if b` treats it as true. Every other call site in the language does check argument types; this is the one that doesn't.

**The grammar is looser than the spec**

3. **Unparenthesized binary operations parse.** The spec's `EXP` rule only admits `"(" EXP BINOP EXP ")"`, and I replaced it with a precedence cascade. `1 + 2 * 3` evaluates to 7 instead of being rejected. The marking criteria award grammar marks partly on rejecting malformed input, so this was a risk I took knowingly and would take again, since the alternative is a grammar that can't express precedence at all.
4. **`binop` is a dead rule.** Twelve labeled alternatives, referenced by nothing, left over from the skeleton after the cascade replaced it. The interpreter still carries all twelve `visit*Binop` methods returning `null` to satisfy the generated interface. The brief specifically warns that grammar rules with no corresponding interpretation cost marks, and I left it in anyway. `unop` next to it is real and is used by `unaryExp`.

**Comments that lie**

5. **`visitLogicExp`'s Javadoc claims short-circuit evaluation.** It says "Implements short-circuit evaluation for boolean operators" directly above a loop that calls `visit` on the right operand before it looks at the operator. A program where the right-hand side of `false & ...` prints something still prints it. Nothing in SimpleLang has side effects that a test would catch, which is exactly why the comment survived.

**Crashes on malformed input**

6. **A recovered parse error still prints `NORMAL_TERMINATION`.** `Task1.java` never checks `parser.getNumberOfSyntaxErrors()`, so when ANTLR recovers rather than giving up, the program runs and reports success. `int main() { 42` prints `missing '}' at '<EOF>'` to stderr and then `NORMAL_TERMINATION` and `42` to stdout. So does `int main() { 42 } @@@`. The marking criteria say malformed programs must not produce output. This one's in supplied code that the brief forbade me from editing and that isn't part of the submission, so the markers would have hit it with everybody's grammar, but it's the reason you can't trust a `NORMAL_TERMINATION` on its own.
7. **Deeper syntax errors throw a `NullPointerException` instead of aborting cleanly.** `getPrimary` walks down the cascade taking child 0 at each level, and after error recovery some of those children are null. `int main() { 1 +++ }` ends in `Cannot invoke "SimpleLangParser$UnaryExpContext.primaryExp()" because "<parameter1>" is null`. It aborts without output, which is what the criteria require, so it costs nothing. It reads as a crash rather than a rejection, which is the part I'd change.
8. **Division by zero is an unguarded `ArithmeticException`.** `(1 / 0)` throws straight out of `visitMultiplicativeExp`. SimpleLang doesn't define the behavior, so aborting is defensible, but it's a Java stack trace rather than anything the interpreter chose to say.

**Leftovers**

9. **17 visitor methods are marked `// not used` and return `null` or throw.** The twelve binop labels, `visitVardec`, `visitType`, `visitProg`, and both `unop` labels. ANTLR generates one interface method per rule and per label, so they have to exist; `visitProg` in particular throws `"Should not be here!"` because the real entry point is the hand-written `visitProgram` that takes command-line arguments alongside the tree.
10. **`visitAssignExpr` calls `put` where the skeleton called `replace`.** `put` creates the variable if it's missing instead of doing nothing, so an assignment to an undeclared name would silently succeed at runtime. The type checker rejects that program first, which is the only reason it doesn't matter.
11. **Neither the Gradle wrapper nor the generated ANTLR sources are committed.** So a clone gets a `build.gradle` that can't run and a source tree that can't compile until you run the generator by hand. That's what the extra step in [Installation](#installation) is for.

## Contributing

I'm not taking changes to this one, and I'm not patching the list above, since the point of keeping it is the record rather than the code. Fork it if a piece is useful. If you want the interesting part, it's the precedence cascade in `SimpleLang.g4` and the way `visitCompareExp` and `inferCompareExpType` both stopped at two operands without either of them noticing the other had done the same.

## License

MIT. See [LICENSE](LICENSE) for the full text. The bundled `antlr-4.13.1-complete.jar` is ANTLR's own, under the BSD 3-clause license in [ANTLR_LICENSE.txt](ANTLR_LICENSE.txt), and the coursework brief in [instructions/](instructions/) is the University of Sussex's, reproduced here for context rather than relicensed.
