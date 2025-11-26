import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Task1SelfTester {

    enum ExpectedOutcome { PASS, FAIL, FATAL }
    enum ActualOutcome   { PASS, FAIL, FATAL }

    static class TestRecord {
        String name;
        boolean isCustom;
        ExpectedOutcome expected;
        ActualOutcome actual;
        String expectedOutput;
        String actualOutput;
        Throwable exception;
        String programCode;
        List<String[]> inputs;

        TestRecord(String name, boolean isCustom,
                   ExpectedOutcome expected, ActualOutcome actual,
                   String expectedOutput, String actualOutput,
                   Throwable exception,
                   String programCode,
                   List<String[]> inputs)
        {
            this.name = name;
            this.isCustom = isCustom;
            this.expected = expected;
            this.actual = actual;
            this.expectedOutput = expectedOutput;
            this.actualOutput = actualOutput;
            this.exception = exception;
            this.programCode = programCode;
            this.inputs = inputs;
        }
    }

    static class TestCase {
        String name;
        String program;
        List<String> argLines;    // now multiple lines like .args files
        String expectedOutput;
        ExpectedOutcome expected;

        TestCase(String name, String program, List<String> argLines,
                 String expectedOutput, ExpectedOutcome expected)
        {
            this.name = name;
            this.program = program;
            this.argLines = argLines;
            this.expectedOutput = expectedOutput;
            this.expected = expected;
        }
    }

    // ==========================================================
    // PARSE PROGRAM
    // ==========================================================
    private static SimpleLangParser.ProgContext parseProgram(String filePath) throws Exception {
        CharStream input = CharStreams.fromFileName(filePath);
        SimpleLangLexer lexer = new SimpleLangLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SimpleLangParser parser = new SimpleLangParser(tokens);

        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> r, Object o,
                                    int line, int pos, String msg,
                                    RecognitionException e)
            {
                throw new ParseCancellationException("Syntax error: " + msg);
            }
        });
        return parser.prog();
    }

    // ==========================================================
    // RUN PROGRAM
    // ==========================================================
    private static ActualOutcome runProgram(SimpleLangParser.ProgContext tree,
                                            String[] args,
                                            StringBuilder out) throws Exception
    {
        PrintStream old = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        try {
            System.setOut(ps);
            SimpleLangInterpreter interpreter = new SimpleLangInterpreter();
            Integer ret = interpreter.visitProgram(tree, args);

            System.out.println();
            System.out.println("NORMAL_TERMINATION");
            System.out.println(ret);
            return ActualOutcome.PASS;
        }
        finally {
            System.out.flush();
            System.setOut(old);
            out.append(baos.toString());
        }
    }

    // ==========================================================
    // FILE LOADING HELPERS
    // ==========================================================
    private static ExpectedOutcome readExpectedOutcome(String base) {
        File meta = new File(base + ".expect");
        if (!meta.exists()) return ExpectedOutcome.PASS;
        try (BufferedReader br = new BufferedReader(new FileReader(meta))) {
            return ExpectedOutcome.valueOf(br.readLine().trim().toUpperCase());
        } catch (Exception e) {
            return ExpectedOutcome.PASS;
        }
    }

    private static String readExpectedOutput(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return br.lines().collect(Collectors.joining("\n")).trim().replace("\r", "");
        } catch (Exception e) {
            return "";
        }
    }

    private static String readProgramText(String path) {
        try {
            return new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(path)));
        } catch (Exception e) {
            return "";
        }
    }

    // ==========================================================
    // CLASSIFY OUTCOME
    // ==========================================================
    private static ActualOutcome classifyOutcome(Throwable thrown,
                                                 String expected,
                                                 String actual)
    {
        if (thrown != null)
            return ActualOutcome.FATAL;
        if (!actual.equals(expected))
            return ActualOutcome.FAIL;
        return ActualOutcome.PASS;
    }

    private static boolean isUnexpected(ExpectedOutcome expected, ActualOutcome actual)
    {
        if (expected == ExpectedOutcome.PASS)  return actual != ActualOutcome.PASS;
        if (expected == ExpectedOutcome.FAIL)  return actual != ActualOutcome.FAIL;
        if (expected == ExpectedOutcome.FATAL) return actual != ActualOutcome.FATAL;
        return true;
    }

    // ==========================================================
    // MAIN
    // ==========================================================
    public static void main(String[] args) {

        List<TestRecord> records = new ArrayList<>();

        int total = 0;
        int passed = 0;
        int failed = 0;
        int fatal  = 0;

        try {
            // ==========================================================
            // FILE TESTS
            // ==========================================================
            File dir = new File("./task1tests/");
            File[] tests = dir.listFiles((d, n) -> n.endsWith(".simp"));

            if (tests != null) {
                for (File simp : tests) {

                    total++;
                    String base = simp.getAbsolutePath();
                    ExpectedOutcome expected = readExpectedOutcome(base);
                    String programText = readProgramText(base);

                    Throwable thrown = null;
                    String actualOutput = "";
                    List<String[]> usedInputs = new ArrayList<>();

                    try {
                        SimpleLangParser.ProgContext tree = parseProgram(base);

                        // Load .args file
                        File argFile = new File(base + ".args");
                        List<String> argLines;
                        try (BufferedReader br = new BufferedReader(new FileReader(argFile))) {
                            argLines = br.lines().collect(Collectors.toList());
                        }

                        if (argLines.isEmpty())
                            argLines = List.of("");

                        StringBuilder sb = new StringBuilder();

                        for (String line : argLines) {
                            String[] arr = (line.trim().isEmpty()
                                    ? new String[]{}
                                    : line.trim().split("\\s+"));
                            usedInputs.add(arr);
                            runProgram(tree, arr, sb);
                        }

                        actualOutput = sb.toString().trim().replace("\r", "");
                    }
                    catch (Throwable t) {
                        thrown = t;
                    }

                    String expectedOutput = readExpectedOutput(base + ".answers");

                    ActualOutcome actual = classifyOutcome(thrown, expectedOutput, actualOutput);
                    boolean ok = !isUnexpected(expected, actual);

                    if (ok) passed++;
                    else if (actual == ActualOutcome.FATAL) fatal++;
                    else failed++;

                    records.add(new TestRecord(
                            simp.getName(), false, expected, actual,
                            expectedOutput, actualOutput,
                            thrown, programText, usedInputs
                    ));
                }
            }

            // ==========================================================
            // CUSTOM TESTS — NOW ARGUMENTS WORK LIKE .simp.args
            // ==========================================================
            List<TestCase> custom = new ArrayList<>();

            // Simple arithmetic tests

            custom.add(new TestCase(
                    "arith_add",
                    "int main(){ (1+2) }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n3\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "arith_sub",
                    "int main(){ (10-3) }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n7\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "arith_mul",
                    "int main(){ (2*8) }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n16\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "arith_div",
                    "int main(){ (20/5) }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n4\n",
                    ExpectedOutcome.PASS
            ));

            // Boolean logic tests

            custom.add(new TestCase(
                    "bool_literal_true",
                    "int main(){ if true then {1} else {0} }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n1\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "bool_and",
                    "int main(){ if (true & false) then {1} else {0} }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n0\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "bool_or",
                    "int main(){ if (true | false) then {1} else {0} }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n1\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "bool_not",
                    "int main(){ if (~false) then {1} else {0} }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n1\n",
                    ExpectedOutcome.PASS
            ));

            // Comparison tests

            custom.add(new TestCase(
                    "cmp_lt",
                    "int main(){ if (3<5) then {1} else {0} }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n1\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "cmp_eq",
                    "int main(){ if (5==5) then {1} else {0} }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n1\n",
                    ExpectedOutcome.PASS
            ));

            // If-else tests

            custom.add(new TestCase(
                    "if_else_true",
                    "int main(){ if true then {7} else {9} }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n7\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "if_else_false",
                    "int main(){ if false then {7} else {9} }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n9\n",
                    ExpectedOutcome.PASS
            ));

            // Blocks

            custom.add(new TestCase(
                    "block_nested",
                    "int main(){ {1;2;3} }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n3\n",
                    ExpectedOutcome.PASS
            ));

            // Assignments and variables

            custom.add(new TestCase(
                    "assign_basic",
                    "int main(){ int x:=1; x:=5; x }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n5\n",
                    ExpectedOutcome.PASS
            ));

            // Print

            custom.add(new TestCase(
                    "print_int",
                    "int main(){ print 3; 9 }",
                    List.of(""),
                    "3\n\nNORMAL_TERMINATION\n9\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "print_space",
                    "int main(){ print space; 1 }",
                    List.of(""),
                    " \n\nNORMAL_TERMINATION\n1\n",
                    ExpectedOutcome.PASS
            ));

            // While tests

            custom.add(new TestCase(
                    "while_counter",
                    "int main(){ int i:=0; while(i<3)do{ i:=(i+1) }; i }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n3\n",
                    ExpectedOutcome.PASS
            ));

            // Repeat tests

            custom.add(new TestCase(
                    "repeat_counter",
                    "int main(){ int x:=0; repeat{ x:=(x+1) }until(x==3); x }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n3\n",
                    ExpectedOutcome.PASS
            ));

            // Function call tests

            custom.add(new TestCase(
                    "func_one_arg",
                    "int f(int x){ x } int main(){ f(7) }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n7\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "func_two_args",
                    "int add(int x,int y){(x+y)} int main(){ add(3,4) }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n7\n",
                    ExpectedOutcome.PASS
            ));

            // Unit function tests

            custom.add(new TestCase(
                    "unit_noop",
                    "unit p(){ skip } int main(){ p(); 5 }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n5\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "unit_print",
                    "unit p(){ print 1 } int main(){ p(); 0 }",
                    List.of(""),
                    "1\n\nNORMAL_TERMINATION\n0\n",
                    ExpectedOutcome.PASS
            ));

            // Argument passing tests

            custom.add(new TestCase(
                    "args_passthrough",
                    "int main(int x){ x }",
                    List.of("5","0","-3"),
                    "\n\nNORMAL_TERMINATION\n5\n\nNORMAL_TERMINATION\n0\n\nNORMAL_TERMINATION\n-3\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "args_add",
                    "int main(int a,int b){(a+b)}",
                    List.of("1 2","10 15"),
                    "\n\nNORMAL_TERMINATION\n3\n\nNORMAL_TERMINATION\n25\n",
                    ExpectedOutcome.PASS
            ));

            // Fatal error tests

            custom.add(new TestCase(
                    "fatal_syntax",
                    "int main( { 5 }",
                    List.of(""),
                    "",
                    ExpectedOutcome.FATAL
            ));

            custom.add(new TestCase(
                    "fatal_type",
                    "int main(){ true }",
                    List.of(""),
                    "",
                    ExpectedOutcome.FATAL
            ));

            custom.add(new TestCase(
                    "fatal_undeclared",
                    "int main(){ x }",
                    List.of(""),
                    "",
                    ExpectedOutcome.FATAL
            ));

            custom.add(new TestCase(
                    "fatal_bad_call",
                    "int f(int x){x} int main(){ f(1,2) }",
                    List.of(""),
                    "",
                    ExpectedOutcome.FATAL
            ));

            // Complex program tests

            custom.add(new TestCase(
                    "complex_factorial_recursive",
                    "int fact(int n){ if(n<2) then {1} else {(n*fact((n-1)))} } int main(){ fact(7) }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n5040\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "complex_fibonacci_recursive",
                    "int fib(int n){ if(n<2) then {n} else {(fib((n-1))+fib((n-2)))} } int main(){ fib(10) }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n55\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "complex_gcd_euclid_iterative",
                    "int gcd(int a,int b){ while(b>0)do{ int t:=b; b:=(a%b); a:=t }; a } int main(){ gcd(270,192) }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n6\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "complex_nested_loops_accumulator",
                    "int main(){ int s:=0; int i:=0; while(i<4)do{ int j:=0; while(j<5)do{ s:=(s+(i*j)); j:=(j+1) }; i:=(i+1) }; s }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n30\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "complex_repeat_and_function_mix",
                    "int inc(int x){(x+1)} int main(){ int v:=0; repeat{ v:=(inc(v)) }until(v==5); v }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n5\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "complex_boolean_chain_conditions",
                    "int main(){ if((1<2)&(2<3)&(~false)) then { (10*2) } else { 0 } }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n20\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "complex_print_matrix_pattern",
                    "int main(){ int i:=1; while(i<=4)do{ int j:=0; while(j<i)do{ print i; j:=(j+1) }; print newline; i:=(i+1) }; 0 }",
                    List.of(""),
                    "1\n\n2\n2\n\n3\n3\n3\n\n4\n4\n4\n4\n\nNORMAL_TERMINATION\n0\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "complex_two_function_chain",
                    "int f(int x){(x*2)} int g(int y){(f((y+3)))} int main(){ g(5) }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n16\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "complex_argument_driven_loops",
                    "int main(int n){ int s:=0; int i:=0; while(i<n)do{ s:=(s+i); i:=(i+1) }; s }",
                    List.of("0","1","5","10"),
                    "\n\nNORMAL_TERMINATION\n0\n\nNORMAL_TERMINATION\n0\n\nNORMAL_TERMINATION\n10\n\nNORMAL_TERMINATION\n45\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "complex_recursive_mutual",
                    "int even(int n){ if(n==0) then {1} else { odd((n-1)) } } int odd(int n){ if(n==0) then {0} else { even((n-1)) } } int main(){ even(9) }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n0\n",
                    ExpectedOutcome.PASS
            ));

            // Spare tests

            custom.add(new TestCase(
                    "edge_arg_type_bool_vs_int",
                    "int main(bool b, int x){ if b then {x} else {(x+1)} }",
                    List.of("true 7","false 7"),
                    "\n\nNORMAL_TERMINATION\n7\n\nNORMAL_TERMINATION\n8\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "edge_assignment_chain",
                    "int main(){ int a:=1; int b:=2; a:=b; b:=3; a }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n2\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "edge_boolean_expr_priority",
                    "int main(){ if (true & false | true) then {1} else {0} }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n1\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "edge_block_last_expression_unit_error",
                    "int main(){ { print 1 } }",
                    List.of(""),
                    "",
                    ExpectedOutcome.FATAL
            ));

            custom.add(new TestCase(
                    "edge_call_wrong_argument_type",
                    "int f(int x){x} int main(){ f(true) }",
                    List.of(""),
                    "",
                    ExpectedOutcome.FATAL
            ));

            custom.add(new TestCase(
                    "edge_call_undefined_function",
                    "int main(){ foo(3) }",
                    List.of(""),
                    "",
                    ExpectedOutcome.FATAL
            ));

            custom.add(new TestCase(
                    "edge_division_by_zero",
                    "int main(){ (5/0) }",
                    List.of(""),
                    "",
                    ExpectedOutcome.FATAL
            ));

            custom.add(new TestCase(
                    "edge_if_unit_mismatch",
                    "int main(){ if true then {1} else {print 1} }",
                    List.of(""),
                    "",
                    ExpectedOutcome.FATAL
            ));

            custom.add(new TestCase(
                    "edge_local_variable_shadowing_error",
                    "int main(){ int x:=1; int x:=2; x }",
                    List.of(""),
                    "",
                    ExpectedOutcome.FATAL
            ));

            custom.add(new TestCase(
                    "edge_main_must_return_int",
                    "bool main(){ true }",
                    List.of(""),
                    "",
                    ExpectedOutcome.FATAL
            ));

            custom.add(new TestCase(
                    "edge_missing_main_function",
                    "int f(){5}",
                    List.of(""),
                    "",
                    ExpectedOutcome.FATAL
            ));

            custom.add(new TestCase(
                    "edge_nested_block_scoping",
                    "int main(){ int x:=1; { int y:=2; { int x:=5; print x }; print y; print x; 0 }",
                    List.of(""),
                    "5\n2\n1\n\nNORMAL_TERMINATION\n0\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "edge_repeat_never_runs_wrongly",
                    "int main(){ int x:=10; repeat { x:=(x+1) } until(true); x }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n11\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "edge_skip_acts_as_unit",
                    "int main(){ skip; 9 }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n9\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "edge_unit_function_in_expression_fatal",
                    "unit p(){ skip } int main(){ (p()+1) }",
                    List.of(""),
                    "",
                    ExpectedOutcome.FATAL
            ));

            custom.add(new TestCase(
                    "mixed_boolean_arithmetic_ordering",
                    "int main(){ if((3+2)==(1*5)&true) then {7} else {0} }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n7\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "mixed_block_function_calls",
                    "int f(int x){(x*2)} int main(){ { print f(3); print space; print f(4) }; 0 }",
                    List.of(""),
                    "6\n 8\n\nNORMAL_TERMINATION\n0\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "mixed_print_inside_repeat_loop",
                    "int main(){ int i:=0; repeat{ print i; i:=(i+1) }until(i==4); 9 }",
                    List.of(""),
                    "0\n1\n2\n3\n\nNORMAL_TERMINATION\n9\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "mixed_scoping_and_function_parameters",
                    "int f(int x){ { int y:=5; (x+y) } } int main(){ f(7) }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n12\n",
                    ExpectedOutcome.PASS
            ));

            custom.add(new TestCase(
                    "stress_mutual_recursion_even_odd_small",
                    "int even(int n){ if(n==0) then {1} else { odd((n-1)) } } int odd(int n){ if(n==0) then {0} else { even((n-1)) } } int main(){ odd(6) }",
                    List.of(""),
                    "\n\nNORMAL_TERMINATION\n0\n",
                    ExpectedOutcome.PASS
            ));

            // ==========================================================
            // RUN CUSTOM TESTS (patched)
            // ==========================================================
            for (TestCase tc : custom) {

                total++;

                Throwable thrown = null;
                String actualOutput = "";
                List<String[]> usedInputs = new ArrayList<>();

                try {
                    File tmp = File.createTempFile("simp", ".simp");
                    try (FileWriter fw = new FileWriter(tmp)) {
                        fw.write(tc.program);
                    }

                    SimpleLangParser.ProgContext tree = parseProgram(tmp.getAbsolutePath());
                    StringBuilder sb = new StringBuilder();

                    List<String> argLines = tc.argLines.isEmpty()
                            ? List.of("")
                            : tc.argLines;

                    for (String line : argLines) {
                        String[] arr = (line.trim().isEmpty()
                                ? new String[]{}
                                : line.trim().split("\\s+"));
                        usedInputs.add(arr);
                        runProgram(tree, arr, sb);
                    }

                    actualOutput = sb.toString().trim().replace("\r", "");
                }
                catch (Throwable t) {
                    thrown = t;
                }

                String expectedOutput = tc.expectedOutput.trim();
                ActualOutcome actual = classifyOutcome(thrown, expectedOutput, actualOutput);

                boolean ok = !isUnexpected(tc.expected, actual);

                if (ok) passed++;
                else if (actual == ActualOutcome.FATAL) fatal++;
                else failed++;

                records.add(new TestRecord(
                        tc.name, true,
                        tc.expected, actual,
                        expectedOutput, actualOutput,
                        thrown, tc.program,
                        usedInputs
                ));
            }

        } finally {

            // ==========================================================
            // SUMMARY
            // ==========================================================
            System.out.println("==================================================");
            System.out.println("SUMMARY");
            System.out.println("Total Tests: " + total);
            // A test is correct if actual outcome matches expected outcome
            int correct = 0;
            int incorrect = 0;

            for (TestRecord r : records) {
                boolean isCorrect = !isUnexpected(r.expected, r.actual);
                if (isCorrect) correct++;
                else incorrect++;
            }
            System.out.println("Correct:   " + correct);
            System.out.println("Incorrect: " + incorrect);
            System.out.println("==================================================");

            // ==========================================================
            // DETAILED REPORT
            // ==========================================================
            System.out.println("==================================================");
            System.out.println("DETAILED REPORT FOR FAILED AND FATAL TESTS");
            System.out.println("==================================================");

            for (TestRecord r : records) {
                if (!isUnexpected(r.expected, r.actual))
                    continue;

                System.out.println("--------------------------------------------------");
                System.out.println("TEST: " + r.name + (r.isCustom ? " (custom)" : ""));
                System.out.println("Expected outcome: " + r.expected);
                System.out.println("Actual outcome:   " + r.actual);

                System.out.println("---- Program Code ----");
                System.out.println(r.programCode);

                System.out.println("---- Inputs ----");
                for (String[] arr : r.inputs) {
                    System.out.println(Arrays.toString(arr));
                }

                System.out.println("---- Expected Output ----");
                System.out.println(r.expectedOutput);

                System.out.println("---- Actual Output ------");
                System.out.println(r.actualOutput);

                if (r.exception != null) {
                    System.out.println("---- Exception ----");
                    StringWriter sw = new StringWriter();
                    r.exception.printStackTrace(new PrintWriter(sw));
                    System.out.println(sw.toString());
                }
            }

            System.out.println("==================================================");
        }
    }
}