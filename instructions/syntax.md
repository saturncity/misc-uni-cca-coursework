Here is your text cleanly formatted in Markdown.

---

# Coursework: The Syntax of a Simple Programming Language

Here we present the syntactic language specifications of the simple programming language for which you have to build the interpreter. The syntax is mainly reflected in the file `SimpleLang.g4` in the interpreter.

## Lexical Description

### Token Classes / Lexemes

The lexical units of the language are keywords, integers, identifiers, special symbols, and white spaces. Any input string that contains only those components is lexically valid.

* **Keywords**  
  `if`, `then`, `else`, `while`, `do`, `repeat`, `until`, `print`, `space`, `newline`, `skip`, `int`, `bool`, `unit`, `true`, `false`.  
  Keywords are case-sensitive. Each keyword is a token class with one inhabitant.

* **Integers**  
  Either `0`, or an optional `-` followed by a non‑empty string of digits not beginning with `0`.

* **Identifiers**  
  Letters (a–z, A–Z), digits, and `_`, but not matching a keyword.  
  Must begin with a **lower‑case letter**.

* **Operators and Delimiters**  
  One- or two-character symbols:  
  `;  (  )  ==  <  >  <=  >=  ,  {  }  :=  +  *  -  /  &  |  ^  ~`

* **White Space**  
  Not part of any token. Includes:  
  space (` `), newline (`\n`), carriage return (`\r`), tab (`\t`).

Because whitespace is not part of a token, it always separates tokens. But tokens need not be separated by whitespace.

Examples:

* `(())` → four tokens: `(` `(` `)` `)`
* `65x` → integer `65`, identifier `x`
* `65if;` → `65`, `if`, `;`

Operator characters also separate identifiers and integers.

### Disambiguation

Lexical rules are ambiguous, so apply:

* **Longest match**  
  Choose the token that consumes the most characters.

* **Keyword priority**  
  If ties occur, prefer keywords.

Examples:

* `iff` → identifier `iff`, not `if` + `f`
* `>==` → `>=` + `=`, not `>` + `==`

---

## Syntactic Description
(Grammar of the Simple Programming Language)

The syntax is given by this context‑free grammar.  
Initial non-terminal: `PROG`.  
Non-terminals in CAPS.  
Terminals in quotes.

```
PROG   → DEC+
DEC    → TYPE IDFR "(" VARDEC ")" BODY
VARDEC → (TYPE IDFR ("," TYPE IDFR)*)?
BODY   → "{" (TYPE IDFR ":=" EXP ";")* ENE "}"
BLOCK  → "{" ENE "}"
ENE    → EXP (";" EXP)*

EXP → IDFR
     | INTLIT
     | BOOLLIT
     | IDFR ":=" EXP
     | "(" EXP BINOP EXP ")"
     | UNOP EXP
     | IDFR "(" ARGS ")"
     | BLOCK
     | "if" EXP "then" BLOCK "else" BLOCK
     | "while" EXP "do" BLOCK
     | "repeat" BLOCK "until" EXP
     | "print" EXP
     | "space"
     | "newline"
     | "skip"

ARGS   → (EXP ("," EXP)*)?

BINOP  → "==" | "<" | ">" | "<=" | ">="
        | "+" | "-" | "*" | "/" | "&" | "|" | "^"

UNOP   → "-" | "~"

TYPE   → "int" | "bool" | "unit"

IDFR    → (an identifier)
INTLIT  → (an integer)
BOOLLIT → "true" | "false"
```

Notes:

* `-` is both unary (`UNOP`) and binary (`BINOP`).
* The grammar uses `*`, `+`, `?` as repetition operators (ANTLR style), so not strictly context‑free.

A program is **well‑formed** if it satisfies all lexical and syntactical rules.

---

## Example Programs

### Example 1
```
int main()
{
    0
}
```

### Example 2
```
int fun(int x, int y, int z) {
  if (x == y) then { z } else { 0 } }

int main() { fun(1, 2, 3) }
```

### Example 3
```
int main() { fibo(10) }
int fibo(int n) {
  if (n < 2)
  then { n }
  else { (fibo((n - 1)) + fibo((n - 2))) } }
```

### Example 4
```
unit doLoop (int i, int a) {
  while (i <= 2) do {
    a := (a + i);
    i := (i + 1) };
  print a }

int main() {
  doLoop(0, 5);
  1337 }
```

### Example 5
```
int main(int n)
{
    int a := 0;
    int i := 1;
    while (i <= n) do {
        a := (a + i);
        i := (i + 1)
    };
    a
}
```

### Example 6
```
int main(int n) {
    int i := 0;
    while (i < n) do {
        print_row(i);
        i := (i + 1)
    };
    0
}
unit print_row(int n) {
    int i := 0;
    while (i < n) do {
        print 1;
        i := (i + 1)
    };
    print newline
}
```