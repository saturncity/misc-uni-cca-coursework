import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;

public class SimpleLangInterpreter extends AbstractParseTreeVisitor<Integer> implements SimpleLangVisitor<Integer> {

    // === TYPE CHECKER DATA STRUCTURES ===

    // Helper class to store function type signatures
    static class FunctionSignature {
        String returnType;
        List<String> paramTypes;
        List<String> paramNames;

        FunctionSignature(String returnType, List<String> paramTypes, List<String> paramNames) {
            this.returnType = returnType;
            this.paramTypes = paramTypes;
            this.paramNames = paramNames;
        }
    }

    // Map from function name to its type signature
    private final Map<String, FunctionSignature> functionSignatures = new HashMap<>();

    // === INTERPRETER DATA STRUCTURES ===

    private final Map<String, SimpleLangParser.DecContext> global_funcs = new HashMap<>();

    private final Stack<Map<String, Integer>> frames = new Stack<>();

    // Top level program functions

    public Integer visitProgram(SimpleLangParser.ProgContext ctx, String[] args) {
        // PHASE 1: TYPE CHECKING (before any interpretation)
        typeCheckProgram(ctx, args);

        // PHASE 2: INTERPRETATION (existing code)
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

    @Override
    public Integer visitProg(SimpleLangParser.ProgContext ctx) {
        // visitProg should never run because visitProgram handles top-level execution.

        throw new RuntimeException("Should not be here!");

    }

    @Override
    public Integer visitDec(SimpleLangParser.DecContext ctx) {
        // visitDec should enter a function by visiting its body and returning its last expression.

        return visit(ctx.body());
    }

    @Override
    public Integer visitVardec(SimpleLangParser.VardecContext ctx) {
        // Vardec: (type Idfr (',' type Idfr)*)?
        // Semantics: this returns parameter names, but since the interpreter
        // only uses the parse tree directly, we simply return null.
        // (The actual names are retrieved through ctx.Idfr())
        return null;
    }


    @Override
    public Integer visitType(SimpleLangParser.TypeContext ctx) {
        // visitType should never be directly evaluated at runtime.

        throw new RuntimeException("Should not be here!");
    }

    @Override
    public Integer visitBody(SimpleLangParser.BodyContext ctx) {
        // The function frame was created in visitInvokeExpr, so we use it directly.

        // Initialize all local variables declared at the top of the function body.
        for (int i = 0; i < ctx.type().size(); i++) {
            String name = ctx.Idfr(i).getText();
            SimpleLangParser.ExpContext rhs = ctx.exp(i);
            int value = visit(rhs);
            frames.peek().put(name, value);  // Add to function frame only
        }

        // Evaluate the ENE expression sequence inside the function body
        Integer result = null;
        for (var e : ctx.ene().exp()) {
            result = visit(e);
        }

        return result;
    }

    @Override
    public Integer visitBlock(SimpleLangParser.BlockContext ctx) {
        // Blocks do not create new scopes - they are function-scoped
        // Just evaluate the expressions in sequence using the current frame
        Integer result = null;
        for (var e : ctx.ene().exp()) {
            result = visit(e);
        }

        return result;
    }

    // Expressions

    @Override
    public Integer visitExp(SimpleLangParser.ExpContext ctx) {
        // visitExp just delegates to logicExp.

        return visit(ctx.logicExp());
    }

    @Override
    public Integer visitLogicExp(SimpleLangParser.LogicExpContext ctx) {
        // visitLogicExp should evaluate compareExp(0), then fold (&, |, ^) left-to-right.

        int value = visit(ctx.compareExp(0));

        for (int i = 1; i < ctx.compareExp().size(); i++) {
            int right = visit(ctx.compareExp(i));
            String op = ctx.getChild(2*i - 1).getText();

            switch (op) {
                case "&": value = (value != 0 && right != 0) ? 1 : 0; break;
                case "|": value = (value != 0 || right != 0) ? 1 : 0; break;
                case "^": value = (value != 0 ^ right != 0) ? 1 : 0; break;
            }
        }

        return value;
    }

    @Override
    public Integer visitCompareExp(SimpleLangParser.CompareExpContext ctx) {
        // visitCompareExp should evaluate additiveExp expressions and apply ==,<,>,<=,>=.

        int left = visit(ctx.additiveExp(0));

        if (ctx.additiveExp().size() == 1)
            return left;

        int right = visit(ctx.additiveExp(1));
        String op = ctx.getChild(1).getText();

        switch (op) {
            case "==": return (left == right) ? 1 : 0;
            case "<": return (left < right) ? 1 : 0;
            case ">": return (left > right) ? 1 : 0;
            case "<=": return (left <= right) ? 1 : 0;
            case ">=": return (left >= right) ? 1 : 0;
        }

        throw new RuntimeException("Unknown compare op");
    }

    @Override
    public Integer visitAdditiveExp(SimpleLangParser.AdditiveExpContext ctx) {
        // visitAdditiveExp should fold + and - across multiplicativeExp children.

        int value = visit(ctx.multiplicativeExp(0));

        for (int i = 1; i < ctx.multiplicativeExp().size(); i++) {
            Integer right = visit(ctx.multiplicativeExp(i));
            String op = ctx.getChild(2*i - 1).getText();

            if (op.equals("+")) value += right;
            else value -= right;
        }

        return value;
    }

    @Override
    public Integer visitMultiplicativeExp(SimpleLangParser.MultiplicativeExpContext ctx) {
        // visitMultiplicativeExp should fold * and / across unaryExp children.

        int value = visit(ctx.unaryExp(0));

        for (int i = 1; i < ctx.unaryExp().size(); i++) {
            Integer right = visit(ctx.unaryExp(i));
            String op = ctx.getChild(2*i - 1).getText();

            if (op.equals("*")) value *= right;
            else value /= right;
        }

        return value;
    }

    @Override
    public Integer visitUnaryExp(SimpleLangParser.UnaryExpContext ctx) {
        // visitUnaryExp should evaluate primary or apply unary negation/not to recursive unaryExp.

        //
        // Case 1: primaryExp → just evaluate it.
        //
        if (ctx.primaryExp() != null) {
            return visit(ctx.primaryExp());
        }

        //
        // Case 2: unop unaryExp → apply unary operator to child expression.
        //
        SimpleLangParser.UnaryExpContext child = ctx.unaryExp();
        if (child == null) {
            throw new RuntimeException("Invalid unaryExp: missing child");
        }
        if (child == ctx) {
            throw new RuntimeException("Malformed unaryExp: unaryExp recurses into itself");
        }

        // Evaluate the operand first (right‑associative)
        int val = visit(child);

        // Identify the operator text
        String op = ctx.unop().getText();

        switch (op) {
            case "-":    // unary negation
                return -val;
            case "~":    // boolean NOT
                return (val == 0) ? 1 : 0;
            default:
                throw new RuntimeException("Unknown unary operator: " + op);
        }
    }

    @Override
    public Integer visitNegUnop(SimpleLangParser.NegUnopContext ctx) {
        // visitNegUnop is not used because unary is handled in visitUnaryExp.
        return null;
    }

    @Override
    public Integer visitNotUnop(SimpleLangParser.NotUnopContext ctx) {
        // visitNotUnop is not used because unary is handled in visitUnaryExp.
        return null;
    }

    // Primary expressions

    @Override
    public Integer visitAssignExpr(SimpleLangParser.AssignExprContext ctx) {
        // visitAssignExpr should update a variable in the current frame and return unit.

        Integer rhs = visit(ctx.exp());
        frames.peek().put(ctx.Idfr().getText(), rhs);
        return 0;   // unit is represented as 0 to avoid null arithmetic crashes

    }

    @Override
    public Integer visitInvokeExpr(SimpleLangParser.InvokeExprContext ctx) {
        // visitInvokeExpr should create a new frame, bind parameters by value, execute function body.

        SimpleLangParser.DecContext fun = global_funcs.get(ctx.Idfr().getText());
        if (fun == null) {
            throw new RuntimeException("Undefined function: " + ctx.Idfr().getText());
        }

        SimpleLangParser.VardecContext params = fun.vardec();
        SimpleLangParser.ArgsContext argsCtx = ctx.args();

        int paramCount = (params == null ? 0 : params.Idfr().size());
        int argCount   = (argsCtx == null ? 0 : argsCtx.exp().size());
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

    @Override
    public Integer visitParenExpr(SimpleLangParser.ParenExprContext ctx) {
        // visitParenExpr simply returns value inside parentheses.

        return visit(ctx.exp());
    }

    @Override
    public Integer visitBlockExpr(SimpleLangParser.BlockExprContext ctx) {
        // visitBlockExpr delegates to visitBlock.

        return visit(ctx.block());
    }

    @Override
    public Integer visitIfExpr(SimpleLangParser.IfExprContext ctx) {
        // visitIfExpr should branch based on whether the condition is nonzero.
        // In SimpleLang: 0 is false, any non-zero value (including negatives) is true

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

    @Override
    public Integer visitWhileExpr(SimpleLangParser.WhileExprContext ctx) {
        // visitWhileExpr should repeatedly evaluate body while condition is true; final result is unit.

        while (visit(ctx.exp()) != 0) {
            visit(ctx.block());
        }
        return 0;
    }

    @Override
    public Integer visitRepeatExpr(SimpleLangParser.RepeatExprContext ctx) {
        // visitRepeatExpr should execute block at least once, then loop until cond != 0; returns unit.

        do {
            visit(ctx.block());
        } while (visit(ctx.exp()) == 0);
        return 0;
    }

    @Override
    public Integer visitPrintExpr(SimpleLangParser.PrintExprContext ctx) {

        SimpleLangParser.PrimaryExpContext prim = getPrimary(ctx.exp());

        // CASE 1 — print space
        if (prim instanceof SimpleLangParser.SpaceExprContext) {
            System.out.print(" ");     // print the value
//            System.out.print("\n");    // print EXACTLY one newline
            return 0;
        }

        // CASE 2 — print newline
        if (prim instanceof SimpleLangParser.NewLineExprContext) {
            System.out.print("\n");    // print EXACTLY one newline character
            return 0;
        }

        // CASE 3 — print integer expression
        int val = visit(ctx.exp());
        System.out.print(val);
//        System.out.print("\n");        // EXACTLY one newline
        return 0;
    }

    @Override
    public Integer visitSpaceExpr(SimpleLangParser.SpaceExprContext ctx) {
        // visitSpaceExpr yields unit; printing handled by print expression.

        return 0;
    }

    @Override
    public Integer visitNewLineExpr(SimpleLangParser.NewLineExprContext ctx) {
        // visitNewLineExpr yields unit.

        return 0;
    }

    @Override
    public Integer visitSkipExpr(SimpleLangParser.SkipExprContext ctx) {
        // visitSkipExpr does nothing and returns unit.

        return 0;
    }

    @Override
    public Integer visitIdExpr(SimpleLangParser.IdExprContext ctx) {
        // visitIdExpr returns the variable's current value from top frame.

        return frames.peek().get(ctx.Idfr().getText());
    }

    @Override
    public Integer visitIntExpr(SimpleLangParser.IntExprContext ctx) {
        // visitIntExpr returns parsed integer literal.

        return Integer.parseInt(ctx.IntLit().getText());

    }


    @Override
    public Integer visitBoolExpr(SimpleLangParser.BoolExprContext ctx) {
        // visitBoolExpr returns 1 or 0 for true/false.

        return ctx.BoolLit().getText().equals("true") ? 1 : 0;
    }

    // Args and Ene

    @Override
    public Integer visitArgs(SimpleLangParser.ArgsContext ctx) {
        // visitArgs should evaluate all argument expressions in order.

        Integer result = null;
        for (var e : ctx.exp()) result = visit(e);
        return result;
    }

    @Override
    public Integer visitEne(SimpleLangParser.EneContext ctx) {
        // visitEne should evaluate sequence of expressions and return last.

        Integer result = null;
        for (var e : ctx.exp()) {
            result = visit(e);
        }
        return result;
    }

    // Binary operators

    @Override
    public Integer visitEqBinop(SimpleLangParser.EqBinopContext ctx) {
        // visitEqBinop is unused because parser does not call it for operator evaluation.

        return null;
    }

    @Override
    public Integer visitLessBinop(SimpleLangParser.LessBinopContext ctx) {
        // visitLessBinop is unused; operator logic handled in visitCompareExp.

        return null;
    }

    @Override
    public Integer visitGreaterBinop(SimpleLangParser.GreaterBinopContext ctx) {
        // visitGreaterBinop is unused for same reason.

        return null;
    }

    @Override
    public Integer visitLessEqBinop(SimpleLangParser.LessEqBinopContext ctx) {
        // visitLessEqBinop is unused for same reason.

        return null;
    }

    @Override
    public Integer visitGreaterEqBinop(SimpleLangParser.GreaterEqBinopContext ctx) {
        // visitGreaterEqBinop is unused for same reason.

        return null;
    }

    @Override
    public Integer visitPlusBinop(SimpleLangParser.PlusBinopContext ctx) {
        // visitPlusBinop is unused because addition is handled in visitAdditiveExp.

        return null;
    }

    @Override
    public Integer visitMinusBinop(SimpleLangParser.MinusBinopContext ctx) {
        // visitMinusBinop is unused for same reason.

        return null;
    }

    @Override
    public Integer visitTimesBinop(SimpleLangParser.TimesBinopContext ctx) {
        // visitTimesBinop is unused because multiplication is handled in visitMultiplicativeExp.

        return null;
    }

    @Override
    public Integer visitDivBinop(SimpleLangParser.DivBinopContext ctx) {
        // visitDivBinop is unused for same reason.

        return null;
    }

    @Override
    public Integer visitAndBinop(SimpleLangParser.AndBinopContext ctx) {
        // visitAndBinop is unused because AND is handled in visitLogicExp.

        return null;
    }

    @Override
    public Integer visitOrBinop(SimpleLangParser.OrBinopContext ctx) {
        // visitOrBinop is unused because OR is handled in visitLogicExp.

        return null;
    }

    @Override
    public Integer visitXorBinop(SimpleLangParser.XorBinopContext ctx) {
        // visitXorBinop is unused because XOR is handled in visitLogicExp.

        return null;
    }

    private SimpleLangParser.PrimaryExpContext getPrimary(SimpleLangParser.ExpContext ctx) {
        SimpleLangParser.LogicExpContext l = ctx.logicExp();
        SimpleLangParser.CompareExpContext c = l.compareExp(0);
        SimpleLangParser.AdditiveExpContext a = c.additiveExp(0);
        SimpleLangParser.MultiplicativeExpContext m = a.multiplicativeExp(0);
        SimpleLangParser.UnaryExpContext u = m.unaryExp(0);
        return u.primaryExp();
    }

    // ==================== TYPE CHECKER IMPLEMENTATION ====================

    /**
     * Main entry point for type checking the entire program
     */
    private void typeCheckProgram(SimpleLangParser.ProgContext ctx, String[] args) {
        // Step 1: Build function signatures and check for duplicates
        buildFunctionSignatures(ctx);

        // Step 2: Check semantic constraints (main exists, correct signature, etc.)
        checkSemanticConstraints(ctx, args);

        // Step 3: Type check each function body
        for (SimpleLangParser.DecContext dec : ctx.dec()) {
            typeCheckFunction(dec);
        }
    }

    /**
     * Collect all function signatures and check for duplicate function names
     */
    private void buildFunctionSignatures(SimpleLangParser.ProgContext ctx) {
        for (SimpleLangParser.DecContext dec : ctx.dec()) {
            String funcName = dec.Idfr().getText();

            // Check for duplicate function name
            if (functionSignatures.containsKey(funcName)) {
                throw new RuntimeException("TYPE ERROR: Duplicate function name: " + funcName);
            }

            // Extract return type
            String returnType = dec.type().getText();

            // Extract parameter types and names
            List<String> paramTypes = new ArrayList<>();
            List<String> paramNames = new ArrayList<>();
            SimpleLangParser.VardecContext vardec = dec.vardec();
            if (vardec != null && vardec.type() != null) {
                for (int i = 0; i < vardec.type().size(); i++) {
                    String paramType = vardec.type(i).getText();
                    String paramName = vardec.Idfr(i).getText();

                    // Check parameter type is int or bool
                    if (!paramType.equals("int") && !paramType.equals("bool")) {
                        throw new RuntimeException("TYPE ERROR: Parameter '" + paramName +
                            "' in function '" + funcName + "' must be int or bool, not " + paramType);
                    }

                    // Check for duplicate parameter names
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
     * Check semantic constraints: main exists, has correct signature, etc.
     */
    private void checkSemanticConstraints(SimpleLangParser.ProgContext ctx, String[] args) {
        // Check that main function exists
        if (!functionSignatures.containsKey("main")) {
            throw new RuntimeException("TYPE ERROR: No main function found");
        }

        FunctionSignature mainSig = functionSignatures.get("main");

        // Check that main returns int
        if (!mainSig.returnType.equals("int")) {
            throw new RuntimeException("TYPE ERROR: main must return int, not " + mainSig.returnType);
        }

        // Check argument count matches main's parameters
        if (args.length != mainSig.paramTypes.size()) {
            throw new RuntimeException("TYPE ERROR: main expects " + mainSig.paramTypes.size() +
                " arguments but got " + args.length);
        }
    }

    /**
     * Type check a single function
     */
    private void typeCheckFunction(SimpleLangParser.DecContext dec) {
        String funcName = dec.Idfr().getText();
        FunctionSignature sig = functionSignatures.get(funcName);

        // Build type environment for this function
        Map<String, String> typeEnv = new HashMap<>();

        // Add parameters to environment
        for (int i = 0; i < sig.paramNames.size(); i++) {
            String paramName = sig.paramNames.get(i);
            // Check parameter name doesn't conflict with function names
            if (functionSignatures.containsKey(paramName)) {
                throw new RuntimeException("TYPE ERROR: Parameter '" + paramName +
                    "' in function '" + funcName + "' conflicts with function name");
            }
            typeEnv.put(paramName, sig.paramTypes.get(i));
        }

        // Type check the function body
        String bodyType = typeCheckBody(dec.body(), typeEnv, funcName);

        // Check that body type matches declared return type
        if (!bodyType.equals(sig.returnType)) {
            throw new RuntimeException("TYPE ERROR: Function '" + funcName +
                "' declares return type " + sig.returnType + " but body has type " + bodyType);
        }
    }

    /**
     * Type check a function body
     */
    private String typeCheckBody(SimpleLangParser.BodyContext ctx, Map<String, String> typeEnv, String funcName) {
        // Add local variables to type environment
        for (int i = 0; i < ctx.type().size(); i++) {
            String varName = ctx.Idfr(i).getText();
            String varType = ctx.type(i).getText();

            // Check local variable type is int or bool
            if (!varType.equals("int") && !varType.equals("bool")) {
                throw new RuntimeException("TYPE ERROR: Local variable '" + varName +
                    "' in function '" + funcName + "' must be int or bool, not " + varType);
            }

            // Check for duplicate local variable or parameter name
            if (typeEnv.containsKey(varName)) {
                throw new RuntimeException("TYPE ERROR: Duplicate variable name '" + varName +
                    "' in function '" + funcName + "'");
            }

            // Check local variable name doesn't conflict with function names
            if (functionSignatures.containsKey(varName)) {
                throw new RuntimeException("TYPE ERROR: Local variable '" + varName +
                    "' in function '" + funcName + "' conflicts with function name");
            }

            // Type check the initializer expression
            String initType = inferExpressionType(ctx.exp(i), typeEnv);
            if (!initType.equals(varType)) {
                throw new RuntimeException("TYPE ERROR: Variable '" + varName + "' declared as " +
                    varType + " but initialized with " + initType);
            }

            typeEnv.put(varName, varType);
        }

        // Type check the ENE block and return its type (type of last expression)
        return typeCheckEne(ctx.ene(), typeEnv);
    }

    /**
     * Type check an ENE (expression list) and return the type of the last expression
     */
    private String typeCheckEne(SimpleLangParser.EneContext ctx, Map<String, String> typeEnv) {
        String lastType = "unit";
        for (SimpleLangParser.ExpContext exp : ctx.exp()) {
            lastType = inferExpressionType(exp, typeEnv);
        }
        return lastType;
    }

    /**
     * Infer the type of an expression
     */
    private String inferExpressionType(SimpleLangParser.ExpContext ctx, Map<String, String> typeEnv) {
        return inferLogicExpType(ctx.logicExp(), typeEnv);
    }

    private String inferLogicExpType(SimpleLangParser.LogicExpContext ctx, Map<String, String> typeEnv) {
        String type = inferCompareExpType(ctx.compareExp(0), typeEnv);

        for (int i = 1; i < ctx.compareExp().size(); i++) {
            String rightType = inferCompareExpType(ctx.compareExp(i), typeEnv);

            // Boolean operators require both operands to be bool
            if (!type.equals("bool") || !rightType.equals("bool")) {
                throw new RuntimeException("TYPE ERROR: Boolean operators (&, |, ^) require bool operands");
            }
        }

        return ctx.compareExp().size() > 1 ? "bool" : type;
    }

    private String inferCompareExpType(SimpleLangParser.CompareExpContext ctx, Map<String, String> typeEnv) {
        String leftType = inferAdditiveExpType(ctx.additiveExp(0), typeEnv);

        if (ctx.additiveExp().size() == 1) {
            return leftType;
        }

        String rightType = inferAdditiveExpType(ctx.additiveExp(1), typeEnv);

        // Comparison operators require int operands
        if (!leftType.equals("int") || !rightType.equals("int")) {
            throw new RuntimeException("TYPE ERROR: Comparison operators require int operands");
        }

        return "bool";
    }

    private String inferAdditiveExpType(SimpleLangParser.AdditiveExpContext ctx, Map<String, String> typeEnv) {
        String type = inferMultiplicativeExpType(ctx.multiplicativeExp(0), typeEnv);

        for (int i = 1; i < ctx.multiplicativeExp().size(); i++) {
            String rightType = inferMultiplicativeExpType(ctx.multiplicativeExp(i), typeEnv);

            // Arithmetic operators require int operands
            if (!type.equals("int") || !rightType.equals("int")) {
                throw new RuntimeException("TYPE ERROR: Arithmetic operators (+, -) require int operands");
            }
        }

        return type;
    }

    private String inferMultiplicativeExpType(SimpleLangParser.MultiplicativeExpContext ctx, Map<String, String> typeEnv) {
        String type = inferUnaryExpType(ctx.unaryExp(0), typeEnv);

        for (int i = 1; i < ctx.unaryExp().size(); i++) {
            String rightType = inferUnaryExpType(ctx.unaryExp(i), typeEnv);

            // Arithmetic operators require int operands
            if (!type.equals("int") || !rightType.equals("int")) {
                throw new RuntimeException("TYPE ERROR: Arithmetic operators (*, /) require int operands");
            }
        }

        return type;
    }

    private String inferUnaryExpType(SimpleLangParser.UnaryExpContext ctx, Map<String, String> typeEnv) {
        if (ctx.primaryExp() != null) {
            return inferPrimaryExpType(ctx.primaryExp(), typeEnv);
        }

        // Unary operator case
        String operandType = inferUnaryExpType(ctx.unaryExp(), typeEnv);
        String op = ctx.unop().getText();

        if (op.equals("-")) {
            // Unary negation requires int
            if (!operandType.equals("int")) {
                throw new RuntimeException("TYPE ERROR: Unary negation (-) requires int operand");
            }
            return "int";
        } else if (op.equals("~")) {
            // Boolean NOT requires bool
            if (!operandType.equals("bool")) {
                throw new RuntimeException("TYPE ERROR: Boolean NOT (~) requires bool operand");
            }
            return "bool";
        }

        throw new RuntimeException("TYPE ERROR: Unknown unary operator: " + op);
    }

    private String inferPrimaryExpType(SimpleLangParser.PrimaryExpContext ctx, Map<String, String> typeEnv) {
        if (ctx instanceof SimpleLangParser.IdExprContext) {
            SimpleLangParser.IdExprContext idCtx = (SimpleLangParser.IdExprContext) ctx;
            String varName = idCtx.Idfr().getText();
            if (!typeEnv.containsKey(varName)) {
                throw new RuntimeException("TYPE ERROR: Variable '" + varName + "' not declared");
            }
            return typeEnv.get(varName);

        } else if (ctx instanceof SimpleLangParser.IntExprContext) {
            return "int";

        } else if (ctx instanceof SimpleLangParser.BoolExprContext) {
            return "bool";

        } else if (ctx instanceof SimpleLangParser.AssignExprContext) {
            SimpleLangParser.AssignExprContext assignCtx = (SimpleLangParser.AssignExprContext) ctx;
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

        } else if (ctx instanceof SimpleLangParser.InvokeExprContext) {
            SimpleLangParser.InvokeExprContext invokeCtx = (SimpleLangParser.InvokeExprContext) ctx;
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
                        throw new RuntimeException("TYPE ERROR: Argument " + (i+1) + " to function '" +
                            funcName + "' has type " + argType + " but expected " + sig.paramTypes.get(i));
                    }
                }
            }

            return sig.returnType;

        } else if (ctx instanceof SimpleLangParser.ParenExprContext) {
            SimpleLangParser.ParenExprContext parenCtx = (SimpleLangParser.ParenExprContext) ctx;
            return inferExpressionType(parenCtx.exp(), typeEnv);

        } else if (ctx instanceof SimpleLangParser.BlockExprContext) {
            SimpleLangParser.BlockExprContext blockCtx = (SimpleLangParser.BlockExprContext) ctx;
            return typeCheckEne(blockCtx.block().ene(), typeEnv);

        } else if (ctx instanceof SimpleLangParser.IfExprContext) {
            SimpleLangParser.IfExprContext ifCtx = (SimpleLangParser.IfExprContext) ctx;
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

        } else if (ctx instanceof SimpleLangParser.WhileExprContext) {
            SimpleLangParser.WhileExprContext whileCtx = (SimpleLangParser.WhileExprContext) ctx;
            String condType = inferExpressionType(whileCtx.exp(), typeEnv);

            if (!condType.equals("bool")) {
                throw new RuntimeException("TYPE ERROR: while condition must be bool, not " + condType);
            }

            String bodyType = typeCheckEne(whileCtx.block().ene(), typeEnv);
            if (!bodyType.equals("unit")) {
                throw new RuntimeException("TYPE ERROR: while body must have type unit, not " + bodyType);
            }

            return "unit";

        } else if (ctx instanceof SimpleLangParser.RepeatExprContext) {
            SimpleLangParser.RepeatExprContext repeatCtx = (SimpleLangParser.RepeatExprContext) ctx;
            String bodyType = typeCheckEne(repeatCtx.block().ene(), typeEnv);

            if (!bodyType.equals("unit")) {
                throw new RuntimeException("TYPE ERROR: repeat body must have type unit, not " + bodyType);
            }

            String condType = inferExpressionType(repeatCtx.exp(), typeEnv);
            if (!condType.equals("bool")) {
                throw new RuntimeException("TYPE ERROR: repeat condition must be bool, not " + condType);
            }

            return "unit";

        } else if (ctx instanceof SimpleLangParser.PrintExprContext) {
            SimpleLangParser.PrintExprContext printCtx = (SimpleLangParser.PrintExprContext) ctx;
            SimpleLangParser.ExpContext printArg = printCtx.exp();

            // Check if it's space or newline (which are allowed)
            SimpleLangParser.PrimaryExpContext prim = getPrimary(printArg);
            if (prim instanceof SimpleLangParser.SpaceExprContext ||
                prim instanceof SimpleLangParser.NewLineExprContext) {
                return "unit";
            }

            // Otherwise must be int
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
