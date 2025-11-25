grammar SimpleLang;

// Parser rules

prog
    : dec+ EOF
;

dec
    : type Idfr LParen vardec RParen body
;

vardec
    : (type Idfr (Comma type Idfr)*)?
;

body
    : LBrace (type Idfr Assign exp Semicolon)* ene RBrace
;

block
    : LBrace ene RBrace
;

// expressions nested expressions
ene
    : exp (Semicolon exp)*
;

// expressions done from lowest to highest precedence (base, then unary, multiplicative, additive, compare, logic)
exp
    : logicExp
    ;

logicExp
    : compareExp ( (And | Or | Xor) compareExp )*
    ;

compareExp
    : additiveExp ( (Eq | Less | Greater | LessEq | GreaterEq) additiveExp )*
    ;

additiveExp
    : multiplicativeExp ( (Plus | Minus) multiplicativeExp )*
    ;

multiplicativeExp
    : unaryExp ( (Times | Div) unaryExp )*
    ;

unaryExp
    : unop unaryExp
    | primaryExp
    ;

primaryExp
    : Idfr Assign exp                       #AssignExpr
    | Idfr LParen args? RParen              #InvokeExpr
    | LParen exp RParen                     #ParenExpr
    | block                                 #BlockExpr
    | If exp Then block Else block          #IfExpr
    | While exp Do block                    #WhileExpr
    | Repeat block Until exp                #RepeatExpr
    | Print exp                             #PrintExpr
    | Space                                 #SpaceExpr
    | NewLine                               #NewLineExpr
    | Skip                                  #SkipExpr
    | Idfr                                  #IdExpr
    | IntLit                                #IntExpr
    | BoolLit                               #BoolExpr
    ;

// function arguments
args
    : exp (Comma exp)*
;

// binary operators
binop
    : Eq              #EqBinop
    | Less            #LessBinop
    | Greater         #GreaterBinop
    | LessEq          #LessEqBinop
    | GreaterEq       #GreaterEqBinop
    | Plus            #PlusBinop
    | Minus           #MinusBinop
    | Times           #TimesBinop
    | Div             #DivBinop
    | And             #AndBinop
    | Or              #OrBinop
    | Xor             #XorBinop
;

// unary operators
unop
    : Minus           #NegUnop
    | Tilde             #NotUnop
;

type
    : IntType
    | BoolType
    | UnitType
;

// Lexer rules

// single character tokens
LParen : '(' ;
Comma : ',' ;
RParen : ')' ;
LBrace : '{' ;
Semicolon : ';' ;
RBrace : '}' ;

// binary operators
Eq : '==' ;
Less : '<' ;
Greater : '>' ;
LessEq : '<=' ;
GreaterEq : '>=' ;


// arithmetic operators
Plus : '+' ;
Minus : '-' ;
Times : '*' ;
Div : '/' ;

// logical operators
And : '&' ;
Or : '|' ;
Xor : '^' ;
Tilde : '~' ;

// assignment operator
Assign : ':=' ;

// keywords
Print : 'print' ;
Space : 'space' ;
NewLine : 'newline' ;
If : 'if' ;
Then : 'then' ;
Else : 'else' ;
While : 'while' ;
Do : 'do' ;
Repeat : 'repeat' ;
Until : 'until' ;
Skip : 'skip' ;

// types
IntType : 'int' ;
BoolType : 'bool' ;
UnitType : 'unit' ;

// literals
BoolLit : 'true' | 'false' ;
IntLit : '0' | ('-'? [1-9][0-9]*) ;

// identifiers: start with a lowercase letter, followed by letters, digits, or underscores
Idfr : [a-z][A-Za-z0-9_]* ;

// whitespace
WS : [ \n\r\t]+ -> skip ;