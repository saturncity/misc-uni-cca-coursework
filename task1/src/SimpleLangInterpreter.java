import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;

public class SimpleLangInterpreter extends AbstractParseTreeVisitor<Integer> implements SimpleLangVisitor<Integer> {
    private final Map<String, SimpleLangParser.DecContext> global_funcs = new HashMap<>();

    private final Stack<Map<String, Integer>> frames = new Stack<>();

    // Top level program functions

    public Integer visitProgram(SimpleLangParser.ProgContext ctx, String[] args) {
    // visitProgram should load all functions into the global map, construct the initial frame for main, convert arguments, and then execute main.
    // TODO: Load all functions, prepare main arguments, set up initial frame, run main.

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
        // visitBody should create and initialize all local variables before running expressions.

        // Initialize all local variables declared at the top of the body:
        // (type Idfr := exp ;)
        for (int i = 0; i < ctx.type().size(); i++) {
            // each declaration corresponds to: type Idfr ':=' exp ';'
            String name = ctx.Idfr(i).getText();
            SimpleLangParser.ExpContext rhs = ctx.exp(i);

            int value = visit(rhs);
            frames.peek().put(name, value);
        }

        // Now evaluate the ENE expression sequence
        Integer result = null;
        for (var e : ctx.ene().exp()) {
            result = visit(e);
        }

        return result;

    }

    @Override
    public Integer visitBlock(SimpleLangParser.BlockContext ctx) {
        // visitBlock should evaluate the block contents using the current function frame.

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
        // TODO: Loop over compareExp(i) and apply correct boolean operator semantics.

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
        // TODO: Reduce left-to-right, returning bool-int results (1 or 0).

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
        // TODO: Iterate multiplicativeExp list and evaluate + or - accordingly.

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
        // TODO: Iterate unaryExp list applying * or /.

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
        // TODO: Evaluate RHS, store into current frame, return null to represent unit.

        Integer rhs = visit(ctx.exp());
        frames.peek().put(ctx.Idfr().getText(), rhs);
        return 0;   // unit is represented as 0 to avoid null arithmetic crashes

    }

    @Override
    public Integer visitInvokeExpr(SimpleLangParser.InvokeExprContext ctx) {
        // visitInvokeExpr should create a new frame, bind parameters by value, execute function body.
        // TODO: Push new frame, evaluate body via visitDec/visitBody, pop frame, return final value.

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
        // TODO: Evaluate the condition, then run either the then‑block or else‑block accordingly.

        SimpleLangParser.ExpContext cond = ctx.exp();
        Integer condValue = visit(cond);
        if (condValue > 0) {

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
        // visitPrintExpr prints ints, space, or newline; result is unit.

        // Obtain primary expression if possible
        SimpleLangParser.PrimaryExpContext prim =
                ctx.exp()
                        .logicExp().compareExp(0)
                        .additiveExp(0).multiplicativeExp(0)
                        .unaryExp(0).primaryExp();

        // print space
        if (prim instanceof SimpleLangParser.SpaceExprContext) {
            System.out.print(" ");
            return 0;
        }

        // print newline
        if (prim instanceof SimpleLangParser.NewLineExprContext) {
            System.out.println();
            return 0;
        }

        // Otherwise print evaluated value
        int val = visit(ctx.exp());
        System.out.print(val);
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
        // TODO: Visit each argument expression and return the last evaluated value.

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

}
