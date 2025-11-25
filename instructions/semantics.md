Here is your text cleanly formatted in Markdown.

---

# Coursework: The Semantics of the Simple Programming Language
(Interpretation and Typing Rules)

Here we present the *semantics*, including typing rules, of the simple programming language for which you write an interpreter. The semantics are mainly reflected in `SimpleLangInterpreter.java`.

## Semantics and Execution Model

A program is a non‑empty sequence of function declarations. Execution:

* Begins from a function `int main(...)` (located anywhere in the program).
* Ends after the last expression in the body of `main` is executed.
* All function arguments are **passed by value**.

Identifiers visible inside a function:

* Names of all functions (global scope).
* Parameters of the current function.
* Local variables declared at the top of the function body.

Parameters and local variables are **function‑scoped**.  
**Special case:** when initializing a local variable, its identifier must differ from all other parameters and local variables in that function.

The language has **no explicit `return`**:  
the *return value* of a function is the value of the **last expression** in its body.

### Notes on Semantics

* `print` outputs to `System.out`.
* `skip` does nothing.
* `repeat { ... } until cond`
    * Executes its block as long as the condition is false.
    * Condition checked **after** each iteration.
    * Always runs at least once.
* `-` is both unary (negation) and binary (subtraction).
* `~`, `&`, `|`, `^` are boolean NOT, AND, OR, XOR.

### Further Semantic Constraints

* A function cannot reference an uninitialized or undeclared local variable.
* Function calls must pass exactly as many arguments as parameters.
* Exactly one `main` function exists.
* No two functions share the same name.
* Functions may call functions defined before or after them.
* Parameters and local variables within one function cannot share names.
* No parameter or local variable may share a name with a function.

---

## Typing Rules

The language has three types:

* `bool` — booleans
* `int` — integers
* `unit` — a type with exactly one value (similar to Java `Void`)

Typing rules:

* `main` must return `int`.
* A function’s return type must match the type of its last expression.
* Function call arguments must match parameter types.
* Parameters and local variables must be either `int` or `bool`.
* The following expressions have **unit** type:
    * `print x`
    * `space`
    * `newline`
    * `skip`
    * `x := y`
    * `while x do {...}`
    * `repeat {...} until x`
* A function call `f(e1, ..., en)` has the return type of `f`.
* A block `{ e1; ...; eN }` has the type of its last expression `eN`.
* A body `{ init; ...; init; e1; ...; eN }` has the type of `eN`.
* In an `if` expression:
    * `then` and `else` blocks must have the same type.
    * The type of the whole `if` expression is that type.
* Comparisons like `x < y`:
    * Have type `bool`.
    * Both operands must be `int`.
* Arithmetic operations (`x + y`, etc.):
    * Have type `int`.
    * Both operands must be `int`.
* Boolean operations (`x & y`, etc.):
    * Have type `bool`.
    * Both operands must be `bool`.
* Conditions in `if`, `while`, and `repeat` must have type `bool`.
* Loop blocks in `while` and `repeat` must have type `unit`.
* Assignment `x := y`:
    * `x` and `y` must have the same type.
* In `print x`:
    * `x` is either `space`, `newline`, or an `int`.

A program is **well‑typed** if it is syntactically well‑formed and satisfies all semantic constraints and typing rules.