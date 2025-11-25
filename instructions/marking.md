# Coursework: marking criteria

The coursework is worth 40% of your grade for this module (the remaining 60% is the final exam in January 2026). It consists of one task: writing an interpreter for a simple programming language using Java and ANTLR, graded out of 100 marks. The submitted Java project will be tested on **Oracle OpenJDK 21.0.2**.

The coursework is assessed using the following criteria:

* **Grammar** — up to **45%**  
  Awarded by assessing `SimpleLang.g4` for soundness and completeness with respect to the syntactic specifications.  
  Marks depend on:
    * how many automatic tests (public + unseen) are parsed correctly
    * manual tests and inspection (including malformed input, which must produce ANTLR errors)

* **Interpretation** — up to **45%**  
  Awarded by assessing `SimpleLangInterpreter.java` for soundness and completeness with respect to the semantic rules (excluding typing).  
  Marks depend on:
    * how many automatic tests (public + unseen) produce correct results
    * manual tests and inspection (malformed programs must *not* produce output)

* **Type‑checker** — up to **10%**  
  Awarded based on correctness/completeness of type checking in `SimpleLangInterpreter.java`.  
  Marks depend on:
    * manual tests and inspection
    * note: the type‑checker receives **0%** automatically if:
        * the interpreter does **not** pass all automatic tests, or
        * `TypeChecker.txt` is missing from the submitted zip file

For every automatic test your interpreter fails, markers will inspect your code, and this costs marks.

Automatic tests assume all input programs are well‑formed and well‑typed. Markers also manually check behaviour using malformed or ill‑typed input. They do **not** require detailed error messages—your interpreter must simply detect the error and abort, without producing a normal result.

Further penalties may apply if:

* `SimpleLang.g4` and `SimpleLangInterpreter.java` are not consistent with each other  
  (e.g., overridden methods with no grammar rule, grammar rules with no interpreter method).

Additional penalties for submission issues:

* −2% for every extra file/directory included in the submission zip
* −10% if `TypeChecker.txt` does not match how the type‑checker is actually implemented
* −65% if either `SimpleLang.g4` or `SimpleLangInterpreter.java` is missing or identical to the originals
* −100% if both files are missing or identical to the originals

### Late submission

As per University of Sussex rules:

* −5% if submitted up to 24 hours late
* −10% if submitted >24 hours and up to 7 days late

Late penalties are applied automatically by Canvas.

### Comments

The type‑checker should be implemented **only after** the grammar and interpreter pass all automatic tests. A broken type‑checker can break interpretation and result in:

* lost marks for interpretation
* 0% for type‑checker
* additional penalties

Therefore, save a version of your project **without** the type‑checker in case you need to revert.

There is no strict correlation between number of public tests passed and the final mark. Passing all public tests does **not** guarantee a high mark, because:

* unseen tests may fail
* grammar or interpreter may be incorrect even if tests pass

However, passing many tests makes major bugs less likely.

`TypeChecker.txt` is *not* marked, but must be included to avoid automatically receiving 0% for the type‑checker. An empty `TypeChecker.txt` is treated as an unnecessary file and incurs a −2% penalty.