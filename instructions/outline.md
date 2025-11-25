# Coursework: ANTLR and the Java project

The task of the coursework is to write an interpreter for a simple programming language whose syntactic and semantic (including typing) specifications are given here and here, respectively. The interpreter should translate any program written in the simple programming language into Java and execute it.

The main resource is a buggy “skeleton” Java project that you can download in a zip format. It implements an interpreter for the simple programming language. It is strongly suggested to work on it using IntelliJ with the ANTLR plugin.

The unzipped project contains:

* a file containing gradle settings (do not change it)
* the file `antlr-4.13.1-complete.jar` to use ANTLR if you cannot install the IntelliJ plugin
* directory `task1` containing `SimpleLang.g4`, `SimpleLangInterpreter.java`, and other Java files
* directory `task1tests` containing programs written in the simple language to test your interpreter

The project is:

* unsound — some token rules, production rules, and interpretations are incorrect
* incomplete — not all required tokens, productions, or semantic interpretations are implemented

The project also includes **no type-checking**.

The goal is to make the interpreter sound and complete with respect to the syntax and semantics (including typing rules). The project uses ANTLR to reduce the amount of handwritten parsing code. The ANTLR plugin for IntelliJ is free and included.

We assume the project is run on **OpenJDK 21** (Oracle or any vendor like Microsoft or Amazon Corretto). If you want to use another Java version, contact the markers.

## Configuring ANTLR on IntelliJ

Make sure the **root** of your IntelliJ project is the folder `coursework` (containing directories `task1`, `task1tests`, `settings.gradle`, and `antlr-4.13.1-complete.jar`).

If you followed the ANTLR labs, these steps should be familiar:

* See Exercise 3 from Week 1 lab to install/configure ANTLR plugin.
* See Exercise 3 from Week 4 lab to use the ANTLR parse-tree visualiser.

## How to run the project

The project contains two files with a `main` method: `Task1.java` and `Task1Tester.java`.

* `Task1.java`  
  Builds the interpreter, waits for an input program typed into the terminal, then executes it.

* `Task1Tester.java`  
  Builds the interpreter and runs *all* programs in the `task1tests` directory.

When running `Task1.java`, paste your program into the terminal and press **Ctrl + D** to mark end-of-input.  
Arguments for the program can be supplied through IntelliJ Run/Debug Configurations.

After executing the program, the interpreter prints:

1. a separator line
2. `NORMAL_TERMINATION`
3. the return value of `main` (an integer)

When running `Task1Tester.java`, each test in `task1tests` is executed. If the output differs from the expected answer, the tester prints both outputs with an error message.

## How to develop the project

You must modify:

* `SimpleLang.g4` — define the grammar (syntax)
* `SimpleLangInterpreter.java` — define interpretation + type checker (semantics)

Do **not** modify project structure or Gradle settings.

Right‑click `SimpleLang.g4` → “Generate ANTLR Recognizer”. This regenerates lexer/parser files, but *not* `SimpleLangInterpreter.java`; that file must be updated manually whenever the grammar changes.

Every production rule in `SimpleLang.g4` corresponds to a visitor method in `SimpleLangInterpreter.java`. Keep them in sync at all times.

### Tests

Tests are located in `task1tests`. Each test consists of:

* `.simp` — program source
* `.simp.args` — input arguments
* `.simp.answers` — expected output

You can create your own tests following this pattern.

Running `Task1Tester.java` shows which tests you pass.

## Some technical details

* Only `SimpleLangInterpreter.java` and `SimpleLang.g4` must be modified. Other Java files may be complex—ignore them.
* If unclear, consult Java documentation. You will likely use:
    * `Integer` — encoding types `bool`, `int`, `unit`
    * `HashMap` / `Map` — environments for variable values
    * `Stack` — representing call frames
* The grammar (start variable `prog`) is named `SimpleLang` in `SimpleLang.g4`. ANTLR generates `SimpleLangLexer` and `SimpleLangParser`.
* Do not modify generated files (`SimpleLangLexer.java`, `SimpleLangParser.java`, etc.).
* `SimpleLangInterpreter` implements `SimpleLangVisitor<Integer>` and has an extra method `visitProgram` returning the integer result of `main`. It takes:
    * the parse tree root (`SimpleLangParser.ProgContext`)
    * an array of strings representing arguments (each must be an `INTLIT` or `BOOLLIT`, matching `main`’s parameter list).