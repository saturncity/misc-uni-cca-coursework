import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;

public class SimpleLangInterpreter extends AbstractParseTreeVisitor<Integer> implements SimpleLangVisitor<Integer> {

    // Type checker data structures

    /**
     * Helper class to store function type signatures.
     * Contains the return type, parameter types, and parameter names for a function declaration.
     */
    static class FunctionSignature {
        String returnType;
        List<String> paramTypes;
        List<String> paramNames;

        /**
         * Constructs a new FunctionSignature.
         *
         * @param returnType the return type of the function (int, bool, or unit)
         * @param paramTypes list of parameter types in declaration order
         * @param paramNames list of parameter names in declaration order
         */
        FunctionSignature(String returnType, List<String> paramTypes, List<String> paramNames) {
            this.returnType = returnType;
            this.paramTypes = paramTypes;
            this.paramNames = paramNames;
        }
    }

    // map from function name to its type signature
    private final Map<String, FunctionSignature> functionSignatures = new HashMap<>();

    // Interpreter data structures

    private final Map<String, SimpleLangParser.DecContext> global_funcs = new HashMap<>();

    private final Stack<Map<String, Integer>> frames = new Stack<>();

    // Top level program functions

    /**
     * Entry point for executing a SimpleLang program.
     * Performs two-phase execution: type checking followed by interpretation.
     * Creates the initial frame for main function with command-line arguments.
     *
     * @param ctx the program parse tree context
     * @param args command-line arguments to pass to main function
     * @return the integer result of the main function
     * @throws RuntimeException if type checking fails or runtime error occurs
     */
    public Integer visitProgram(SimpleLangParser.ProgContext ctx, String[] args) {
        // phase 1: type checking (before any interpretation)
        typeCheckProgram(ctx, args);

        // phase 2: interpretation (existing code)
        for (int i = 0; i < ctx.dec().size(); ++i) {
            SimpleLangParser.DecContext dec = ctx.dec(i);
            String fname = dec.Idfr().getText();
            global_funcs.put(fname, dec);
        }

        SimpleLangParser.DecContext main = global_funcs.get("main");

        Map<String, Integer> newFrame = new HashMap<>();
        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("true")) {
                newFrame.put(main.vardec().Idfr(i).getText(), 1);
            } else if (args[i].equals("false")) {
                newFrame.put(main.vardec().Idfr(i).getText(), 0);
            } else {
                newFrame.put(main.vardec().Idfr(i).getText(), Integer.parseInt(args[i]));
            }
        }

        frames.push(newFrame);
        Integer result = visit(main);
        frames.pop();
        return result;

    }

    // not used
    @Override
    public Integer visitProg(SimpleLangParser.ProgContext ctx) {
        throw new RuntimeException("Should not be here!");
    }

    /**
     * Visits a function declaration node by executing its body.
     *
     * @param ctx the function declaration context
     * @return the return value of the function (value of last expression in body)
     */
    @Override
    public Integer visitDec(SimpleLangParser.DecContext ctx) {
        return visit(ctx.body());
    }

    // not used
    @Override
    public Integer visitVardec(SimpleLangParser.VardecContext ctx) {
        return null;
    }

    // not used
    @Override
    public Integer visitType(SimpleLangParser.TypeContext ctx) {
        throw new RuntimeException("Should not be here!");
    }

    /**
     * Visits a function body, initializing local variables and evaluating the expression sequence.
     * The function frame has already been created by visitInvokeExpr.
     *
     * @param ctx the body context
     * @return the value of the last expression in the body
     */
    @Override
    public Integer visitBody(SimpleLangParser.BodyContext ctx) {
        // the function frame was created in visitInvokeExpr, so we use it directly

        // initialize all local variables declared at the top of the function body
        for (int i = 0; i < ctx.type().size(); i++) {
            String name = ctx.Idfr(i).getText();
            SimpleLangParser.ExpContext rhs = ctx.exp(i);
            int value = visit(rhs);
            frames.peek().put(name, value);  // add to function frame only
        }

        // evaluate the ENE expression sequence inside the function body
        Integer result = null;
        for (var e : ctx.ene().exp()) {
            result = visit(e);
        }

        return result;
    }

    /**
     * Visits a block expression, evaluating all expressions in sequence.
     * Blocks do not create new scopes; they use the current function scope.
     *
     * @param ctx the block context
     * @return the value of the last expression in the block
     */
    @Override
    public Integer visitBlock(SimpleLangParser.BlockContext ctx) {
        // blocks do not create new scopes - they are function-scoped
        // just evaluate the expressions in sequence using the current frame
        Integer result = null;
        for (var e : ctx.ene().exp()) {
            result = visit(e);
        }

        return result;
    }

    // Expressions

    /**
     * Visits an expression node by delegating to the logic expression visitor.
     *
     * @param ctx the expression context
     * @return the computed integer value of the expression
     */
    @Override
    public Integer visitExp(SimpleLangParser.ExpContext ctx) {
        return visit(ctx.logicExp());
    }

    /**
     * Visits a logic expression, evaluating boolean operators (&, |, ^) left-to-right.
     * Implements short-circuit evaluation for boolean operators.
     *
     * @param ctx the logic expression context
     * @return 1 for true, 0 for false, or the value if no boolean operators present
     */
    @Override
    public Integer visitLogicExp(SimpleLangParser.LogicExpContext ctx) {
        // evaluate leftmost operand first
        int value = visit(ctx.compareExp(0));

        // apply each boolean operator left-to-right
        for (int i = 1; i < ctx.compareExp().size(); i++) {
            int right = visit(ctx.compareExp(i));
            String op = ctx.getChild(2 * i - 1).getText();

            switch (op) {
                case "&":
                    value = (value != 0 && right != 0) ? 1 : 0;
                    break;
                case "|":
                    value = (value != 0 || right != 0) ? 1 : 0;
                    break;
                case "^":
                    value = (value != 0 ^ right != 0) ? 1 : 0;
                    break;
            }
        }

        return value;
    }

    /**
     * Visits a comparison expression, evaluating comparison operators (==, <, >, <=, >=).
     *
     * @param ctx the comparison expression context
     * @return 1 if comparison is true, 0 if false, or the value if no comparison operator
     */
    @Override
    public Integer visitCompareExp(SimpleLangParser.CompareExpContext ctx) {
        // evaluate left operand
        int left = visit(ctx.additiveExp(0));

        // no comparison operator present, just return the value
        if (ctx.additiveExp().size() == 1)
            return left;

        // evaluate right operand and apply comparison
        int right = visit(ctx.additiveExp(1));
        String op = ctx.getChild(1).getText();

        switch (op) {
            case "==":
                return (left == right) ? 1 : 0;
            case "<":
                return (left < right) ? 1 : 0;
            case ">":
                return (left > right) ? 1 : 0;
            case "<=":
                return (left <= right) ? 1 : 0;
            case ">=":
                return (left >= right) ? 1 : 0;
        }

        throw new RuntimeException("Unknown compare op");
    }

    /**
     * Visits an additive expression, evaluating addition and subtraction left-to-right.
     *
     * @param ctx the additive expression context
     * @return the computed integer result of the additive operations
     */
    @Override
    public Integer visitAdditiveExp(SimpleLangParser.AdditiveExpContext ctx) {

        int value = visit(ctx.multiplicativeExp(0));

        for (int i = 1; i < ctx.multiplicativeExp().size(); i++) {
            Integer right = visit(ctx.multiplicativeExp(i));
            String op = ctx.getChild(2 * i - 1).getText();

            if (op.equals("+")) value += right;
            else value -= right;
        }

        return value;
    }

    /**
     * Visits a multiplicative expression, evaluating multiplication and division left-to-right.
     *
     * @param ctx the multiplicative expression context
     * @return the computed integer result of the multiplicative operations
     */
    @Override
    public Integer visitMultiplicativeExp(SimpleLangParser.MultiplicativeExpContext ctx) {

        int value = visit(ctx.unaryExp(0));

        for (int i = 1; i < ctx.unaryExp().size(); i++) {
            Integer right = visit(ctx.unaryExp(i));
            String op = ctx.getChild(2 * i - 1).getText();

            if (op.equals("*")) value *= right;
            else value /= right;
        }

        return value;
    }

    /**
     * Visits a unary expression, applying unary negation (-) or boolean NOT (~).
     * Evaluates right-associatively for chained unary operators.
     *
     * @param ctx the unary expression context
     * @return the computed value after applying unary operator(s)
     */
    @Override
    public Integer visitUnaryExp(SimpleLangParser.UnaryExpContext ctx) {
        // case 1: primaryExp
        if (ctx.primaryExp() != null) {
            return visit(ctx.primaryExp());
        }

        // case 2: unop unaryExp
        SimpleLangParser.UnaryExpContext child = ctx.unaryExp();
        if (child == null) {
            throw new RuntimeException("Invalid unaryExp: missing child");
        }
        if (child == ctx) {
            throw new RuntimeException("Malformed unaryExp: unaryExp recurses into itself");
        }

        // evaluate the operand first (right-associative)
        int val = visit(child);
        String op = ctx.unop().getText();

        switch (op) {
            case "-":
                return -val;
            case "~":
                return (val == 0) ? 1 : 0;
            default:
                throw new RuntimeException("Unknown unary operator: " + op);
        }
    }

    // not used
    @Override
    public Integer visitNegUnop(SimpleLangParser.NegUnopContext ctx) {
        return null;
    }

    // not used
    @Override
    public Integer visitNotUnop(SimpleLangParser.NotUnopContext ctx) {
        return null;
    }

    // Primary expressions

    /**
     * Visits an assignment expression, updating the variable in the current frame.
     *
     * @param ctx the assignment expression context
     * @return 0 (representing unit type)
     */
    @Override
    public Integer visitAssignExpr(SimpleLangParser.AssignExprContext ctx) {
        Integer rhs = visit(ctx.exp());
        frames.peek().put(ctx.Idfr().getText(), rhs);
        return 0;   // unit is represented as 0 to avoid null arithmetic crashes
    }

    /**
     * Visits a function invocation, creating a new frame and binding arguments to parameters.
     * Arguments are passed by value.
     *
     * @param ctx the function invocation context
     * @return the return value of the invoked function
     */
    @Override
    public Integer visitInvokeExpr(SimpleLangParser.InvokeExprContext ctx) {

        SimpleLangParser.DecContext fun = global_funcs.get(ctx.Idfr().getText());
        if (fun == null) {
            throw new RuntimeException("Undefined function: " + ctx.Idfr().getText());
        }

        SimpleLangParser.VardecContext params = fun.vardec();
        SimpleLangParser.ArgsContext argsCtx = ctx.args();

        int paramCount = (params == null ? 0 : params.Idfr().size());
        int argCount = (argsCtx == null ? 0 : argsCtx.exp().size());
        if (paramCount != argCount) {
            throw new RuntimeException("Argument count mismatch calling: " + ctx.Idfr().getText());
        }

        Map<String, Integer> frame = new HashMap<>();

        for (int i = 0; i < paramCount; i++) {
            String name = params.Idfr(i).getText();
            int value = visit(argsCtx.exp(i));
            frame.put(name, value);
        }

        frames.push(frame);
        Integer result = visit(fun);
        frames.pop();

        return result;
    }

    /**
     * Visits a parenthesized expression, returning the value of the expression inside.
     *
     * @param ctx the parenthesized expression context
     * @return the computed value of the inner expression
     */
    @Override
    public Integer visitParenExpr(SimpleLangParser.ParenExprContext ctx) {
        return visit(ctx.exp());
    }

    /**
     * Visits a block expression by delegating to the block visitor.
     *
     * @param ctx the block expression context
     * @return the value of the last expression in the block
     */
    @Override
    public Integer visitBlockExpr(SimpleLangParser.BlockExprContext ctx) {
        return visit(ctx.block());
    }

    /**
     * Visits an if expression, evaluating the then or else branch based on the condition.
     * In SimpleLang, 0 is false and any non-zero value is true.
     *
     * @param ctx the if expression context
     * @return the value of the executed branch
     */
    @Override
    public Integer visitIfExpr(SimpleLangParser.IfExprContext ctx) {

        SimpleLangParser.ExpContext cond = ctx.exp();
        Integer condValue = visit(cond);
        if (condValue != 0) {

            SimpleLangParser.BlockContext thenBlock = ctx.block(0);
            return visit(thenBlock);

        } else {

            SimpleLangParser.BlockContext elseBlock = ctx.block(1);
            return visit(elseBlock);

        }
    }

    /**
     * Visits a while loop, repeatedly evaluating the body while the condition is true.
     *
     * @param ctx the while expression context
     * @return 0 (representing unit type)
     */
    @Override
    public Integer visitWhileExpr(SimpleLangParser.WhileExprContext ctx) {
        while (visit(ctx.exp()) != 0) {
            visit(ctx.block());
        }
        return 0;
    }

    /**
     * Visits a repeat-until loop, executing the block at least once, then repeating while condition is false.
     *
     * @param ctx the repeat expression context
     * @return 0 (representing unit type)
     */
    @Override
    public Integer visitRepeatExpr(SimpleLangParser.RepeatExprContext ctx) {

        do {
            visit(ctx.block());
        } while (visit(ctx.exp()) == 0);
        return 0;
    }

    /**
     * Visits a print expression, printing a space, newline, or integer value to stdout.
     *
     * @param ctx the print expression context
     * @return 0 (representing unit type)
     */
    @Override
    public Integer visitPrintExpr(SimpleLangParser.PrintExprContext ctx) {
        SimpleLangParser.PrimaryExpContext prim = getPrimary(ctx.exp());

        // case 1: print space
        if (prim instanceof SimpleLangParser.SpaceExprContext) {
            System.out.print(" ");
            return 0;
        }

        // case 2: print newline
        if (prim instanceof SimpleLangParser.NewLineExprContext) {
            System.out.print("\n");
            return 0;
        }

        // case 3: print integer expression
        int val = visit(ctx.exp());
        System.out.print(val);
        return 0;
    }

    /**
     * Visits a space expression, representing a space character.
     *
     * @param ctx the space expression context
     * @return 0 (representing unit type)
     */
    @Override
    public Integer visitSpaceExpr(SimpleLangParser.SpaceExprContext ctx) {
        return 0;
    }

    /**
     * Visits a newline expression, representing a newline character.
     *
     * @param ctx the newline expression context
     * @return 0 (representing unit type)
     */
    @Override
    public Integer visitNewLineExpr(SimpleLangParser.NewLineExprContext ctx) {
        return 0;
    }

    /**
     * Visits a skip expression, which performs no operation.
     *
     * @param ctx the skip expression context
     * @return 0 (representing unit type)
     */
    @Override
    public Integer visitSkipExpr(SimpleLangParser.SkipExprContext ctx) {
        return 0;
    }

    /**
     * Visits an identifier expression, looking up the variable's value in the current frame.
     *
     * @param ctx the identifier expression context
     * @return the current value of the variable
     */
    @Override
    public Integer visitIdExpr(SimpleLangParser.IdExprContext ctx) {
        return frames.peek().get(ctx.Idfr().getText());
    }

    /**
     * Visits an integer literal expression.
     *
     * @param ctx the integer expression context
     * @return the parsed integer value
     */
    @Override
    public Integer visitIntExpr(SimpleLangParser.IntExprContext ctx) {
        return Integer.parseInt(ctx.IntLit().getText());
    }

    /**
     * Visits a boolean literal expression.
     *
     * @param ctx the boolean expression context
     * @return 1 for true, 0 for false
     */
    @Override
    public Integer visitBoolExpr(SimpleLangParser.BoolExprContext ctx) {
        return ctx.BoolLit().getText().equals("true") ? 1 : 0;
    }

    // Args and Ene

    /**
     * Visits function arguments, evaluating all argument expressions in order.
     *
     * @param ctx the arguments context
     * @return the value of the last argument (or null if no arguments)
     */
    @Override
    public Integer visitArgs(SimpleLangParser.ArgsContext ctx) {
        Integer result = null;
        for (var e : ctx.exp()) result = visit(e);
        return result;
    }

    /**
     * Visits an expression sequence (ENE), evaluating all expressions in order.
     *
     * @param ctx the expression sequence context
     * @return the value of the last expression
     */
    @Override
    public Integer visitEne(SimpleLangParser.EneContext ctx) {

        Integer result = null;
        for (var e : ctx.exp()) {
            result = visit(e);
        }
        return result;
    }

    // Binary operators

    // not used
    @Override
    public Integer visitEqBinop(SimpleLangParser.EqBinopContext ctx) {
        return null;
    }

    // not used
    @Override
    public Integer visitLessBinop(SimpleLangParser.LessBinopContext ctx) {
        return null;
    }

    // not used
    @Override
    public Integer visitGreaterBinop(SimpleLangParser.GreaterBinopContext ctx) {
        return null;
    }

    // not used
    @Override
    public Integer visitLessEqBinop(SimpleLangParser.LessEqBinopContext ctx) {
        return null;
    }

    // not used
    @Override
    public Integer visitGreaterEqBinop(SimpleLangParser.GreaterEqBinopContext ctx) {
        return null;
    }

    // not used
    @Override
    public Integer visitPlusBinop(SimpleLangParser.PlusBinopContext ctx) {
        return null;
    }

    // not used
    @Override
    public Integer visitMinusBinop(SimpleLangParser.MinusBinopContext ctx) {
        return null;
    }

    // not used
    @Override
    public Integer visitTimesBinop(SimpleLangParser.TimesBinopContext ctx) {
        return null;
    }

    // not used
    @Override
    public Integer visitDivBinop(SimpleLangParser.DivBinopContext ctx) {
        return null;
    }

    // not used
    @Override
    public Integer visitAndBinop(SimpleLangParser.AndBinopContext ctx) {
        return null;
    }

    // not used
    @Override
    public Integer visitOrBinop(SimpleLangParser.OrBinopContext ctx) {
        return null;
    }

    // not used
    @Override
    public Integer visitXorBinop(SimpleLangParser.XorBinopContext ctx) {
        return null;
    }

    /**
     * Helper method to extract the primary expression from a nested expression tree.
     * Traverses through logic, compare, additive, multiplicative, and unary layers.
     *
     * @param ctx the expression context
     * @return the innermost primary expression
     */
    private SimpleLangParser.PrimaryExpContext getPrimary(SimpleLangParser.ExpContext ctx) {
        SimpleLangParser.LogicExpContext l = ctx.logicExp();
        SimpleLangParser.CompareExpContext c = l.compareExp(0);
        SimpleLangParser.AdditiveExpContext a = c.additiveExp(0);
        SimpleLangParser.MultiplicativeExpContext m = a.multiplicativeExp(0);
        SimpleLangParser.UnaryExpContext u = m.unaryExp(0);
        return u.primaryExp();
    }

    // Type checker implementation

    /**
     * Main entry point for type checking the entire program.
     * Performs three phases: building function signatures, checking semantic constraints,
     * and type checking all function bodies.
     *
     * @param ctx the program parse tree context
     * @param args command-line arguments passed to the program
     * @throws RuntimeException if any type error or semantic constraint violation is found
     */
    private void typeCheckProgram(SimpleLangParser.ProgContext ctx, String[] args) {
        // build function signatures and check for duplicates
        buildFunctionSignatures(ctx);

        // check semantic constraints (main exists, correct signature, etc.)
        checkSemanticConstraints(ctx, args);

        // type check each function body
        for (SimpleLangParser.DecContext dec : ctx.dec()) {
            typeCheckFunction(dec);
        }
    }

    /**
     * Collects all function signatures from the program and validates them.
     * Checks for duplicate function names and ensures parameter types are int or bool.
     * Also validates that parameter names within each function are unique.
     *
     * @param ctx the program parse tree context
     * @throws RuntimeException if duplicate function names or invalid types are found
     */
    private void buildFunctionSignatures(SimpleLangParser.ProgContext ctx) {
        for (SimpleLangParser.DecContext dec : ctx.dec()) {
            String funcName = dec.Idfr().getText();

            // check for duplicate function name
            if (functionSignatures.containsKey(funcName)) {
                throw new RuntimeException("TYPE ERROR: Duplicate function name: " + funcName);
            }

            // extract return type
            String returnType = dec.type().getText();

            // extract parameter types and names
            List<String> paramTypes = new ArrayList<>();
            List<String> paramNames = new ArrayList<>();
            SimpleLangParser.VardecContext vardec = dec.vardec();
            if (vardec != null && vardec.type() != null) {
                for (int i = 0; i < vardec.type().size(); i++) {
                    String paramType = vardec.type(i).getText();
                    String paramName = vardec.Idfr(i).getText();

                    // check parameter type is int or bool
                    if (!paramType.equals("int") && !paramType.equals("bool")) {
                        throw new RuntimeException("TYPE ERROR: Parameter '" + paramName +
                                "' in function '" + funcName + "' must be int or bool, not " + paramType);
                    }

                    // check for duplicate parameter names
                    if (paramNames.contains(paramName)) {
                        throw new RuntimeException("TYPE ERROR: Duplicate parameter name '" +
                                paramName + "' in function '" + funcName + "'");
                    }

                    paramTypes.add(paramType);
                    paramNames.add(paramName);
                }
            }

            functionSignatures.put(funcName, new FunctionSignature(returnType, paramTypes, paramNames));
        }
    }

    /**
     * Checks semantic constraints on the program.
     * Validates that a main function exists, returns int, and has the correct number of parameters.
     *
     * @param ctx the program parse tree context (unused but kept for consistency)
     * @param args command-line arguments to validate against main's parameter count
     * @throws RuntimeException if semantic constraints are violated
     */
    private void checkSemanticConstraints(SimpleLangParser.ProgContext ctx, String[] args) {
        // check that main function exists
        if (!functionSignatures.containsKey("main")) {
            throw new RuntimeException("TYPE ERROR: No main function found");
        }

        FunctionSignature mainSig = functionSignatures.get("main");

        // check that main returns int
        if (!mainSig.returnType.equals("int")) {
            throw new RuntimeException("TYPE ERROR: main must return int, not " + mainSig.returnType);
        }

        // check argument count matches main's parameters
        if (args.length != mainSig.paramTypes.size()) {
            throw new RuntimeException("TYPE ERROR: main expects " + mainSig.paramTypes.size() +
                    " arguments but got " + args.length);
        }
    }

    /**
     * Type checks a single function declaration.
     * Builds a type environment with parameters, type checks the body, and validates
     * that the body's type matches the declared return type.
     *
     * @param dec the function declaration context
     * @throws RuntimeException if type errors are found
     */
    private void typeCheckFunction(SimpleLangParser.DecContext dec) {
        String funcName = dec.Idfr().getText();
        FunctionSignature sig = functionSignatures.get(funcName);

        // build type environment for this function
        Map<String, String> typeEnv = new HashMap<>();

        // add parameters to environment
        for (int i = 0; i < sig.paramNames.size(); i++) {
            String paramName = sig.paramNames.get(i);
            // check parameter name doesn't conflict with function names
            if (functionSignatures.containsKey(paramName)) {
                throw new RuntimeException("TYPE ERROR: Parameter '" + paramName +
                        "' in function '" + funcName + "' conflicts with function name");
            }
            typeEnv.put(paramName, sig.paramTypes.get(i));
        }

        // type check the function body
        String bodyType = typeCheckBody(dec.body(), typeEnv, funcName);

        // check that body type matches declared return type
        if (!bodyType.equals(sig.returnType)) {
            throw new RuntimeException("TYPE ERROR: Function '" + funcName +
                    "' declares return type " + sig.returnType + " but body has type " + bodyType);
        }
    }

    /**
     * Type checks a function body, processing local variable declarations and the expression sequence.
     * Validates that local variables have valid types, unique names, and correct initializers.
     *
     * @param ctx the body context
     * @param typeEnv the type environment containing parameters and local variables
     * @param funcName the name of the enclosing function (for error messages)
     * @return the type of the function body (type of the last expression)
     * @throws RuntimeException if type errors are found
     */
    private String typeCheckBody(SimpleLangParser.BodyContext ctx, Map<String, String> typeEnv, String funcName) {
        // add local variables to type environment
        for (int i = 0; i < ctx.type().size(); i++) {
            String varName = ctx.Idfr(i).getText();
            String varType = ctx.type(i).getText();

            // check local variable type is int or bool
            if (!varType.equals("int") && !varType.equals("bool")) {
                throw new RuntimeException("TYPE ERROR: Local variable '" + varName +
                        "' in function '" + funcName + "' must be int or bool, not " + varType);
            }

            // check for duplicate local variable or parameter name
            if (typeEnv.containsKey(varName)) {
                throw new RuntimeException("TYPE ERROR: Duplicate variable name '" + varName +
                        "' in function '" + funcName + "'");
            }

            // check local variable name doesn't conflict with function names
            if (functionSignatures.containsKey(varName)) {
                throw new RuntimeException("TYPE ERROR: Local variable '" + varName +
                        "' in function '" + funcName + "' conflicts with function name");
            }

            // type check the initializer expression
            String initType = inferExpressionType(ctx.exp(i), typeEnv);
            if (!initType.equals(varType)) {
                throw new RuntimeException("TYPE ERROR: Variable '" + varName + "' declared as " +
                        varType + " but initialized with " + initType);
            }

            typeEnv.put(varName, varType);
        }

        // type check the ENE block and return its type (type of last expression)
        return typeCheckEne(ctx.ene(), typeEnv);
    }

    /**
     * Type checks an expression sequence (ENE), evaluating all expressions and returning
     * the type of the last expression.
     *
     * @param ctx the expression sequence context
     * @param typeEnv the type environment
     * @return the type of the last expression in the sequence
     */
    private String typeCheckEne(SimpleLangParser.EneContext ctx, Map<String, String> typeEnv) {
        String lastType = "unit";
        for (SimpleLangParser.ExpContext exp : ctx.exp()) {
            lastType = inferExpressionType(exp, typeEnv);
        }
        return lastType;
    }

    /**
     * Infers the type of an expression by delegating to the logic expression type inference.
     *
     * @param ctx the expression context
     * @param typeEnv the type environment
     * @return the inferred type (int, bool, or unit)
     */
    private String inferExpressionType(SimpleLangParser.ExpContext ctx, Map<String, String> typeEnv) {
        return inferLogicExpType(ctx.logicExp(), typeEnv);
    }

    /**
     * Infers the type of a logic expression containing boolean operators (&, |, ^).
     * Validates that all operands are bool type.
     *
     * @param ctx the logic expression context
     * @param typeEnv the type environment
     * @return bool if operators are present, otherwise the type of the single operand
     * @throws RuntimeException if operands are not bool
     */
    private String inferLogicExpType(SimpleLangParser.LogicExpContext ctx, Map<String, String> typeEnv) {
        String type = inferCompareExpType(ctx.compareExp(0), typeEnv);

        for (int i = 1; i < ctx.compareExp().size(); i++) {
            String rightType = inferCompareExpType(ctx.compareExp(i), typeEnv);

            // boolean operators require both operands to be bool
            if (!type.equals("bool") || !rightType.equals("bool")) {
                throw new RuntimeException("TYPE ERROR: Boolean operators (&, |, ^) require bool operands");
            }
        }

        return ctx.compareExp().size() > 1 ? "bool" : type;
    }

    /**
     * Infers the type of a comparison expression.
     * Validates that comparison operators receive int operands.
     *
     * @param ctx the comparison expression context
     * @param typeEnv the type environment
     * @return bool if a comparison operator is present, otherwise the type of the single operand
     * @throws RuntimeException if operands are not int
     */
    private String inferCompareExpType(SimpleLangParser.CompareExpContext ctx, Map<String, String> typeEnv) {
        String leftType = inferAdditiveExpType(ctx.additiveExp(0), typeEnv);

        if (ctx.additiveExp().size() == 1) {
            return leftType;
        }

        String rightType = inferAdditiveExpType(ctx.additiveExp(1), typeEnv);

        // comparison operators require int operands
        if (!leftType.equals("int") || !rightType.equals("int")) {
            throw new RuntimeException("TYPE ERROR: Comparison operators require int operands");
        }

        return "bool";
    }

    /**
     * Infers the type of an additive expression (+ or -).
     * Validates that all operands are int type.
     *
     * @param ctx the additive expression context
     * @param typeEnv the type environment
     * @return int
     * @throws RuntimeException if any operand is not int
     */
    private String inferAdditiveExpType(SimpleLangParser.AdditiveExpContext ctx, Map<String, String> typeEnv) {
        String type = inferMultiplicativeExpType(ctx.multiplicativeExp(0), typeEnv);

        for (int i = 1; i < ctx.multiplicativeExp().size(); i++) {
            String rightType = inferMultiplicativeExpType(ctx.multiplicativeExp(i), typeEnv);

            // arithmetic operators require int operands
            if (!type.equals("int") || !rightType.equals("int")) {
                throw new RuntimeException("TYPE ERROR: Arithmetic operators (+, -) require int operands");
            }
        }

        return type;
    }

    /**
     * Infers the type of a multiplicative expression (* or /).
     * Validates that all operands are int type.
     *
     * @param ctx the multiplicative expression context
     * @param typeEnv the type environment
     * @return int
     * @throws RuntimeException if any operand is not int
     */
    private String inferMultiplicativeExpType(SimpleLangParser.MultiplicativeExpContext ctx, Map<String, String> typeEnv) {
        String type = inferUnaryExpType(ctx.unaryExp(0), typeEnv);

        for (int i = 1; i < ctx.unaryExp().size(); i++) {
            String rightType = inferUnaryExpType(ctx.unaryExp(i), typeEnv);

            // arithmetic operators require int operands
            if (!type.equals("int") || !rightType.equals("int")) {
                throw new RuntimeException("TYPE ERROR: Arithmetic operators (*, /) require int operands");
            }
        }

        return type;
    }

    /**
     * Infers the type of a unary expression (- for negation, ~ for boolean NOT).
     * Validates that negation receives int and NOT receives bool.
     *
     * @param ctx the unary expression context
     * @param typeEnv the type environment
     * @return the inferred type of the unary expression
     * @throws RuntimeException if operand type doesn't match operator requirements
     */
    private String inferUnaryExpType(SimpleLangParser.UnaryExpContext ctx, Map<String, String> typeEnv) {
        if (ctx.primaryExp() != null) {
            return inferPrimaryExpType(ctx.primaryExp(), typeEnv);
        }

        // unary operator case
        String operandType = inferUnaryExpType(ctx.unaryExp(), typeEnv);
        String op = ctx.unop().getText();

        if (op.equals("-")) {
            // unary negation requires int
            if (!operandType.equals("int")) {
                throw new RuntimeException("TYPE ERROR: Unary negation (-) requires int operand");
            }
            return "int";
        } else if (op.equals("~")) {
            // boolean NOT requires bool
            if (!operandType.equals("bool")) {
                throw new RuntimeException("TYPE ERROR: Boolean NOT (~) requires bool operand");
            }
            return "bool";
        }

        throw new RuntimeException("TYPE ERROR: Unknown unary operator: " + op);
    }

    /**
     * Infers the type of a primary expression (literals, variables, assignments, function calls,
     * control flow, etc.). This is the main type inference workhorse.
     *
     * @param ctx the primary expression context
     * @param typeEnv the type environment
     * @return the inferred type of the primary expression
     * @throws RuntimeException if type errors are detected
     */
    private String inferPrimaryExpType(SimpleLangParser.PrimaryExpContext ctx, Map<String, String> typeEnv) {
        if (ctx instanceof SimpleLangParser.IdExprContext idCtx) {
            String varName = idCtx.Idfr().getText();
            if (!typeEnv.containsKey(varName)) {
                throw new RuntimeException("TYPE ERROR: Variable '" + varName + "' not declared");
            }
            return typeEnv.get(varName);

        } else if (ctx instanceof SimpleLangParser.IntExprContext) {
            return "int";

        } else if (ctx instanceof SimpleLangParser.BoolExprContext) {
            return "bool";

        } else if (ctx instanceof SimpleLangParser.AssignExprContext assignCtx) {
            String varName = assignCtx.Idfr().getText();
            if (!typeEnv.containsKey(varName)) {
                throw new RuntimeException("TYPE ERROR: Variable '" + varName + "' not declared");
            }
            String varType = typeEnv.get(varName);
            String rhsType = inferExpressionType(assignCtx.exp(), typeEnv);

            if (!varType.equals(rhsType)) {
                throw new RuntimeException("TYPE ERROR: Cannot assign " + rhsType + " to variable '" +
                        varName + "' of type " + varType);
            }
            return "unit";

        } else if (ctx instanceof SimpleLangParser.InvokeExprContext invokeCtx) {
            String funcName = invokeCtx.Idfr().getText();

            if (!functionSignatures.containsKey(funcName)) {
                throw new RuntimeException("TYPE ERROR: Function '" + funcName + "' not declared");
            }

            FunctionSignature sig = functionSignatures.get(funcName);
            SimpleLangParser.ArgsContext argsCtx = invokeCtx.args();

            int argCount = (argsCtx == null || argsCtx.exp() == null) ? 0 : argsCtx.exp().size();
            if (argCount != sig.paramTypes.size()) {
                throw new RuntimeException("TYPE ERROR: Function '" + funcName + "' expects " +
                        sig.paramTypes.size() + " arguments but got " + argCount);
            }

            if (argsCtx != null && argsCtx.exp() != null) {
                for (int i = 0; i < argsCtx.exp().size(); i++) {
                    String argType = inferExpressionType(argsCtx.exp(i), typeEnv);
                    if (!argType.equals(sig.paramTypes.get(i))) {
                        throw new RuntimeException("TYPE ERROR: Argument " + (i + 1) + " to function '" +
                                funcName + "' has type " + argType + " but expected " + sig.paramTypes.get(i));
                    }
                }
            }

            return sig.returnType;

        } else if (ctx instanceof SimpleLangParser.ParenExprContext parenCtx) {
            return inferExpressionType(parenCtx.exp(), typeEnv);

        } else if (ctx instanceof SimpleLangParser.BlockExprContext blockCtx) {
            return typeCheckEne(blockCtx.block().ene(), typeEnv);

        } else if (ctx instanceof SimpleLangParser.IfExprContext ifCtx) {
            String condType = inferExpressionType(ifCtx.exp(), typeEnv);

            if (!condType.equals("bool")) {
                throw new RuntimeException("TYPE ERROR: if condition must be bool, not " + condType);
            }

            String thenType = typeCheckEne(ifCtx.block(0).ene(), typeEnv);
            String elseType = typeCheckEne(ifCtx.block(1).ene(), typeEnv);

            if (!thenType.equals(elseType)) {
                throw new RuntimeException("TYPE ERROR: if branches must have same type, got " +
                        thenType + " and " + elseType);
            }

            return thenType;

        } else if (ctx instanceof SimpleLangParser.WhileExprContext whileCtx) {
            String condType = inferExpressionType(whileCtx.exp(), typeEnv);

            if (!condType.equals("bool")) {
                throw new RuntimeException("TYPE ERROR: while condition must be bool, not " + condType);
            }

            String bodyType = typeCheckEne(whileCtx.block().ene(), typeEnv);
            if (!bodyType.equals("unit")) {
                throw new RuntimeException("TYPE ERROR: while body must have type unit, not " + bodyType);
            }

            return "unit";

        } else if (ctx instanceof SimpleLangParser.RepeatExprContext repeatCtx) {
            String bodyType = typeCheckEne(repeatCtx.block().ene(), typeEnv);

            if (!bodyType.equals("unit")) {
                throw new RuntimeException("TYPE ERROR: repeat body must have type unit, not " + bodyType);
            }

            String condType = inferExpressionType(repeatCtx.exp(), typeEnv);
            if (!condType.equals("bool")) {
                throw new RuntimeException("TYPE ERROR: repeat condition must be bool, not " + condType);
            }

            return "unit";

        } else if (ctx instanceof SimpleLangParser.PrintExprContext printCtx) {
            SimpleLangParser.ExpContext printArg = printCtx.exp();

            // check if it's space or newline (which are allowed)
            SimpleLangParser.PrimaryExpContext prim = getPrimary(printArg);
            if (prim instanceof SimpleLangParser.SpaceExprContext ||
                    prim instanceof SimpleLangParser.NewLineExprContext) {
                return "unit";
            }

            // otherwise must be int
            String argType = inferExpressionType(printArg, typeEnv);
            if (!argType.equals("int")) {
                throw new RuntimeException("TYPE ERROR: print argument must be int, space, or newline, not " + argType);
            }

            return "unit";

        } else if (ctx instanceof SimpleLangParser.SpaceExprContext) {
            return "unit";

        } else if (ctx instanceof SimpleLangParser.NewLineExprContext) {
            return "unit";

        } else if (ctx instanceof SimpleLangParser.SkipExprContext) {
            return "unit";
        }

        throw new RuntimeException("TYPE ERROR: Unknown expression type");
    }

}
