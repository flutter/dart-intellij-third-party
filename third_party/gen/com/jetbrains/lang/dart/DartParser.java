// This is a generated file. Not intended for manual editing.
package com.jetbrains.lang.dart;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.jetbrains.lang.dart.DartTokenTypes.*;
import static com.jetbrains.lang.dart.DartGeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class DartParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    parseLight(root_, builder_);
    return builder_.getTreeBuilt();
  }

  public void parseLight(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, EXTENDS_SETS_);
    Marker marker_ = enter_section_(builder_, 0, _COLLAPSE_, null);
    result_ = parse_root_(root_, builder_);
    exit_section_(builder_, 0, marker_, root_, result_, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType root_, PsiBuilder builder_) {
    return parse_root_(root_, builder_, 0);
  }

  static boolean parse_root_(IElementType root_, PsiBuilder builder_, int level_) {
    boolean result_;
    if (root_ == BLOCK) {
      result_ = block(builder_, level_ + 1);
    }
    else {
      result_ = dartUnit(builder_, level_ + 1);
    }
    return result_;
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ADDITIVE_EXPRESSION, ARRAY_ACCESS_EXPRESSION, ASSIGN_EXPRESSION, AS_EXPRESSION,
      AWAIT_EXPRESSION, BITWISE_EXPRESSION, CALL_EXPRESSION, CASCADE_REFERENCE_EXPRESSION,
      COMPARE_EXPRESSION, CONST_OBJECT_EXPRESSION, EXPRESSION, FUNCTION_EXPRESSION,
      IF_NULL_EXPRESSION, IS_EXPRESSION, LIBRARY_COMPONENT_REFERENCE_EXPRESSION, LIST_LITERAL_EXPRESSION,
      LITERAL_EXPRESSION, LOGIC_AND_EXPRESSION, LOGIC_OR_EXPRESSION, MULTIPLICATIVE_EXPRESSION,
      NEW_EXPRESSION, PARAMETER_NAME_REFERENCE_EXPRESSION, PARENTHESIZED_EXPRESSION, PREFIX_EXPRESSION,
      REFERENCE_EXPRESSION, SET_OR_MAP_LITERAL_EXPRESSION, SHIFT_EXPRESSION, SHORTHAND_EXPRESSION,
      STRING_LITERAL_EXPRESSION, SUFFIX_EXPRESSION, SUPER_EXPRESSION, SWITCH_EXPRESSION,
      SWITCH_STATEMENT_OR_EXPRESSION, SYMBOL_LITERAL_EXPRESSION, TERNARY_EXPRESSION, THIS_EXPRESSION,
      THROW_EXPRESSION, VALUE_EXPRESSION),
  };

  /* ********************************************************** */
  // additiveOperator multiplicativeExpressionWrapper
  public static boolean additiveExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "additiveExpression")) return false;
    if (!nextTokenIs(builder_, "<additive expression>", MINUS, PLUS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, ADDITIVE_EXPRESSION, "<additive expression>");
    result_ = additiveOperator(builder_, level_ + 1);
    result_ = result_ && multiplicativeExpressionWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // multiplicativeExpressionWrapper additiveExpression*
  static boolean additiveExpressionWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "additiveExpressionWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = multiplicativeExpressionWrapper(builder_, level_ + 1);
    result_ = result_ && additiveExpressionWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // additiveExpression*
  private static boolean additiveExpressionWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "additiveExpressionWrapper_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!additiveExpression(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "additiveExpressionWrapper_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '+' | '-'
  public static boolean additiveOperator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "additiveOperator")) return false;
    if (!nextTokenIs(builder_, "<additive operator>", MINUS, PLUS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ADDITIVE_OPERATOR, "<additive operator>");
    result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // argumentListPart (',' argumentListPart)* ','?
  public static boolean argumentList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argumentList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ARGUMENT_LIST, "<argument list>");
    result_ = argumentListPart(builder_, level_ + 1);
    result_ = result_ && argumentList_1(builder_, level_ + 1);
    result_ = result_ && argumentList_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, DartParser::argument_list_recover);
    return result_;
  }

  // (',' argumentListPart)*
  private static boolean argumentList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argumentList_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!argumentList_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "argumentList_1", pos_)) break;
    }
    return true;
  }

  // ',' argumentListPart
  private static boolean argumentList_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argumentList_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && argumentListPart(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean argumentList_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argumentList_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // namedArgument | expression
  static boolean argumentListPart(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argumentListPart")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = namedArgument(builder_, level_ + 1);
    if (!result_) result_ = expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, DartParser::argument_list_part_recover);
    return result_;
  }

  /* ********************************************************** */
  // !(')' | ',')
  static boolean argument_list_part_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_list_part_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !argument_list_part_recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ')' | ','
  private static boolean argument_list_part_recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_list_part_recover_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, RPAREN);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    return result_;
  }

  /* ********************************************************** */
  // !(')')
  static boolean argument_list_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_list_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !consumeToken(builder_, RPAREN);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '(' argumentList? ')'
  public static boolean arguments(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arguments")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ARGUMENTS, null);
    result_ = consumeToken(builder_, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, arguments_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // argumentList?
  private static boolean arguments_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arguments_1")) return false;
    argumentList(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // !('?' '[' elements? ']' callOrArrayAccessOrQualifiedRefExpression ':') ('?.' | '?')? '[' expression? ']'
  static boolean arrayAccess(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrayAccess")) return false;
    if (!nextTokenIs(builder_, "", LBRACKET, QUEST, QUEST_DOT)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = arrayAccess_0(builder_, level_ + 1);
    result_ = result_ && arrayAccess_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LBRACKET);
    pinned_ = result_; // pin = 3
    result_ = result_ && report_error_(builder_, arrayAccess_3(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RBRACKET) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // !('?' '[' elements? ']' callOrArrayAccessOrQualifiedRefExpression ':')
  private static boolean arrayAccess_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrayAccess_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !arrayAccess_0_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // '?' '[' elements? ']' callOrArrayAccessOrQualifiedRefExpression ':'
  private static boolean arrayAccess_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrayAccess_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, QUEST, LBRACKET);
    result_ = result_ && arrayAccess_0_0_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    result_ = result_ && callOrArrayAccessOrQualifiedRefExpression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // elements?
  private static boolean arrayAccess_0_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrayAccess_0_0_2")) return false;
    elements(builder_, level_ + 1);
    return true;
  }

  // ('?.' | '?')?
  private static boolean arrayAccess_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrayAccess_1")) return false;
    arrayAccess_1_0(builder_, level_ + 1);
    return true;
  }

  // '?.' | '?'
  private static boolean arrayAccess_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrayAccess_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, QUEST_DOT);
    if (!result_) result_ = consumeToken(builder_, QUEST);
    return result_;
  }

  // expression?
  private static boolean arrayAccess_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrayAccess_3")) return false;
    expression(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // arrayAccess
  public static boolean arrayAccessExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrayAccessExpression")) return false;
    if (!nextTokenIs(builder_, "<array access expression>", LBRACKET, QUEST, QUEST_DOT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, ARRAY_ACCESS_EXPRESSION, "<array access expression>");
    result_ = arrayAccess(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // ('async' '*'? | 'sync' '*'?)? '=>' expression
  static boolean arrowBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrowBody")) return false;
    if (!nextTokenIs(builder_, "", ASYNC, EXPRESSION_BODY_DEF, SYNC)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = arrowBody_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EXPRESSION_BODY_DEF);
    pinned_ = result_; // pin = 2
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // ('async' '*'? | 'sync' '*'?)?
  private static boolean arrowBody_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrowBody_0")) return false;
    arrowBody_0_0(builder_, level_ + 1);
    return true;
  }

  // 'async' '*'? | 'sync' '*'?
  private static boolean arrowBody_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrowBody_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = arrowBody_0_0_0(builder_, level_ + 1);
    if (!result_) result_ = arrowBody_0_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'async' '*'?
  private static boolean arrowBody_0_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrowBody_0_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASYNC);
    result_ = result_ && arrowBody_0_0_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // '*'?
  private static boolean arrowBody_0_0_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrowBody_0_0_0_1")) return false;
    consumeToken(builder_, MUL);
    return true;
  }

  // 'sync' '*'?
  private static boolean arrowBody_0_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrowBody_0_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SYNC);
    result_ = result_ && arrowBody_0_0_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // '*'?
  private static boolean arrowBody_0_0_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrowBody_0_0_1_1")) return false;
    consumeToken(builder_, MUL);
    return true;
  }

  /* ********************************************************** */
  // <<arrowBodyWrapper>> ';'
  static boolean arrowBodyWithSemi(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "arrowBodyWithSemi")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = arrowBodyWrapper(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && consumeToken(builder_, SEMICOLON);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // 'as' type
  public static boolean asExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asExpression")) return false;
    if (!nextTokenIs(builder_, AS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, AS_EXPRESSION, null);
    result_ = consumeToken(builder_, AS);
    result_ = result_ && type(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // 'assert' '(' expressionWithRecoverUntilParenOrComma (',' expressionWithRecoverUntilParenOrComma)? ','? ')'
  public static boolean assertStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assertStatement")) return false;
    if (!nextTokenIs(builder_, ASSERT)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ASSERT_STATEMENT, null);
    result_ = consumeTokens(builder_, 1, ASSERT, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expressionWithRecoverUntilParenOrComma(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, assertStatement_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, assertStatement_4(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (',' expressionWithRecoverUntilParenOrComma)?
  private static boolean assertStatement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assertStatement_3")) return false;
    assertStatement_3_0(builder_, level_ + 1);
    return true;
  }

  // ',' expressionWithRecoverUntilParenOrComma
  private static boolean assertStatement_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assertStatement_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && expressionWithRecoverUntilParenOrComma(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean assertStatement_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assertStatement_4")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // assertStatement ';'
  static boolean assertStatementWithSemicolon(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assertStatementWithSemicolon")) return false;
    if (!nextTokenIs(builder_, ASSERT)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = assertStatement(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && consumeToken(builder_, SEMICOLON);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // assignmentOperator ternaryExpressionWrapper
  public static boolean assignExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assignExpression")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, ASSIGN_EXPRESSION, "<assign expression>");
    result_ = assignmentOperator(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && ternaryExpressionWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // ternaryExpressionWrapper assignExpression*
  static boolean assignExpressionWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assignExpressionWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ternaryExpressionWrapper(builder_, level_ + 1);
    result_ = result_ && assignExpressionWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // assignExpression*
  private static boolean assignExpressionWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assignExpressionWrapper_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!assignExpression(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "assignExpressionWrapper_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '=' | '*=' | '/=' | '~/=' | '%=' | '+=' | '-=' | '<<=' | <<gtGtEq>> | <<gtGtGtEq>> | '&=' | '&&=' | '^=' | '|=' | '||=' | '??='
  public static boolean assignmentOperator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assignmentOperator")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ASSIGNMENT_OPERATOR, "<assignment operator>");
    result_ = consumeToken(builder_, EQ);
    if (!result_) result_ = consumeToken(builder_, MUL_EQ);
    if (!result_) result_ = consumeToken(builder_, DIV_EQ);
    if (!result_) result_ = consumeToken(builder_, INT_DIV_EQ);
    if (!result_) result_ = consumeToken(builder_, REM_EQ);
    if (!result_) result_ = consumeToken(builder_, PLUS_EQ);
    if (!result_) result_ = consumeToken(builder_, MINUS_EQ);
    if (!result_) result_ = consumeToken(builder_, LT_LT_EQ);
    if (!result_) result_ = gtGtEq(builder_, level_ + 1);
    if (!result_) result_ = gtGtGtEq(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, AND_EQ);
    if (!result_) result_ = consumeToken(builder_, AND_AND_EQ);
    if (!result_) result_ = consumeToken(builder_, XOR_EQ);
    if (!result_) result_ = consumeToken(builder_, OR_EQ);
    if (!result_) result_ = consumeToken(builder_, OR_OR_EQ);
    if (!result_) result_ = consumeToken(builder_, QUEST_QUEST_EQ);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // <<isInsideSyncOrAsyncFunction>> 'await' prefixExpression
  public static boolean awaitExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "awaitExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, AWAIT_EXPRESSION, "<await expression>");
    result_ = isInsideSyncOrAsyncFunction(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, AWAIT);
    result_ = result_ && prefixExpression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // multiplicativeOperator |
  //                            additiveOperator |
  //                            shiftOperator|
  //                            relationalOperator|
  //                            '==' |
  //                            bitwiseOperator
  static boolean binaryOperator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "binaryOperator")) return false;
    boolean result_;
    result_ = multiplicativeOperator(builder_, level_ + 1);
    if (!result_) result_ = additiveOperator(builder_, level_ + 1);
    if (!result_) result_ = shiftOperator(builder_, level_ + 1);
    if (!result_) result_ = relationalOperator(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, EQ_EQ);
    if (!result_) result_ = bitwiseOperator(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // bitwiseOperator shiftExpressionWrapper
  public static boolean bitwiseExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bitwiseExpression")) return false;
    if (!nextTokenIs(builder_, "<bitwise expression>", AND, OR, XOR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, BITWISE_EXPRESSION, "<bitwise expression>");
    result_ = bitwiseOperator(builder_, level_ + 1);
    result_ = result_ && shiftExpressionWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // shiftExpressionWrapper bitwiseExpression*
  static boolean bitwiseExpressionWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bitwiseExpressionWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = shiftExpressionWrapper(builder_, level_ + 1);
    result_ = result_ && bitwiseExpressionWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // bitwiseExpression*
  private static boolean bitwiseExpressionWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bitwiseExpressionWrapper_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bitwiseExpression(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bitwiseExpressionWrapper_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '&' | '^' | '|'
  public static boolean bitwiseOperator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bitwiseOperator")) return false;
    if (!nextTokenIs(builder_, "<bitwise operator>", AND, OR, XOR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BITWISE_OPERATOR, "<bitwise operator>");
    result_ = consumeToken(builder_, AND);
    if (!result_) result_ = consumeToken(builder_, XOR);
    if (!result_) result_ = consumeToken(builder_, OR);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '{' statements '}'
  public static boolean block(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block")) return false;
    if (!nextTokenIs(builder_, LBRACE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BLOCK, null);
    result_ = consumeToken(builder_, LBRACE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, statements(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RBRACE) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // ('async' '*'? | 'sync' '*'?)? lazyParseableBlock
  static boolean blockBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = blockBody_0(builder_, level_ + 1);
    result_ = result_ && lazyParseableBlock(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ('async' '*'? | 'sync' '*'?)?
  private static boolean blockBody_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockBody_0")) return false;
    blockBody_0_0(builder_, level_ + 1);
    return true;
  }

  // 'async' '*'? | 'sync' '*'?
  private static boolean blockBody_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockBody_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = blockBody_0_0_0(builder_, level_ + 1);
    if (!result_) result_ = blockBody_0_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'async' '*'?
  private static boolean blockBody_0_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockBody_0_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASYNC);
    result_ = result_ && blockBody_0_0_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // '*'?
  private static boolean blockBody_0_0_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockBody_0_0_0_1")) return false;
    consumeToken(builder_, MUL);
    return true;
  }

  // 'sync' '*'?
  private static boolean blockBody_0_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockBody_0_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SYNC);
    result_ = result_ && blockBody_0_0_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // '*'?
  private static boolean blockBody_0_0_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "blockBody_0_0_1_1")) return false;
    consumeToken(builder_, MUL);
    return true;
  }

  /* ********************************************************** */
  // 'break' referenceExpression? ';'
  public static boolean breakStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "breakStatement")) return false;
    if (!nextTokenIs(builder_, BREAK)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BREAK_STATEMENT, null);
    result_ = consumeToken(builder_, BREAK);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, breakStatement_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, SEMICOLON) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // referenceExpression?
  private static boolean breakStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "breakStatement_1")) return false;
    referenceExpression(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // typeArguments? ('.' 'new')? <<argumentsWrapper>>
  public static boolean callExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "callExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, CALL_EXPRESSION, "<call expression>");
    result_ = callExpression_0(builder_, level_ + 1);
    result_ = result_ && callExpression_1(builder_, level_ + 1);
    result_ = result_ && argumentsWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // typeArguments?
  private static boolean callExpression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "callExpression_0")) return false;
    typeArguments(builder_, level_ + 1);
    return true;
  }

  // ('.' 'new')?
  private static boolean callExpression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "callExpression_1")) return false;
    callExpression_1_0(builder_, level_ + 1);
    return true;
  }

  // '.' 'new'
  private static boolean callExpression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "callExpression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DOT, NEW);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // (callExpression | arrayAccessExpression | qualifiedReferenceExpression | '!' | typeArguments)*
  static boolean callOrArrayAccessOrQualifiedRefExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "callOrArrayAccessOrQualifiedRefExpression")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!callOrArrayAccessOrQualifiedRefExpression_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "callOrArrayAccessOrQualifiedRefExpression", pos_)) break;
    }
    return true;
  }

  // callExpression | arrayAccessExpression | qualifiedReferenceExpression | '!' | typeArguments
  private static boolean callOrArrayAccessOrQualifiedRefExpression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "callOrArrayAccessOrQualifiedRefExpression_0")) return false;
    boolean result_;
    result_ = callExpression(builder_, level_ + 1);
    if (!result_) result_ = arrayAccessExpression(builder_, level_ + 1);
    if (!result_) result_ = qualifiedReferenceExpression(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, NOT);
    if (!result_) result_ = typeArguments(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // ('?..' | '..') << cascadeStopper >> (arrayAccess | refOrThisOrSuperOrParenExpression callOrArrayAccessOrQualifiedRefExpression) << varInitWrapper >>
  public static boolean cascadeReferenceExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cascadeReferenceExpression")) return false;
    if (!nextTokenIs(builder_, "<cascade reference expression>", DOT_DOT, QUEST_DOT_DOT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CASCADE_REFERENCE_EXPRESSION, "<cascade reference expression>");
    result_ = cascadeReferenceExpression_0(builder_, level_ + 1);
    result_ = result_ && cascadeStopper(builder_, level_ + 1);
    result_ = result_ && cascadeReferenceExpression_2(builder_, level_ + 1);
    result_ = result_ && varInitWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // '?..' | '..'
  private static boolean cascadeReferenceExpression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cascadeReferenceExpression_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, QUEST_DOT_DOT);
    if (!result_) result_ = consumeToken(builder_, DOT_DOT);
    return result_;
  }

  // arrayAccess | refOrThisOrSuperOrParenExpression callOrArrayAccessOrQualifiedRefExpression
  private static boolean cascadeReferenceExpression_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cascadeReferenceExpression_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = arrayAccess(builder_, level_ + 1);
    if (!result_) result_ = cascadeReferenceExpression_2_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // refOrThisOrSuperOrParenExpression callOrArrayAccessOrQualifiedRefExpression
  private static boolean cascadeReferenceExpression_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cascadeReferenceExpression_2_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = refOrThisOrSuperOrParenExpression(builder_, level_ + 1);
    result_ = result_ && callOrArrayAccessOrQualifiedRefExpression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'catch' '(' componentName (',' componentName)? ')'
  public static boolean catchPart(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "catchPart")) return false;
    if (!nextTokenIs(builder_, CATCH)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CATCH_PART, null);
    result_ = consumeTokens(builder_, 1, CATCH, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, componentName(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, catchPart_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (',' componentName)?
  private static boolean catchPart_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "catchPart_3")) return false;
    catchPart_3_0(builder_, level_ + 1);
    return true;
  }

  // ',' componentName
  private static boolean catchPart_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "catchPart_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '{' classMembers '}'
  public static boolean classBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classBody")) return false;
    if (!nextTokenIs(builder_, LBRACE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CLASS_BODY, null);
    result_ = consumeToken(builder_, LBRACE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, classMembers(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RBRACE) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // metadata* 'augment'? (mixinClassModifiers | classModifiers) 'class' componentName typeParameters? (mixinApplication | standardClassDeclarationTail)
  public static boolean classDefinition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDefinition")) return false;
    if (!nextTokenIs(builder_, "<class definition>", ABSTRACT, AT,
      AUGMENT, BASE, CLASS, FINAL, INTERFACE, MACRO, MIXIN, SEALED)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CLASS_DEFINITION, "<class definition>");
    result_ = classDefinition_0(builder_, level_ + 1);
    result_ = result_ && classDefinition_1(builder_, level_ + 1);
    result_ = result_ && classDefinition_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLASS);
    result_ = result_ && componentName(builder_, level_ + 1);
    pinned_ = result_; // pin = 5
    result_ = result_ && report_error_(builder_, classDefinition_5(builder_, level_ + 1));
    result_ = pinned_ && classDefinition_6(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean classDefinition_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDefinition_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "classDefinition_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean classDefinition_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDefinition_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // mixinClassModifiers | classModifiers
  private static boolean classDefinition_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDefinition_2")) return false;
    boolean result_;
    result_ = mixinClassModifiers(builder_, level_ + 1);
    if (!result_) result_ = classModifiers(builder_, level_ + 1);
    return result_;
  }

  // typeParameters?
  private static boolean classDefinition_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDefinition_5")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // mixinApplication | standardClassDeclarationTail
  private static boolean classDefinition_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classDefinition_6")) return false;
    boolean result_;
    result_ = mixinApplication(builder_, level_ + 1);
    if (!result_) result_ = standardClassDeclarationTail(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // factoryConstructorDeclaration
  //                                 | namedConstructorDeclaration
  //                                 | getterOrSetterDeclaration
  //                                 | methodDeclaration
  //                                 | varDeclarationListWithSemicolon
  //                                 | incompleteDeclaration
  static boolean classMemberDefinition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classMemberDefinition")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = factoryConstructorDeclaration(builder_, level_ + 1);
    if (!result_) result_ = namedConstructorDeclaration(builder_, level_ + 1);
    if (!result_) result_ = getterOrSetterDeclaration(builder_, level_ + 1);
    if (!result_) result_ = methodDeclaration(builder_, level_ + 1);
    if (!result_) result_ = varDeclarationListWithSemicolon(builder_, level_ + 1);
    if (!result_) result_ = incompleteDeclaration(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, DartParser::class_member_recover);
    return result_;
  }

  /* ********************************************************** */
  // classMemberDefinition*
  public static boolean classMembers(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classMembers")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CLASS_MEMBERS, "<class members>");
    while (true) {
      int pos_ = current_position_(builder_);
      if (!classMemberDefinition(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "classMembers", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, true, false, DartParser::simple_scope_recover);
    return true;
  }

  /* ********************************************************** */
  // 'sealed' | 'macro' | 'abstract'? ('base' | 'interface' | 'final')?
  static boolean classModifiers(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classModifiers")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEALED);
    if (!result_) result_ = consumeToken(builder_, MACRO);
    if (!result_) result_ = classModifiers_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'abstract'? ('base' | 'interface' | 'final')?
  private static boolean classModifiers_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classModifiers_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = classModifiers_2_0(builder_, level_ + 1);
    result_ = result_ && classModifiers_2_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'abstract'?
  private static boolean classModifiers_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classModifiers_2_0")) return false;
    consumeToken(builder_, ABSTRACT);
    return true;
  }

  // ('base' | 'interface' | 'final')?
  private static boolean classModifiers_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classModifiers_2_1")) return false;
    classModifiers_2_1_0(builder_, level_ + 1);
    return true;
  }

  // 'base' | 'interface' | 'final'
  private static boolean classModifiers_2_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "classModifiers_2_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, BASE);
    if (!result_) result_ = consumeToken(builder_, INTERFACE);
    if (!result_) result_ = consumeToken(builder_, FINAL);
    return result_;
  }

  /* ********************************************************** */
  // !(<<nonStrictID>> | 'operator' | '(' | '@' | 'abstract' | 'base' | 'class' | 'const' | 'covariant' |
  //                                  'enum' | 'export' | 'extension' | 'external' | 'factory' | 'final' | 'get' | 'import' | 'interface' |
  //                                  'late' | 'library' | 'mixin' | 'part' | 'sealed' | 'set' | 'static' | 'typedef' | 'var' | 'void' | '}')
  static boolean class_member_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "class_member_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !class_member_recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // <<nonStrictID>> | 'operator' | '(' | '@' | 'abstract' | 'base' | 'class' | 'const' | 'covariant' |
  //                                  'enum' | 'export' | 'extension' | 'external' | 'factory' | 'final' | 'get' | 'import' | 'interface' |
  //                                  'late' | 'library' | 'mixin' | 'part' | 'sealed' | 'set' | 'static' | 'typedef' | 'var' | 'void' | '}'
  private static boolean class_member_recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "class_member_recover_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nonStrictID(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, OPERATOR);
    if (!result_) result_ = consumeToken(builder_, LPAREN);
    if (!result_) result_ = consumeToken(builder_, AT);
    if (!result_) result_ = consumeToken(builder_, ABSTRACT);
    if (!result_) result_ = consumeToken(builder_, BASE);
    if (!result_) result_ = consumeToken(builder_, CLASS);
    if (!result_) result_ = consumeToken(builder_, CONST);
    if (!result_) result_ = consumeToken(builder_, COVARIANT);
    if (!result_) result_ = consumeToken(builder_, ENUM);
    if (!result_) result_ = consumeToken(builder_, EXPORT);
    if (!result_) result_ = consumeToken(builder_, EXTENSION);
    if (!result_) result_ = consumeToken(builder_, EXTERNAL);
    if (!result_) result_ = consumeToken(builder_, FACTORY);
    if (!result_) result_ = consumeToken(builder_, FINAL);
    if (!result_) result_ = consumeToken(builder_, GET);
    if (!result_) result_ = consumeToken(builder_, IMPORT);
    if (!result_) result_ = consumeToken(builder_, INTERFACE);
    if (!result_) result_ = consumeToken(builder_, LATE);
    if (!result_) result_ = consumeToken(builder_, LIBRARY);
    if (!result_) result_ = consumeToken(builder_, MIXIN);
    if (!result_) result_ = consumeToken(builder_, PART);
    if (!result_) result_ = consumeToken(builder_, SEALED);
    if (!result_) result_ = consumeToken(builder_, SET);
    if (!result_) result_ = consumeToken(builder_, STATIC);
    if (!result_) result_ = consumeToken(builder_, TYPEDEF);
    if (!result_) result_ = consumeToken(builder_, VAR);
    if (!result_) result_ = consumeToken(builder_, VOID);
    if (!result_) result_ = consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // showCombinator | hideCombinator
  static boolean combinator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "combinator")) return false;
    if (!nextTokenIs(builder_, "", HIDE, SHOW)) return false;
    boolean result_;
    result_ = showCombinator(builder_, level_ + 1);
    if (!result_) result_ = hideCombinator(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // (relationalOperator | equalityOperator) bitwiseExpressionWrapper
  public static boolean compareExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "compareExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, COMPARE_EXPRESSION, "<compare expression>");
    result_ = compareExpression_0(builder_, level_ + 1);
    result_ = result_ && bitwiseExpressionWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // relationalOperator | equalityOperator
  private static boolean compareExpression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "compareExpression_0")) return false;
    boolean result_;
    result_ = relationalOperator(builder_, level_ + 1);
    if (!result_) result_ = equalityOperator(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // bitwiseExpressionWrapper compareExpression*
  static boolean compareExpressionWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "compareExpressionWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bitwiseExpressionWrapper(builder_, level_ + 1);
    result_ = result_ && compareExpressionWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // compareExpression*
  private static boolean compareExpressionWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "compareExpressionWrapper_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!compareExpression(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "compareExpressionWrapper_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // << nonStrictID >>
  public static boolean componentName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "componentName")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, COMPONENT_NAME, "<component name>");
    result_ = nonStrictID(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // 'const' constructorDesignation arguments
  public static boolean constObjectExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constObjectExpression")) return false;
    if (!nextTokenIs(builder_, CONST)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONST);
    result_ = result_ && constructorDesignation(builder_, level_ + 1);
    result_ = result_ && arguments(builder_, level_ + 1);
    exit_section_(builder_, marker_, CONST_OBJECT_EXPRESSION, result_);
    return result_;
  }

  /* ********************************************************** */
  // NULL | TRUE | FALSE | '-'? (NUMBER | HEX_NUMBER) | stringLiteralExpression | symbolLiteralExpression |
  //                     staticMemberShorthandValue |
  //                     simpleQualifiedReferenceExpression |
  //                     constObjectExpression |
  //                     'const' typeArguments? '[' elements? ']' |
  //                     'const' typeArguments? '{' elements? '}' |
  //                     'const' '(' expression ')'
  public static boolean constantPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constantPattern")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CONSTANT_PATTERN, "<constant pattern>");
    result_ = consumeToken(builder_, NULL);
    if (!result_) result_ = consumeToken(builder_, TRUE);
    if (!result_) result_ = consumeToken(builder_, FALSE);
    if (!result_) result_ = constantPattern_3(builder_, level_ + 1);
    if (!result_) result_ = stringLiteralExpression(builder_, level_ + 1);
    if (!result_) result_ = symbolLiteralExpression(builder_, level_ + 1);
    if (!result_) result_ = staticMemberShorthandValue(builder_, level_ + 1);
    if (!result_) result_ = simpleQualifiedReferenceExpression(builder_, level_ + 1);
    if (!result_) result_ = constObjectExpression(builder_, level_ + 1);
    if (!result_) result_ = constantPattern_9(builder_, level_ + 1);
    if (!result_) result_ = constantPattern_10(builder_, level_ + 1);
    if (!result_) result_ = constantPattern_11(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // '-'? (NUMBER | HEX_NUMBER)
  private static boolean constantPattern_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constantPattern_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = constantPattern_3_0(builder_, level_ + 1);
    result_ = result_ && constantPattern_3_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // '-'?
  private static boolean constantPattern_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constantPattern_3_0")) return false;
    consumeToken(builder_, MINUS);
    return true;
  }

  // NUMBER | HEX_NUMBER
  private static boolean constantPattern_3_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constantPattern_3_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, NUMBER);
    if (!result_) result_ = consumeToken(builder_, HEX_NUMBER);
    return result_;
  }

  // 'const' typeArguments? '[' elements? ']'
  private static boolean constantPattern_9(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constantPattern_9")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONST);
    result_ = result_ && constantPattern_9_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LBRACKET);
    result_ = result_ && constantPattern_9_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // typeArguments?
  private static boolean constantPattern_9_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constantPattern_9_1")) return false;
    typeArguments(builder_, level_ + 1);
    return true;
  }

  // elements?
  private static boolean constantPattern_9_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constantPattern_9_3")) return false;
    elements(builder_, level_ + 1);
    return true;
  }

  // 'const' typeArguments? '{' elements? '}'
  private static boolean constantPattern_10(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constantPattern_10")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONST);
    result_ = result_ && constantPattern_10_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LBRACE);
    result_ = result_ && constantPattern_10_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // typeArguments?
  private static boolean constantPattern_10_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constantPattern_10_1")) return false;
    typeArguments(builder_, level_ + 1);
    return true;
  }

  // elements?
  private static boolean constantPattern_10_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constantPattern_10_3")) return false;
    elements(builder_, level_ + 1);
    return true;
  }

  // 'const' '(' expression ')'
  private static boolean constantPattern_11(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constantPattern_11")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, CONST, LPAREN);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // simpleQualifiedReferenceExpression (typeArguments ('.' referenceExpression)?)?
  static boolean constructorDesignation(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constructorDesignation")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = simpleQualifiedReferenceExpression(builder_, level_ + 1);
    result_ = result_ && constructorDesignation_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (typeArguments ('.' referenceExpression)?)?
  private static boolean constructorDesignation_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constructorDesignation_1")) return false;
    constructorDesignation_1_0(builder_, level_ + 1);
    return true;
  }

  // typeArguments ('.' referenceExpression)?
  private static boolean constructorDesignation_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constructorDesignation_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = typeArguments(builder_, level_ + 1);
    result_ = result_ && constructorDesignation_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ('.' referenceExpression)?
  private static boolean constructorDesignation_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constructorDesignation_1_0_1")) return false;
    constructorDesignation_1_0_1_0(builder_, level_ + 1);
    return true;
  }

  // '.' referenceExpression
  private static boolean constructorDesignation_1_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constructorDesignation_1_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && referenceExpression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'continue' referenceExpression? ';'
  public static boolean continueStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "continueStatement")) return false;
    if (!nextTokenIs(builder_, CONTINUE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CONTINUE_STATEMENT, null);
    result_ = consumeToken(builder_, CONTINUE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, continueStatement_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, SEMICOLON) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // referenceExpression?
  private static boolean continueStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "continueStatement_1")) return false;
    referenceExpression(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // topLevelDefinition*
  static boolean dartUnit(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "dartUnit")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!topLevelDefinition(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "dartUnit", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // metadata* 'required'? finalConstVarOrTypeAndComponentName
  static boolean declaredIdentifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "declaredIdentifier")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = declaredIdentifier_0(builder_, level_ + 1);
    result_ = result_ && declaredIdentifier_1(builder_, level_ + 1);
    result_ = result_ && finalConstVarOrTypeAndComponentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean declaredIdentifier_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "declaredIdentifier_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "declaredIdentifier_0", pos_)) break;
    }
    return true;
  }

  // 'required'?
  private static boolean declaredIdentifier_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "declaredIdentifier_1")) return false;
    consumeToken(builder_, REQUIRED);
    return true;
  }

  /* ********************************************************** */
  // label* 'default' ':' statements
  public static boolean defaultCase(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "defaultCase")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, DEFAULT_CASE, "<default case>");
    result_ = defaultCase_0(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 1, DEFAULT, COLON);
    pinned_ = result_; // pin = 2
    result_ = result_ && statements(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // label*
  private static boolean defaultCase_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "defaultCase_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!label(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "defaultCase_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // normalFormalParameter (('=' | ':') expression)?
  public static boolean defaultFormalNamedParameter(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "defaultFormalNamedParameter")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, DEFAULT_FORMAL_NAMED_PARAMETER, "<default formal named parameter>");
    result_ = normalFormalParameter(builder_, level_ + 1);
    result_ = result_ && defaultFormalNamedParameter_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, DartParser::default_formal_parameter_recover);
    return result_;
  }

  // (('=' | ':') expression)?
  private static boolean defaultFormalNamedParameter_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "defaultFormalNamedParameter_1")) return false;
    defaultFormalNamedParameter_1_0(builder_, level_ + 1);
    return true;
  }

  // ('=' | ':') expression
  private static boolean defaultFormalNamedParameter_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "defaultFormalNamedParameter_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = defaultFormalNamedParameter_1_0_0(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // '=' | ':'
  private static boolean defaultFormalNamedParameter_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "defaultFormalNamedParameter_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, EQ);
    if (!result_) result_ = consumeToken(builder_, COLON);
    return result_;
  }

  /* ********************************************************** */
  // !(')' | ',' | ']' | '}')
  static boolean default_formal_parameter_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "default_formal_parameter_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !default_formal_parameter_recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ')' | ',' | ']' | '}'
  private static boolean default_formal_parameter_recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "default_formal_parameter_recover_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, RPAREN);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = consumeToken(builder_, RBRACKET);
    if (!result_) result_ = consumeToken(builder_, RBRACE);
    return result_;
  }

  /* ********************************************************** */
  // 'do' statement 'while' '(' expressionWithRecoverUntilParen ')' ';'
  public static boolean doWhileStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "doWhileStatement")) return false;
    if (!nextTokenIs(builder_, DO)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, DO_WHILE_STATEMENT, null);
    result_ = consumeToken(builder_, DO);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, statement(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeTokens(builder_, -1, WHILE, LPAREN)) && result_;
    result_ = pinned_ && report_error_(builder_, expressionWithRecoverUntilParen(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, consumeTokens(builder_, -1, RPAREN, SEMICOLON)) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // <<nonStrictID>> ('.' <<nonStrictID>>)*
  static boolean dottedName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "dottedName")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nonStrictID(builder_, level_ + 1);
    result_ = result_ && dottedName_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ('.' <<nonStrictID>>)*
  private static boolean dottedName_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "dottedName_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!dottedName_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "dottedName_1", pos_)) break;
    }
    return true;
  }

  // '.' <<nonStrictID>>
  private static boolean dottedName_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "dottedName_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && nonStrictID(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // spreadElement | ifElement | forElement | mapEntryOrExpression
  public static boolean element(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "element")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ELEMENT, "<element>");
    result_ = spreadElement(builder_, level_ + 1);
    if (!result_) result_ = ifElement(builder_, level_ + 1);
    if (!result_) result_ = forElement(builder_, level_ + 1);
    if (!result_) result_ = mapEntryOrExpression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // element (','? element)* ','?
  static boolean elements(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elements")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = element(builder_, level_ + 1);
    result_ = result_ && elements_1(builder_, level_ + 1);
    result_ = result_ && elements_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (','? element)*
  private static boolean elements_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elements_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!elements_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "elements_1", pos_)) break;
    }
    return true;
  }

  // ','? element
  private static boolean elements_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elements_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = elements_1_0_0(builder_, level_ + 1);
    result_ = result_ && element(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean elements_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elements_1_0_0")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  // ','?
  private static boolean elements_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elements_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // metadata* 'augment'? componentName typeArguments? '.' componentName arguments |
  //                             metadata* 'augment'? componentName typeArguments? arguments?
  public static boolean enumConstantDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumConstantDeclaration")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ENUM_CONSTANT_DECLARATION, "<enum constant declaration>");
    result_ = enumConstantDeclaration_0(builder_, level_ + 1);
    if (!result_) result_ = enumConstantDeclaration_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata* 'augment'? componentName typeArguments? '.' componentName arguments
  private static boolean enumConstantDeclaration_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumConstantDeclaration_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = enumConstantDeclaration_0_0(builder_, level_ + 1);
    result_ = result_ && enumConstantDeclaration_0_1(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && enumConstantDeclaration_0_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOT);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && arguments(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean enumConstantDeclaration_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumConstantDeclaration_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "enumConstantDeclaration_0_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean enumConstantDeclaration_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumConstantDeclaration_0_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // typeArguments?
  private static boolean enumConstantDeclaration_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumConstantDeclaration_0_3")) return false;
    typeArguments(builder_, level_ + 1);
    return true;
  }

  // metadata* 'augment'? componentName typeArguments? arguments?
  private static boolean enumConstantDeclaration_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumConstantDeclaration_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = enumConstantDeclaration_1_0(builder_, level_ + 1);
    result_ = result_ && enumConstantDeclaration_1_1(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && enumConstantDeclaration_1_3(builder_, level_ + 1);
    result_ = result_ && enumConstantDeclaration_1_4(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean enumConstantDeclaration_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumConstantDeclaration_1_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "enumConstantDeclaration_1_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean enumConstantDeclaration_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumConstantDeclaration_1_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // typeArguments?
  private static boolean enumConstantDeclaration_1_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumConstantDeclaration_1_3")) return false;
    typeArguments(builder_, level_ + 1);
    return true;
  }

  // arguments?
  private static boolean enumConstantDeclaration_1_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumConstantDeclaration_1_4")) return false;
    arguments(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // metadata* 'augment'? 'enum' componentName typeParameters? mixins? interfaces? '{' enumConstantDeclaration? (',' enumConstantDeclaration)* ','? ';'? classMembers '}'
  public static boolean enumDefinition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumDefinition")) return false;
    if (!nextTokenIs(builder_, "<enum definition>", AT, AUGMENT, ENUM)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ENUM_DEFINITION, "<enum definition>");
    result_ = enumDefinition_0(builder_, level_ + 1);
    result_ = result_ && enumDefinition_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ENUM);
    result_ = result_ && componentName(builder_, level_ + 1);
    pinned_ = result_; // pin = 4
    result_ = result_ && report_error_(builder_, enumDefinition_4(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, enumDefinition_5(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, enumDefinition_6(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, LBRACE)) && result_;
    result_ = pinned_ && report_error_(builder_, enumDefinition_8(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, enumDefinition_9(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, enumDefinition_10(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, enumDefinition_11(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, classMembers(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, RBRACE) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean enumDefinition_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumDefinition_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "enumDefinition_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean enumDefinition_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumDefinition_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // typeParameters?
  private static boolean enumDefinition_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumDefinition_4")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // mixins?
  private static boolean enumDefinition_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumDefinition_5")) return false;
    mixins(builder_, level_ + 1);
    return true;
  }

  // interfaces?
  private static boolean enumDefinition_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumDefinition_6")) return false;
    interfaces(builder_, level_ + 1);
    return true;
  }

  // enumConstantDeclaration?
  private static boolean enumDefinition_8(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumDefinition_8")) return false;
    enumConstantDeclaration(builder_, level_ + 1);
    return true;
  }

  // (',' enumConstantDeclaration)*
  private static boolean enumDefinition_9(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumDefinition_9")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!enumDefinition_9_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "enumDefinition_9", pos_)) break;
    }
    return true;
  }

  // ',' enumConstantDeclaration
  private static boolean enumDefinition_9_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumDefinition_9_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && enumConstantDeclaration(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean enumDefinition_10(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumDefinition_10")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  // ';'?
  private static boolean enumDefinition_11(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enumDefinition_11")) return false;
    consumeToken(builder_, SEMICOLON);
    return true;
  }

  /* ********************************************************** */
  // '==' | '!='
  public static boolean equalityOperator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "equalityOperator")) return false;
    if (!nextTokenIs(builder_, "<equality operator>", EQ_EQ, NEQ)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EQUALITY_OPERATOR, "<equality operator>");
    result_ = consumeToken(builder_, EQ_EQ);
    if (!result_) result_ = consumeToken(builder_, NEQ);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // metadata* 'export' uriElement importConfig* combinator* ';'
  public static boolean exportStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "exportStatement")) return false;
    if (!nextTokenIs(builder_, "<export statement>", AT, EXPORT)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EXPORT_STATEMENT, "<export statement>");
    result_ = exportStatement_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EXPORT);
    result_ = result_ && uriElement(builder_, level_ + 1);
    pinned_ = result_; // pin = 3
    result_ = result_ && report_error_(builder_, exportStatement_3(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, exportStatement_4(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, SEMICOLON) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean exportStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "exportStatement_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "exportStatement_0", pos_)) break;
    }
    return true;
  }

  // importConfig*
  private static boolean exportStatement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "exportStatement_3")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!importConfig(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "exportStatement_3", pos_)) break;
    }
    return true;
  }

  // combinator*
  private static boolean exportStatement_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "exportStatement_4")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!combinator(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "exportStatement_4", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // assignExpressionWrapper
  public static boolean expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, EXPRESSION, "<expression>");
    result_ = assignExpressionWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, DartParser::expression_recover);
    return result_;
  }

  /* ********************************************************** */
  // 'case' expression ':'
  static boolean expressionCase(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expressionCase")) return false;
    if (!nextTokenIs(builder_, CASE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, CASE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expression(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, COLON) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // expression | statement
  static boolean expressionInParentheses(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expressionInParentheses")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = expression(builder_, level_ + 1);
    if (!result_) result_ = statement(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, DartParser::parenthesesRecovery);
    return result_;
  }

  /* ********************************************************** */
  // expression (','? expression)*
  public static boolean expressionList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expressionList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EXPRESSION_LIST, "<expression list>");
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && expressionList_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (','? expression)*
  private static boolean expressionList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expressionList_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!expressionList_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "expressionList_1", pos_)) break;
    }
    return true;
  }

  // ','? expression
  private static boolean expressionList_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expressionList_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = expressionList_1_0_0(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean expressionList_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expressionList_1_0_0")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // expression
  static boolean expressionWithRecoverUntilParen(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expressionWithRecoverUntilParen")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, DartParser::not_paren_recover);
    return result_;
  }

  /* ********************************************************** */
  // expression
  static boolean expressionWithRecoverUntilParenOrComma(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expressionWithRecoverUntilParenOrComma")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, DartParser::not_paren_or_comma_recover);
    return result_;
  }

  /* ********************************************************** */
  // !(<<nonStrictID>> |
  //                                'async' | 'sync' | 'operator'
  //                                <<gtEq>> | <<gtGt>> | <<gtGtEq>> | <<gtGtGt>> | <<gtGtGtEq>> |
  //                                '!' | '!=' | '#' | '%' | '%=' | '&&' | '&&=' | '&' | '&=' | '(' | ')' | '*' | '*=' | '+' | '++' | '+=' |
  //                                ',' | '-' | '--' | '-=' | '.' | '..' | '...' | '...?' | '/' | '/=' | ':' | ';' | '<' | '<<' | '<<=' | '<=' |
  //                                '=' | '==' | '=>' | '>' | '?' | '?.' | '?..' | '??' | '??=' | '@' | '[' | ']' | '^' | '^=' | 'abstract' |
  //                                'as' | 'assert' | 'await' | 'base' | 'break' | 'case' | 'catch' | 'class' | 'const' | 'continue' |
  //                                'covariant' | 'default' | 'deferred' | 'do' | 'else' | 'enum' | 'export' | 'extension' | 'external' |
  //                                'factory' | 'final' | 'finally' | 'for' | 'get' | 'hide' | 'if' | 'import' | 'interface' | 'is' | 'late' |
  //                                'library' | 'mixin' | 'native' | 'new' | 'on' | 'part' | 'rethrow' | 'return' | 'sealed' | 'set' | 'show' |
  //                                'static' | 'super' | 'switch' | 'this' | 'throw' | 'try' | 'typedef' | 'var' | 'void' | 'when' | 'while' |
  //                                'yield' | '{' | '|' | '|=' | '||' | '||=' | '}' | '~' | '~/' | '~/=' | CLOSING_QUOTE | FALSE | HEX_NUMBER |
  //                                LONG_TEMPLATE_ENTRY_END | LONG_TEMPLATE_ENTRY_START | NULL | NUMBER | OPEN_QUOTE | RAW_SINGLE_QUOTED_STRING |
  //                                RAW_TRIPLE_QUOTED_STRING | REGULAR_STRING_PART | SHORT_TEMPLATE_ENTRY_START | TRUE)
  static boolean expression_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !expression_recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // <<nonStrictID>> |
  //                                'async' | 'sync' | 'operator'
  //                                <<gtEq>> | <<gtGt>> | <<gtGtEq>> | <<gtGtGt>> | <<gtGtGtEq>> |
  //                                '!' | '!=' | '#' | '%' | '%=' | '&&' | '&&=' | '&' | '&=' | '(' | ')' | '*' | '*=' | '+' | '++' | '+=' |
  //                                ',' | '-' | '--' | '-=' | '.' | '..' | '...' | '...?' | '/' | '/=' | ':' | ';' | '<' | '<<' | '<<=' | '<=' |
  //                                '=' | '==' | '=>' | '>' | '?' | '?.' | '?..' | '??' | '??=' | '@' | '[' | ']' | '^' | '^=' | 'abstract' |
  //                                'as' | 'assert' | 'await' | 'base' | 'break' | 'case' | 'catch' | 'class' | 'const' | 'continue' |
  //                                'covariant' | 'default' | 'deferred' | 'do' | 'else' | 'enum' | 'export' | 'extension' | 'external' |
  //                                'factory' | 'final' | 'finally' | 'for' | 'get' | 'hide' | 'if' | 'import' | 'interface' | 'is' | 'late' |
  //                                'library' | 'mixin' | 'native' | 'new' | 'on' | 'part' | 'rethrow' | 'return' | 'sealed' | 'set' | 'show' |
  //                                'static' | 'super' | 'switch' | 'this' | 'throw' | 'try' | 'typedef' | 'var' | 'void' | 'when' | 'while' |
  //                                'yield' | '{' | '|' | '|=' | '||' | '||=' | '}' | '~' | '~/' | '~/=' | CLOSING_QUOTE | FALSE | HEX_NUMBER |
  //                                LONG_TEMPLATE_ENTRY_END | LONG_TEMPLATE_ENTRY_START | NULL | NUMBER | OPEN_QUOTE | RAW_SINGLE_QUOTED_STRING |
  //                                RAW_TRIPLE_QUOTED_STRING | REGULAR_STRING_PART | SHORT_TEMPLATE_ENTRY_START | TRUE
  private static boolean expression_recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_recover_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nonStrictID(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, ASYNC);
    if (!result_) result_ = consumeToken(builder_, SYNC);
    if (!result_) result_ = expression_recover_0_3(builder_, level_ + 1);
    if (!result_) result_ = gtGt(builder_, level_ + 1);
    if (!result_) result_ = gtGtEq(builder_, level_ + 1);
    if (!result_) result_ = gtGtGt(builder_, level_ + 1);
    if (!result_) result_ = gtGtGtEq(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, NOT);
    if (!result_) result_ = consumeToken(builder_, NEQ);
    if (!result_) result_ = consumeToken(builder_, HASH);
    if (!result_) result_ = consumeToken(builder_, REM);
    if (!result_) result_ = consumeToken(builder_, REM_EQ);
    if (!result_) result_ = consumeToken(builder_, AND_AND);
    if (!result_) result_ = consumeToken(builder_, AND_AND_EQ);
    if (!result_) result_ = consumeToken(builder_, AND);
    if (!result_) result_ = consumeToken(builder_, AND_EQ);
    if (!result_) result_ = consumeToken(builder_, LPAREN);
    if (!result_) result_ = consumeToken(builder_, RPAREN);
    if (!result_) result_ = consumeToken(builder_, MUL);
    if (!result_) result_ = consumeToken(builder_, MUL_EQ);
    if (!result_) result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, PLUS_PLUS);
    if (!result_) result_ = consumeToken(builder_, PLUS_EQ);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, MINUS_MINUS);
    if (!result_) result_ = consumeToken(builder_, MINUS_EQ);
    if (!result_) result_ = consumeToken(builder_, DOT);
    if (!result_) result_ = consumeToken(builder_, DOT_DOT);
    if (!result_) result_ = consumeToken(builder_, DOT_DOT_DOT);
    if (!result_) result_ = consumeToken(builder_, DOT_DOT_DOT_QUEST);
    if (!result_) result_ = consumeToken(builder_, DIV);
    if (!result_) result_ = consumeToken(builder_, DIV_EQ);
    if (!result_) result_ = consumeToken(builder_, COLON);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = consumeToken(builder_, LT);
    if (!result_) result_ = consumeToken(builder_, LT_LT);
    if (!result_) result_ = consumeToken(builder_, LT_LT_EQ);
    if (!result_) result_ = consumeToken(builder_, LT_EQ);
    if (!result_) result_ = consumeToken(builder_, EQ);
    if (!result_) result_ = consumeToken(builder_, EQ_EQ);
    if (!result_) result_ = consumeToken(builder_, EXPRESSION_BODY_DEF);
    if (!result_) result_ = consumeToken(builder_, GT);
    if (!result_) result_ = consumeToken(builder_, QUEST);
    if (!result_) result_ = consumeToken(builder_, QUEST_DOT);
    if (!result_) result_ = consumeToken(builder_, QUEST_DOT_DOT);
    if (!result_) result_ = consumeToken(builder_, QUEST_QUEST);
    if (!result_) result_ = consumeToken(builder_, QUEST_QUEST_EQ);
    if (!result_) result_ = consumeToken(builder_, AT);
    if (!result_) result_ = consumeToken(builder_, LBRACKET);
    if (!result_) result_ = consumeToken(builder_, RBRACKET);
    if (!result_) result_ = consumeToken(builder_, XOR);
    if (!result_) result_ = consumeToken(builder_, XOR_EQ);
    if (!result_) result_ = consumeToken(builder_, ABSTRACT);
    if (!result_) result_ = consumeToken(builder_, AS);
    if (!result_) result_ = consumeToken(builder_, ASSERT);
    if (!result_) result_ = consumeToken(builder_, AWAIT);
    if (!result_) result_ = consumeToken(builder_, BASE);
    if (!result_) result_ = consumeToken(builder_, BREAK);
    if (!result_) result_ = consumeToken(builder_, CASE);
    if (!result_) result_ = consumeToken(builder_, CATCH);
    if (!result_) result_ = consumeToken(builder_, CLASS);
    if (!result_) result_ = consumeToken(builder_, CONST);
    if (!result_) result_ = consumeToken(builder_, CONTINUE);
    if (!result_) result_ = consumeToken(builder_, COVARIANT);
    if (!result_) result_ = consumeToken(builder_, DEFAULT);
    if (!result_) result_ = consumeToken(builder_, DEFERRED);
    if (!result_) result_ = consumeToken(builder_, DO);
    if (!result_) result_ = consumeToken(builder_, ELSE);
    if (!result_) result_ = consumeToken(builder_, ENUM);
    if (!result_) result_ = consumeToken(builder_, EXPORT);
    if (!result_) result_ = consumeToken(builder_, EXTENSION);
    if (!result_) result_ = consumeToken(builder_, EXTERNAL);
    if (!result_) result_ = consumeToken(builder_, FACTORY);
    if (!result_) result_ = consumeToken(builder_, FINAL);
    if (!result_) result_ = consumeToken(builder_, FINALLY);
    if (!result_) result_ = consumeToken(builder_, FOR);
    if (!result_) result_ = consumeToken(builder_, GET);
    if (!result_) result_ = consumeToken(builder_, HIDE);
    if (!result_) result_ = consumeToken(builder_, IF);
    if (!result_) result_ = consumeToken(builder_, IMPORT);
    if (!result_) result_ = consumeToken(builder_, INTERFACE);
    if (!result_) result_ = consumeToken(builder_, IS);
    if (!result_) result_ = consumeToken(builder_, LATE);
    if (!result_) result_ = consumeToken(builder_, LIBRARY);
    if (!result_) result_ = consumeToken(builder_, MIXIN);
    if (!result_) result_ = consumeToken(builder_, NATIVE);
    if (!result_) result_ = consumeToken(builder_, NEW);
    if (!result_) result_ = consumeToken(builder_, ON);
    if (!result_) result_ = consumeToken(builder_, PART);
    if (!result_) result_ = consumeToken(builder_, RETHROW);
    if (!result_) result_ = consumeToken(builder_, RETURN);
    if (!result_) result_ = consumeToken(builder_, SEALED);
    if (!result_) result_ = consumeToken(builder_, SET);
    if (!result_) result_ = consumeToken(builder_, SHOW);
    if (!result_) result_ = consumeToken(builder_, STATIC);
    if (!result_) result_ = consumeToken(builder_, SUPER);
    if (!result_) result_ = consumeToken(builder_, SWITCH);
    if (!result_) result_ = consumeToken(builder_, THIS);
    if (!result_) result_ = consumeToken(builder_, THROW);
    if (!result_) result_ = consumeToken(builder_, TRY);
    if (!result_) result_ = consumeToken(builder_, TYPEDEF);
    if (!result_) result_ = consumeToken(builder_, VAR);
    if (!result_) result_ = consumeToken(builder_, VOID);
    if (!result_) result_ = consumeToken(builder_, WHEN);
    if (!result_) result_ = consumeToken(builder_, WHILE);
    if (!result_) result_ = consumeToken(builder_, YIELD);
    if (!result_) result_ = consumeToken(builder_, LBRACE);
    if (!result_) result_ = consumeToken(builder_, OR);
    if (!result_) result_ = consumeToken(builder_, OR_EQ);
    if (!result_) result_ = consumeToken(builder_, OR_OR);
    if (!result_) result_ = consumeToken(builder_, OR_OR_EQ);
    if (!result_) result_ = consumeToken(builder_, RBRACE);
    if (!result_) result_ = consumeToken(builder_, BIN_NOT);
    if (!result_) result_ = consumeToken(builder_, INT_DIV);
    if (!result_) result_ = consumeToken(builder_, INT_DIV_EQ);
    if (!result_) result_ = consumeToken(builder_, CLOSING_QUOTE);
    if (!result_) result_ = consumeToken(builder_, FALSE);
    if (!result_) result_ = consumeToken(builder_, HEX_NUMBER);
    if (!result_) result_ = consumeToken(builder_, LONG_TEMPLATE_ENTRY_END);
    if (!result_) result_ = consumeToken(builder_, LONG_TEMPLATE_ENTRY_START);
    if (!result_) result_ = consumeToken(builder_, NULL);
    if (!result_) result_ = consumeToken(builder_, NUMBER);
    if (!result_) result_ = consumeToken(builder_, OPEN_QUOTE);
    if (!result_) result_ = consumeToken(builder_, RAW_SINGLE_QUOTED_STRING);
    if (!result_) result_ = consumeToken(builder_, RAW_TRIPLE_QUOTED_STRING);
    if (!result_) result_ = consumeToken(builder_, REGULAR_STRING_PART);
    if (!result_) result_ = consumeToken(builder_, SHORT_TEMPLATE_ENTRY_START);
    if (!result_) result_ = consumeToken(builder_, TRUE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'operator'
  //                                <<gtEq>>
  private static boolean expression_recover_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_recover_0_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OPERATOR);
    result_ = result_ && gtEq(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // metadata* 'augment'? 'extension' typeParameters? 'on' type '?'? classBody |
  //                          metadata* 'augment'? 'extension' <<nonStrictID>> typeParameters? 'on' type '?'? classBody
  public static boolean extensionDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionDeclaration")) return false;
    if (!nextTokenIs(builder_, "<extension declaration>", AT, AUGMENT, EXTENSION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EXTENSION_DECLARATION, "<extension declaration>");
    result_ = extensionDeclaration_0(builder_, level_ + 1);
    if (!result_) result_ = extensionDeclaration_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata* 'augment'? 'extension' typeParameters? 'on' type '?'? classBody
  private static boolean extensionDeclaration_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionDeclaration_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = extensionDeclaration_0_0(builder_, level_ + 1);
    result_ = result_ && extensionDeclaration_0_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EXTENSION);
    result_ = result_ && extensionDeclaration_0_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ON);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && extensionDeclaration_0_6(builder_, level_ + 1);
    result_ = result_ && classBody(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean extensionDeclaration_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionDeclaration_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "extensionDeclaration_0_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean extensionDeclaration_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionDeclaration_0_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // typeParameters?
  private static boolean extensionDeclaration_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionDeclaration_0_3")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // '?'?
  private static boolean extensionDeclaration_0_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionDeclaration_0_6")) return false;
    consumeToken(builder_, QUEST);
    return true;
  }

  // metadata* 'augment'? 'extension' <<nonStrictID>> typeParameters? 'on' type '?'? classBody
  private static boolean extensionDeclaration_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionDeclaration_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = extensionDeclaration_1_0(builder_, level_ + 1);
    result_ = result_ && extensionDeclaration_1_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EXTENSION);
    result_ = result_ && nonStrictID(builder_, level_ + 1);
    result_ = result_ && extensionDeclaration_1_4(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ON);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && extensionDeclaration_1_7(builder_, level_ + 1);
    result_ = result_ && classBody(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean extensionDeclaration_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionDeclaration_1_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "extensionDeclaration_1_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean extensionDeclaration_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionDeclaration_1_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // typeParameters?
  private static boolean extensionDeclaration_1_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionDeclaration_1_4")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // '?'?
  private static boolean extensionDeclaration_1_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionDeclaration_1_7")) return false;
    consumeToken(builder_, QUEST);
    return true;
  }

  /* ********************************************************** */
  // metadata* 'augment'? 'extension' <<typeForExtensionType>> 'const'? componentName typeParameters? representationDeclaration interfaces? classBody
  public static boolean extensionTypeDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionTypeDeclaration")) return false;
    if (!nextTokenIs(builder_, "<extension type declaration>", AT, AUGMENT, EXTENSION)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EXTENSION_TYPE_DECLARATION, "<extension type declaration>");
    result_ = extensionTypeDeclaration_0(builder_, level_ + 1);
    result_ = result_ && extensionTypeDeclaration_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EXTENSION);
    result_ = result_ && typeForExtensionType(builder_, level_ + 1);
    result_ = result_ && extensionTypeDeclaration_4(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    pinned_ = result_; // pin = 6
    result_ = result_ && report_error_(builder_, extensionTypeDeclaration_6(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, representationDeclaration(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, extensionTypeDeclaration_8(builder_, level_ + 1)) && result_;
    result_ = pinned_ && classBody(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean extensionTypeDeclaration_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionTypeDeclaration_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "extensionTypeDeclaration_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean extensionTypeDeclaration_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionTypeDeclaration_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // 'const'?
  private static boolean extensionTypeDeclaration_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionTypeDeclaration_4")) return false;
    consumeToken(builder_, CONST);
    return true;
  }

  // typeParameters?
  private static boolean extensionTypeDeclaration_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionTypeDeclaration_6")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // interfaces?
  private static boolean extensionTypeDeclaration_8(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extensionTypeDeclaration_8")) return false;
    interfaces(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // metadata* 'augment'? ('external' | 'const')* 'factory' componentName ('.' componentName)? formalParameterList factoryTail?
  public static boolean factoryConstructorDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "factoryConstructorDeclaration")) return false;
    if (!nextTokenIs(builder_, "<factory constructor declaration>", AT, AUGMENT,
      CONST, EXTERNAL, FACTORY)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FACTORY_CONSTRUCTOR_DECLARATION, "<factory constructor declaration>");
    result_ = factoryConstructorDeclaration_0(builder_, level_ + 1);
    result_ = result_ && factoryConstructorDeclaration_1(builder_, level_ + 1);
    result_ = result_ && factoryConstructorDeclaration_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, FACTORY);
    pinned_ = result_; // pin = 4
    result_ = result_ && report_error_(builder_, componentName(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, factoryConstructorDeclaration_5(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, formalParameterList(builder_, level_ + 1)) && result_;
    result_ = pinned_ && factoryConstructorDeclaration_7(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean factoryConstructorDeclaration_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "factoryConstructorDeclaration_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "factoryConstructorDeclaration_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean factoryConstructorDeclaration_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "factoryConstructorDeclaration_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // ('external' | 'const')*
  private static boolean factoryConstructorDeclaration_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "factoryConstructorDeclaration_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!factoryConstructorDeclaration_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "factoryConstructorDeclaration_2", pos_)) break;
    }
    return true;
  }

  // 'external' | 'const'
  private static boolean factoryConstructorDeclaration_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "factoryConstructorDeclaration_2_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, EXTERNAL);
    if (!result_) result_ = consumeToken(builder_, CONST);
    return result_;
  }

  // ('.' componentName)?
  private static boolean factoryConstructorDeclaration_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "factoryConstructorDeclaration_5")) return false;
    factoryConstructorDeclaration_5_0(builder_, level_ + 1);
    return true;
  }

  // '.' componentName
  private static boolean factoryConstructorDeclaration_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "factoryConstructorDeclaration_5_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // factoryTail?
  private static boolean factoryConstructorDeclaration_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "factoryConstructorDeclaration_7")) return false;
    factoryTail(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // varFactoryDeclaration ';' | functionBodyOrNative | ';'
  static boolean factoryTail(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "factoryTail")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = factoryTail_0(builder_, level_ + 1);
    if (!result_) result_ = functionBodyOrNative(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // varFactoryDeclaration ';'
  private static boolean factoryTail_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "factoryTail_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = varFactoryDeclaration(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && consumeToken(builder_, SEMICOLON);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // metadata* 'required'? finalConstVarVoidOrType? ('this' | 'super') '.' referenceExpression typeParameters? formalParameterList?
  public static boolean fieldFormalParameter(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldFormalParameter")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FIELD_FORMAL_PARAMETER, "<field formal parameter>");
    result_ = fieldFormalParameter_0(builder_, level_ + 1);
    result_ = result_ && fieldFormalParameter_1(builder_, level_ + 1);
    result_ = result_ && fieldFormalParameter_2(builder_, level_ + 1);
    result_ = result_ && fieldFormalParameter_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOT);
    result_ = result_ && referenceExpression(builder_, level_ + 1);
    result_ = result_ && fieldFormalParameter_6(builder_, level_ + 1);
    result_ = result_ && fieldFormalParameter_7(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata*
  private static boolean fieldFormalParameter_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldFormalParameter_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "fieldFormalParameter_0", pos_)) break;
    }
    return true;
  }

  // 'required'?
  private static boolean fieldFormalParameter_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldFormalParameter_1")) return false;
    consumeToken(builder_, REQUIRED);
    return true;
  }

  // finalConstVarVoidOrType?
  private static boolean fieldFormalParameter_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldFormalParameter_2")) return false;
    finalConstVarVoidOrType(builder_, level_ + 1);
    return true;
  }

  // 'this' | 'super'
  private static boolean fieldFormalParameter_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldFormalParameter_3")) return false;
    boolean result_;
    result_ = consumeToken(builder_, THIS);
    if (!result_) result_ = consumeToken(builder_, SUPER);
    return result_;
  }

  // typeParameters?
  private static boolean fieldFormalParameter_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldFormalParameter_6")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // formalParameterList?
  private static boolean fieldFormalParameter_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldFormalParameter_7")) return false;
    formalParameterList(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // ('this' '.')? referenceExpression '=' expression
  public static boolean fieldInitializer(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldInitializer")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FIELD_INITIALIZER, "<field initializer>");
    result_ = fieldInitializer_0(builder_, level_ + 1);
    result_ = result_ && referenceExpression(builder_, level_ + 1);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, consumeToken(builder_, EQ));
    result_ = pinned_ && expression(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // ('this' '.')?
  private static boolean fieldInitializer_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldInitializer_0")) return false;
    fieldInitializer_0_0(builder_, level_ + 1);
    return true;
  }

  // 'this' '.'
  private static boolean fieldInitializer_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fieldInitializer_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, THIS, DOT);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'covariant'? 'late'? 'final' type componentName |
  //                                                 'covariant'? 'late'? 'final'      componentName |
  //                                                 'covariant'?         'const' type componentName |
  //                                                 'covariant'?         'const'      componentName |
  //                                                 'covariant'? 'late'? 'var'        componentName |
  //                                                 'covariant'  'late'          type componentName |
  //                                                 'covariant'                  type componentName |
  //                                                              'late'          type componentName |
  //                                                                              type componentName
  static boolean finalConstVarOrTypeAndComponentName(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = finalConstVarOrTypeAndComponentName_0(builder_, level_ + 1);
    if (!result_) result_ = finalConstVarOrTypeAndComponentName_1(builder_, level_ + 1);
    if (!result_) result_ = finalConstVarOrTypeAndComponentName_2(builder_, level_ + 1);
    if (!result_) result_ = finalConstVarOrTypeAndComponentName_3(builder_, level_ + 1);
    if (!result_) result_ = finalConstVarOrTypeAndComponentName_4(builder_, level_ + 1);
    if (!result_) result_ = finalConstVarOrTypeAndComponentName_5(builder_, level_ + 1);
    if (!result_) result_ = finalConstVarOrTypeAndComponentName_6(builder_, level_ + 1);
    if (!result_) result_ = finalConstVarOrTypeAndComponentName_7(builder_, level_ + 1);
    if (!result_) result_ = finalConstVarOrTypeAndComponentName_8(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'covariant'? 'late'? 'final' type componentName
  private static boolean finalConstVarOrTypeAndComponentName_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = finalConstVarOrTypeAndComponentName_0_0(builder_, level_ + 1);
    result_ = result_ && finalConstVarOrTypeAndComponentName_0_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, FINAL);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'covariant'?
  private static boolean finalConstVarOrTypeAndComponentName_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_0_0")) return false;
    consumeToken(builder_, COVARIANT);
    return true;
  }

  // 'late'?
  private static boolean finalConstVarOrTypeAndComponentName_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_0_1")) return false;
    consumeToken(builder_, LATE);
    return true;
  }

  // 'covariant'? 'late'? 'final'      componentName
  private static boolean finalConstVarOrTypeAndComponentName_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = finalConstVarOrTypeAndComponentName_1_0(builder_, level_ + 1);
    result_ = result_ && finalConstVarOrTypeAndComponentName_1_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, FINAL);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'covariant'?
  private static boolean finalConstVarOrTypeAndComponentName_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_1_0")) return false;
    consumeToken(builder_, COVARIANT);
    return true;
  }

  // 'late'?
  private static boolean finalConstVarOrTypeAndComponentName_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_1_1")) return false;
    consumeToken(builder_, LATE);
    return true;
  }

  // 'covariant'?         'const' type componentName
  private static boolean finalConstVarOrTypeAndComponentName_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = finalConstVarOrTypeAndComponentName_2_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CONST);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'covariant'?
  private static boolean finalConstVarOrTypeAndComponentName_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_2_0")) return false;
    consumeToken(builder_, COVARIANT);
    return true;
  }

  // 'covariant'?         'const'      componentName
  private static boolean finalConstVarOrTypeAndComponentName_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = finalConstVarOrTypeAndComponentName_3_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CONST);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'covariant'?
  private static boolean finalConstVarOrTypeAndComponentName_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_3_0")) return false;
    consumeToken(builder_, COVARIANT);
    return true;
  }

  // 'covariant'? 'late'? 'var'        componentName
  private static boolean finalConstVarOrTypeAndComponentName_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = finalConstVarOrTypeAndComponentName_4_0(builder_, level_ + 1);
    result_ = result_ && finalConstVarOrTypeAndComponentName_4_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, VAR);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'covariant'?
  private static boolean finalConstVarOrTypeAndComponentName_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_4_0")) return false;
    consumeToken(builder_, COVARIANT);
    return true;
  }

  // 'late'?
  private static boolean finalConstVarOrTypeAndComponentName_4_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_4_1")) return false;
    consumeToken(builder_, LATE);
    return true;
  }

  // 'covariant'  'late'          type componentName
  private static boolean finalConstVarOrTypeAndComponentName_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COVARIANT, LATE);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'covariant'                  type componentName
  private static boolean finalConstVarOrTypeAndComponentName_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_6")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COVARIANT);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'late'          type componentName
  private static boolean finalConstVarOrTypeAndComponentName_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_7")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LATE);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // type componentName
  private static boolean finalConstVarOrTypeAndComponentName_8(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarOrTypeAndComponentName_8")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = type(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'late'? 'final' type? | 'const' type? | 'late'? 'var' | 'late'? 'void' | 'late' type | type
  static boolean finalConstVarVoidOrType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarVoidOrType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = finalConstVarVoidOrType_0(builder_, level_ + 1);
    if (!result_) result_ = finalConstVarVoidOrType_1(builder_, level_ + 1);
    if (!result_) result_ = finalConstVarVoidOrType_2(builder_, level_ + 1);
    if (!result_) result_ = finalConstVarVoidOrType_3(builder_, level_ + 1);
    if (!result_) result_ = finalConstVarVoidOrType_4(builder_, level_ + 1);
    if (!result_) result_ = type(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'late'? 'final' type?
  private static boolean finalConstVarVoidOrType_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarVoidOrType_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = finalConstVarVoidOrType_0_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, FINAL);
    result_ = result_ && finalConstVarVoidOrType_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'late'?
  private static boolean finalConstVarVoidOrType_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarVoidOrType_0_0")) return false;
    consumeToken(builder_, LATE);
    return true;
  }

  // type?
  private static boolean finalConstVarVoidOrType_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarVoidOrType_0_2")) return false;
    type(builder_, level_ + 1);
    return true;
  }

  // 'const' type?
  private static boolean finalConstVarVoidOrType_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarVoidOrType_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONST);
    result_ = result_ && finalConstVarVoidOrType_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // type?
  private static boolean finalConstVarVoidOrType_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarVoidOrType_1_1")) return false;
    type(builder_, level_ + 1);
    return true;
  }

  // 'late'? 'var'
  private static boolean finalConstVarVoidOrType_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarVoidOrType_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = finalConstVarVoidOrType_2_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, VAR);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'late'?
  private static boolean finalConstVarVoidOrType_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarVoidOrType_2_0")) return false;
    consumeToken(builder_, LATE);
    return true;
  }

  // 'late'? 'void'
  private static boolean finalConstVarVoidOrType_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarVoidOrType_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = finalConstVarVoidOrType_3_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, VOID);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'late'?
  private static boolean finalConstVarVoidOrType_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarVoidOrType_3_0")) return false;
    consumeToken(builder_, LATE);
    return true;
  }

  // 'late' type
  private static boolean finalConstVarVoidOrType_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalConstVarVoidOrType_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LATE);
    result_ = result_ && type(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'final' | 'const'
  static boolean finalOrConst(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finalOrConst")) return false;
    if (!nextTokenIs(builder_, "", CONST, FINAL)) return false;
    boolean result_;
    result_ = consumeToken(builder_, FINAL);
    if (!result_) result_ = consumeToken(builder_, CONST);
    return result_;
  }

  /* ********************************************************** */
  // 'finally' block
  public static boolean finallyPart(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "finallyPart")) return false;
    if (!nextTokenIs(builder_, FINALLY)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FINALLY_PART, null);
    result_ = consumeToken(builder_, FINALLY);
    pinned_ = result_; // pin = 1
    result_ = result_ && block(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // 'await'? 'for' '(' forLoopParts ')' element
  public static boolean forElement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forElement")) return false;
    if (!nextTokenIs(builder_, "<for element>", AWAIT, FOR)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FOR_ELEMENT, "<for element>");
    result_ = forElement_0(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 1, FOR, LPAREN);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, forLoopParts(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, RPAREN)) && result_;
    result_ = pinned_ && element(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // 'await'?
  private static boolean forElement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forElement_0")) return false;
    consumeToken(builder_, AWAIT);
    return true;
  }

  /* ********************************************************** */
  // (varAccessDeclaration | componentName) 'in' expression |
  //               metadata* ( 'final' | 'var' ) outerPattern 'in' expression
  public static boolean forInPart(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forInPart")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FOR_IN_PART, "<for in part>");
    result_ = forInPart_0(builder_, level_ + 1);
    if (!result_) result_ = forInPart_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (varAccessDeclaration | componentName) 'in' expression
  private static boolean forInPart_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forInPart_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = forInPart_0_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, IN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // varAccessDeclaration | componentName
  private static boolean forInPart_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forInPart_0_0")) return false;
    boolean result_;
    result_ = varAccessDeclaration(builder_, level_ + 1);
    if (!result_) result_ = componentName(builder_, level_ + 1);
    return result_;
  }

  // metadata* ( 'final' | 'var' ) outerPattern 'in' expression
  private static boolean forInPart_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forInPart_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = forInPart_1_0(builder_, level_ + 1);
    result_ = result_ && forInPart_1_1(builder_, level_ + 1);
    result_ = result_ && outerPattern(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, IN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean forInPart_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forInPart_1_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "forInPart_1_0", pos_)) break;
    }
    return true;
  }

  // 'final' | 'var'
  private static boolean forInPart_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forInPart_1_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, FINAL);
    if (!result_) result_ = consumeToken(builder_, VAR);
    return result_;
  }

  /* ********************************************************** */
  // forInPart
  //               | varDeclarationList (';' expression? (';' expressionList?)?)?
  //               | patternVariableDeclaration (';' expression? (';' expressionList?)?)?
  //               | expressionList? (';' expression? (';' expressionList?)?)?
  public static boolean forLoopParts(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FOR_LOOP_PARTS, "<for loop parts>");
    result_ = forInPart(builder_, level_ + 1);
    if (!result_) result_ = forLoopParts_1(builder_, level_ + 1);
    if (!result_) result_ = forLoopParts_2(builder_, level_ + 1);
    if (!result_) result_ = forLoopParts_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, DartParser::for_loops_parts_recover);
    return result_;
  }

  // varDeclarationList (';' expression? (';' expressionList?)?)?
  private static boolean forLoopParts_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = varDeclarationList(builder_, level_ + 1);
    result_ = result_ && forLoopParts_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (';' expression? (';' expressionList?)?)?
  private static boolean forLoopParts_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_1_1")) return false;
    forLoopParts_1_1_0(builder_, level_ + 1);
    return true;
  }

  // ';' expression? (';' expressionList?)?
  private static boolean forLoopParts_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_1_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEMICOLON);
    result_ = result_ && forLoopParts_1_1_0_1(builder_, level_ + 1);
    result_ = result_ && forLoopParts_1_1_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // expression?
  private static boolean forLoopParts_1_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_1_1_0_1")) return false;
    expression(builder_, level_ + 1);
    return true;
  }

  // (';' expressionList?)?
  private static boolean forLoopParts_1_1_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_1_1_0_2")) return false;
    forLoopParts_1_1_0_2_0(builder_, level_ + 1);
    return true;
  }

  // ';' expressionList?
  private static boolean forLoopParts_1_1_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_1_1_0_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEMICOLON);
    result_ = result_ && forLoopParts_1_1_0_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // expressionList?
  private static boolean forLoopParts_1_1_0_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_1_1_0_2_0_1")) return false;
    expressionList(builder_, level_ + 1);
    return true;
  }

  // patternVariableDeclaration (';' expression? (';' expressionList?)?)?
  private static boolean forLoopParts_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = patternVariableDeclaration(builder_, level_ + 1);
    result_ = result_ && forLoopParts_2_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (';' expression? (';' expressionList?)?)?
  private static boolean forLoopParts_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_2_1")) return false;
    forLoopParts_2_1_0(builder_, level_ + 1);
    return true;
  }

  // ';' expression? (';' expressionList?)?
  private static boolean forLoopParts_2_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_2_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEMICOLON);
    result_ = result_ && forLoopParts_2_1_0_1(builder_, level_ + 1);
    result_ = result_ && forLoopParts_2_1_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // expression?
  private static boolean forLoopParts_2_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_2_1_0_1")) return false;
    expression(builder_, level_ + 1);
    return true;
  }

  // (';' expressionList?)?
  private static boolean forLoopParts_2_1_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_2_1_0_2")) return false;
    forLoopParts_2_1_0_2_0(builder_, level_ + 1);
    return true;
  }

  // ';' expressionList?
  private static boolean forLoopParts_2_1_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_2_1_0_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEMICOLON);
    result_ = result_ && forLoopParts_2_1_0_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // expressionList?
  private static boolean forLoopParts_2_1_0_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_2_1_0_2_0_1")) return false;
    expressionList(builder_, level_ + 1);
    return true;
  }

  // expressionList? (';' expression? (';' expressionList?)?)?
  private static boolean forLoopParts_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = forLoopParts_3_0(builder_, level_ + 1);
    result_ = result_ && forLoopParts_3_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // expressionList?
  private static boolean forLoopParts_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_3_0")) return false;
    expressionList(builder_, level_ + 1);
    return true;
  }

  // (';' expression? (';' expressionList?)?)?
  private static boolean forLoopParts_3_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_3_1")) return false;
    forLoopParts_3_1_0(builder_, level_ + 1);
    return true;
  }

  // ';' expression? (';' expressionList?)?
  private static boolean forLoopParts_3_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_3_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEMICOLON);
    result_ = result_ && forLoopParts_3_1_0_1(builder_, level_ + 1);
    result_ = result_ && forLoopParts_3_1_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // expression?
  private static boolean forLoopParts_3_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_3_1_0_1")) return false;
    expression(builder_, level_ + 1);
    return true;
  }

  // (';' expressionList?)?
  private static boolean forLoopParts_3_1_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_3_1_0_2")) return false;
    forLoopParts_3_1_0_2_0(builder_, level_ + 1);
    return true;
  }

  // ';' expressionList?
  private static boolean forLoopParts_3_1_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_3_1_0_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SEMICOLON);
    result_ = result_ && forLoopParts_3_1_0_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // expressionList?
  private static boolean forLoopParts_3_1_0_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopParts_3_1_0_2_0_1")) return false;
    expressionList(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // '(' forLoopParts ')'
  public static boolean forLoopPartsInBraces(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forLoopPartsInBraces")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && forLoopParts(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, FOR_LOOP_PARTS_IN_BRACES, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'await'? 'for' forLoopPartsInBraces statement
  public static boolean forStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forStatement")) return false;
    if (!nextTokenIs(builder_, "<for statement>", AWAIT, FOR)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FOR_STATEMENT, "<for statement>");
    result_ = forStatement_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, FOR);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, forLoopPartsInBraces(builder_, level_ + 1));
    result_ = pinned_ && statement(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // 'await'?
  private static boolean forStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "forStatement_0")) return false;
    consumeToken(builder_, AWAIT);
    return true;
  }

  /* ********************************************************** */
  // !')'
  static boolean for_loops_parts_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "for_loops_parts_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !consumeToken(builder_, RPAREN);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '(' ')' |
  //                         '(' normalFormalParameters (',' optionalFormalParameters)? ','? ')' |
  //                         '(' optionalFormalParameters ')'
  public static boolean formalParameterList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "formalParameterList")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parseTokens(builder_, 0, LPAREN, RPAREN);
    if (!result_) result_ = formalParameterList_1(builder_, level_ + 1);
    if (!result_) result_ = formalParameterList_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, FORMAL_PARAMETER_LIST, result_);
    return result_;
  }

  // '(' normalFormalParameters (',' optionalFormalParameters)? ','? ')'
  private static boolean formalParameterList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "formalParameterList_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && normalFormalParameters(builder_, level_ + 1);
    result_ = result_ && formalParameterList_1_2(builder_, level_ + 1);
    result_ = result_ && formalParameterList_1_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (',' optionalFormalParameters)?
  private static boolean formalParameterList_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "formalParameterList_1_2")) return false;
    formalParameterList_1_2_0(builder_, level_ + 1);
    return true;
  }

  // ',' optionalFormalParameters
  private static boolean formalParameterList_1_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "formalParameterList_1_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && optionalFormalParameters(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean formalParameterList_1_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "formalParameterList_1_3")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  // '(' optionalFormalParameters ')'
  private static boolean formalParameterList_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "formalParameterList_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && optionalFormalParameters(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // arrowBodyWithSemi | <<blockBodyWrapper>>
  public static boolean functionBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FUNCTION_BODY, "<function body>");
    result_ = arrowBodyWithSemi(builder_, level_ + 1);
    if (!result_) result_ = blockBodyWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // 'native' functionBody
  //                                | functionNative
  //                                | functionBody
  static boolean functionBodyOrNative(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionBodyOrNative")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionBodyOrNative_0(builder_, level_ + 1);
    if (!result_) result_ = functionNative(builder_, level_ + 1);
    if (!result_) result_ = functionBody(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'native' functionBody
  private static boolean functionBodyOrNative_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionBodyOrNative_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NATIVE);
    result_ = result_ && functionBody(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // metadata* 'augment'? (returnType componentName typeParameters? formalParameterList | componentName typeParameters? formalParameterList) functionBody
  public static boolean functionDeclarationWithBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FUNCTION_DECLARATION_WITH_BODY, "<function declaration with body>");
    result_ = functionDeclarationWithBody_0(builder_, level_ + 1);
    result_ = result_ && functionDeclarationWithBody_1(builder_, level_ + 1);
    result_ = result_ && functionDeclarationWithBody_2(builder_, level_ + 1);
    result_ = result_ && functionBody(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata*
  private static boolean functionDeclarationWithBody_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBody_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "functionDeclarationWithBody_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean functionDeclarationWithBody_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBody_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // returnType componentName typeParameters? formalParameterList | componentName typeParameters? formalParameterList
  private static boolean functionDeclarationWithBody_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBody_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionDeclarationWithBody_2_0(builder_, level_ + 1);
    if (!result_) result_ = functionDeclarationWithBody_2_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // returnType componentName typeParameters? formalParameterList
  private static boolean functionDeclarationWithBody_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBody_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = returnType(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && functionDeclarationWithBody_2_0_2(builder_, level_ + 1);
    result_ = result_ && formalParameterList(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // typeParameters?
  private static boolean functionDeclarationWithBody_2_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBody_2_0_2")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // componentName typeParameters? formalParameterList
  private static boolean functionDeclarationWithBody_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBody_2_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = componentName(builder_, level_ + 1);
    result_ = result_ && functionDeclarationWithBody_2_1_1(builder_, level_ + 1);
    result_ = result_ && formalParameterList(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // typeParameters?
  private static boolean functionDeclarationWithBody_2_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBody_2_1_1")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // metadata* 'augment'? 'external'? (returnType componentName typeParameters? formalParameterList | componentName typeParameters? formalParameterList) ( ';' | functionBodyOrNative)
  public static boolean functionDeclarationWithBodyOrNative(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBodyOrNative")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FUNCTION_DECLARATION_WITH_BODY_OR_NATIVE, "<function declaration with body or native>");
    result_ = functionDeclarationWithBodyOrNative_0(builder_, level_ + 1);
    result_ = result_ && functionDeclarationWithBodyOrNative_1(builder_, level_ + 1);
    result_ = result_ && functionDeclarationWithBodyOrNative_2(builder_, level_ + 1);
    result_ = result_ && functionDeclarationWithBodyOrNative_3(builder_, level_ + 1);
    result_ = result_ && functionDeclarationWithBodyOrNative_4(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata*
  private static boolean functionDeclarationWithBodyOrNative_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBodyOrNative_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "functionDeclarationWithBodyOrNative_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean functionDeclarationWithBodyOrNative_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBodyOrNative_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // 'external'?
  private static boolean functionDeclarationWithBodyOrNative_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBodyOrNative_2")) return false;
    consumeToken(builder_, EXTERNAL);
    return true;
  }

  // returnType componentName typeParameters? formalParameterList | componentName typeParameters? formalParameterList
  private static boolean functionDeclarationWithBodyOrNative_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBodyOrNative_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionDeclarationWithBodyOrNative_3_0(builder_, level_ + 1);
    if (!result_) result_ = functionDeclarationWithBodyOrNative_3_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // returnType componentName typeParameters? formalParameterList
  private static boolean functionDeclarationWithBodyOrNative_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBodyOrNative_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = returnType(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && functionDeclarationWithBodyOrNative_3_0_2(builder_, level_ + 1);
    result_ = result_ && formalParameterList(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // typeParameters?
  private static boolean functionDeclarationWithBodyOrNative_3_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBodyOrNative_3_0_2")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // componentName typeParameters? formalParameterList
  private static boolean functionDeclarationWithBodyOrNative_3_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBodyOrNative_3_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = componentName(builder_, level_ + 1);
    result_ = result_ && functionDeclarationWithBodyOrNative_3_1_1(builder_, level_ + 1);
    result_ = result_ && formalParameterList(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // typeParameters?
  private static boolean functionDeclarationWithBodyOrNative_3_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBodyOrNative_3_1_1")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // ';' | functionBodyOrNative
  private static boolean functionDeclarationWithBodyOrNative_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionDeclarationWithBodyOrNative_4")) return false;
    boolean result_;
    result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = functionBodyOrNative(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // typeParameters? formalParameterList functionExpressionBody
  public static boolean functionExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionExpression")) return false;
    if (!nextTokenIs(builder_, "<function expression>", LPAREN, LT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FUNCTION_EXPRESSION, "<function expression>");
    result_ = functionExpression_0(builder_, level_ + 1);
    result_ = result_ && formalParameterList(builder_, level_ + 1);
    result_ = result_ && functionExpressionBody(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // typeParameters?
  private static boolean functionExpression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionExpression_0")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // <<arrowBodyWrapper>> | <<blockBodyWrapper>>
  public static boolean functionExpressionBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionExpressionBody")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FUNCTION_EXPRESSION_BODY, "<function expression body>");
    result_ = arrowBodyWrapper(builder_, level_ + 1);
    if (!result_) result_ = blockBodyWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // !functionTypeWrapper (metadata*                         componentName typeParameters? formalParameterList |
  //                                                   metadata* 'covariant'? returnType componentName typeParameters? formalParameterList |
  //                                                   metadata* 'covariant'             componentName typeParameters? formalParameterList)
  //                             '?'?
  public static boolean functionFormalParameter(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FUNCTION_FORMAL_PARAMETER, "<function formal parameter>");
    result_ = functionFormalParameter_0(builder_, level_ + 1);
    result_ = result_ && functionFormalParameter_1(builder_, level_ + 1);
    result_ = result_ && functionFormalParameter_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // !functionTypeWrapper
  private static boolean functionFormalParameter_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !functionTypeWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata*                         componentName typeParameters? formalParameterList |
  //                                                   metadata* 'covariant'? returnType componentName typeParameters? formalParameterList |
  //                                                   metadata* 'covariant'             componentName typeParameters? formalParameterList
  private static boolean functionFormalParameter_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionFormalParameter_1_0(builder_, level_ + 1);
    if (!result_) result_ = functionFormalParameter_1_1(builder_, level_ + 1);
    if (!result_) result_ = functionFormalParameter_1_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*                         componentName typeParameters? formalParameterList
  private static boolean functionFormalParameter_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionFormalParameter_1_0_0(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && functionFormalParameter_1_0_2(builder_, level_ + 1);
    result_ = result_ && formalParameterList(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean functionFormalParameter_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter_1_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "functionFormalParameter_1_0_0", pos_)) break;
    }
    return true;
  }

  // typeParameters?
  private static boolean functionFormalParameter_1_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter_1_0_2")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // metadata* 'covariant'? returnType componentName typeParameters? formalParameterList
  private static boolean functionFormalParameter_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter_1_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionFormalParameter_1_1_0(builder_, level_ + 1);
    result_ = result_ && functionFormalParameter_1_1_1(builder_, level_ + 1);
    result_ = result_ && returnType(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && functionFormalParameter_1_1_4(builder_, level_ + 1);
    result_ = result_ && formalParameterList(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean functionFormalParameter_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter_1_1_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "functionFormalParameter_1_1_0", pos_)) break;
    }
    return true;
  }

  // 'covariant'?
  private static boolean functionFormalParameter_1_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter_1_1_1")) return false;
    consumeToken(builder_, COVARIANT);
    return true;
  }

  // typeParameters?
  private static boolean functionFormalParameter_1_1_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter_1_1_4")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // metadata* 'covariant'             componentName typeParameters? formalParameterList
  private static boolean functionFormalParameter_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter_1_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionFormalParameter_1_2_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COVARIANT);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && functionFormalParameter_1_2_3(builder_, level_ + 1);
    result_ = result_ && formalParameterList(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean functionFormalParameter_1_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter_1_2_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "functionFormalParameter_1_2_0", pos_)) break;
    }
    return true;
  }

  // typeParameters?
  private static boolean functionFormalParameter_1_2_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter_1_2_3")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // '?'?
  private static boolean functionFormalParameter_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionFormalParameter_2")) return false;
    consumeToken(builder_, QUEST);
    return true;
  }

  /* ********************************************************** */
  // 'native' (stringLiteralExpression ';' | ';' | stringLiteralExpression functionBody)
  static boolean functionNative(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionNative")) return false;
    if (!nextTokenIs(builder_, NATIVE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NATIVE);
    result_ = result_ && functionNative_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // stringLiteralExpression ';' | ';' | stringLiteralExpression functionBody
  private static boolean functionNative_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionNative_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionNative_1_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = functionNative_1_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // stringLiteralExpression ';'
  private static boolean functionNative_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionNative_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = stringLiteralExpression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, SEMICOLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // stringLiteralExpression functionBody
  private static boolean functionNative_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionNative_1_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = stringLiteralExpression(builder_, level_ + 1);
    result_ = result_ && functionBody(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // returnType componentName | componentName
  static boolean functionPrefix(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionPrefix")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionPrefix_0(builder_, level_ + 1);
    if (!result_) result_ = componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // returnType componentName
  private static boolean functionPrefix_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionPrefix_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = returnType(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // metadata* 'augment'? 'typedef' functionPrefix typeParameters? formalParameterList ';'? |
  //                       metadata* 'augment'? 'typedef' componentName typeParameters? '=' type ';'?
  public static boolean functionTypeAlias(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeAlias")) return false;
    if (!nextTokenIs(builder_, "<function type alias>", AT, AUGMENT, TYPEDEF)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FUNCTION_TYPE_ALIAS, "<function type alias>");
    result_ = functionTypeAlias_0(builder_, level_ + 1);
    if (!result_) result_ = functionTypeAlias_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata* 'augment'? 'typedef' functionPrefix typeParameters? formalParameterList ';'?
  private static boolean functionTypeAlias_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeAlias_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionTypeAlias_0_0(builder_, level_ + 1);
    result_ = result_ && functionTypeAlias_0_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, TYPEDEF);
    result_ = result_ && functionPrefix(builder_, level_ + 1);
    result_ = result_ && functionTypeAlias_0_4(builder_, level_ + 1);
    result_ = result_ && formalParameterList(builder_, level_ + 1);
    result_ = result_ && functionTypeAlias_0_6(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean functionTypeAlias_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeAlias_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "functionTypeAlias_0_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean functionTypeAlias_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeAlias_0_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // typeParameters?
  private static boolean functionTypeAlias_0_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeAlias_0_4")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // ';'?
  private static boolean functionTypeAlias_0_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeAlias_0_6")) return false;
    consumeToken(builder_, SEMICOLON);
    return true;
  }

  // metadata* 'augment'? 'typedef' componentName typeParameters? '=' type ';'?
  private static boolean functionTypeAlias_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeAlias_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionTypeAlias_1_0(builder_, level_ + 1);
    result_ = result_ && functionTypeAlias_1_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, TYPEDEF);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && functionTypeAlias_1_4(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EQ);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && functionTypeAlias_1_7(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean functionTypeAlias_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeAlias_1_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "functionTypeAlias_1_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean functionTypeAlias_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeAlias_1_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // typeParameters?
  private static boolean functionTypeAlias_1_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeAlias_1_4")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // ';'?
  private static boolean functionTypeAlias_1_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeAlias_1_7")) return false;
    consumeToken(builder_, SEMICOLON);
    return true;
  }

  /* ********************************************************** */
  // (voidTypeFunctionType | untypedFunctionType | simpleType) typedFunctionType+ | untypedFunctionType
  static boolean functionTypeWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionTypeWrapper_0(builder_, level_ + 1);
    if (!result_) result_ = untypedFunctionType(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (voidTypeFunctionType | untypedFunctionType | simpleType) typedFunctionType+
  private static boolean functionTypeWrapper_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeWrapper_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = functionTypeWrapper_0_0(builder_, level_ + 1);
    result_ = result_ && functionTypeWrapper_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // voidTypeFunctionType | untypedFunctionType | simpleType
  private static boolean functionTypeWrapper_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeWrapper_0_0")) return false;
    boolean result_;
    result_ = voidTypeFunctionType(builder_, level_ + 1);
    if (!result_) result_ = untypedFunctionType(builder_, level_ + 1);
    if (!result_) result_ = simpleType(builder_, level_ + 1);
    return result_;
  }

  // typedFunctionType+
  private static boolean functionTypeWrapper_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionTypeWrapper_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = typedFunctionType(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!typedFunctionType(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "functionTypeWrapper_0_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // getterDeclarationWithReturnType | getterDeclarationWithoutReturnType
  public static boolean getterDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclaration")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, GETTER_DECLARATION, "<getter declaration>");
    result_ = getterDeclarationWithReturnType(builder_, level_ + 1);
    if (!result_) result_ = getterDeclarationWithoutReturnType(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // metadata* 'augment'? ('external' | 'static')* returnType 'get' componentName formalParameterList? (';' | functionBodyOrNative)
  static boolean getterDeclarationWithReturnType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithReturnType")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = getterDeclarationWithReturnType_0(builder_, level_ + 1);
    result_ = result_ && getterDeclarationWithReturnType_1(builder_, level_ + 1);
    result_ = result_ && getterDeclarationWithReturnType_2(builder_, level_ + 1);
    result_ = result_ && returnType(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, GET);
    result_ = result_ && componentName(builder_, level_ + 1);
    pinned_ = result_; // pin = 6
    result_ = result_ && report_error_(builder_, getterDeclarationWithReturnType_6(builder_, level_ + 1));
    result_ = pinned_ && getterDeclarationWithReturnType_7(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean getterDeclarationWithReturnType_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithReturnType_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "getterDeclarationWithReturnType_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean getterDeclarationWithReturnType_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithReturnType_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // ('external' | 'static')*
  private static boolean getterDeclarationWithReturnType_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithReturnType_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!getterDeclarationWithReturnType_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "getterDeclarationWithReturnType_2", pos_)) break;
    }
    return true;
  }

  // 'external' | 'static'
  private static boolean getterDeclarationWithReturnType_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithReturnType_2_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, EXTERNAL);
    if (!result_) result_ = consumeToken(builder_, STATIC);
    return result_;
  }

  // formalParameterList?
  private static boolean getterDeclarationWithReturnType_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithReturnType_6")) return false;
    formalParameterList(builder_, level_ + 1);
    return true;
  }

  // ';' | functionBodyOrNative
  private static boolean getterDeclarationWithReturnType_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithReturnType_7")) return false;
    boolean result_;
    result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = functionBodyOrNative(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // metadata* 'augment'? ('external' | 'static')*            'get' componentName formalParameterList? (';' | functionBodyOrNative)
  static boolean getterDeclarationWithoutReturnType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithoutReturnType")) return false;
    if (!nextTokenIs(builder_, "", AT, AUGMENT,
      EXTERNAL, GET, STATIC)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = getterDeclarationWithoutReturnType_0(builder_, level_ + 1);
    result_ = result_ && getterDeclarationWithoutReturnType_1(builder_, level_ + 1);
    result_ = result_ && getterDeclarationWithoutReturnType_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, GET);
    result_ = result_ && componentName(builder_, level_ + 1);
    pinned_ = result_; // pin = 5
    result_ = result_ && report_error_(builder_, getterDeclarationWithoutReturnType_5(builder_, level_ + 1));
    result_ = pinned_ && getterDeclarationWithoutReturnType_6(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean getterDeclarationWithoutReturnType_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithoutReturnType_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "getterDeclarationWithoutReturnType_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean getterDeclarationWithoutReturnType_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithoutReturnType_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // ('external' | 'static')*
  private static boolean getterDeclarationWithoutReturnType_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithoutReturnType_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!getterDeclarationWithoutReturnType_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "getterDeclarationWithoutReturnType_2", pos_)) break;
    }
    return true;
  }

  // 'external' | 'static'
  private static boolean getterDeclarationWithoutReturnType_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithoutReturnType_2_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, EXTERNAL);
    if (!result_) result_ = consumeToken(builder_, STATIC);
    return result_;
  }

  // formalParameterList?
  private static boolean getterDeclarationWithoutReturnType_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithoutReturnType_5")) return false;
    formalParameterList(builder_, level_ + 1);
    return true;
  }

  // ';' | functionBodyOrNative
  private static boolean getterDeclarationWithoutReturnType_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterDeclarationWithoutReturnType_6")) return false;
    boolean result_;
    result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = functionBodyOrNative(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // getterDeclaration | setterDeclaration
  static boolean getterOrSetterDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "getterOrSetterDeclaration")) return false;
    boolean result_;
    result_ = getterDeclaration(builder_, level_ + 1);
    if (!result_) result_ = setterDeclaration(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // constantPattern 'when' expression | // to capture "case a when b:..."
  //                            pattern ('when' expression)?
  static boolean guardedPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "guardedPattern")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = guardedPattern_0(builder_, level_ + 1);
    if (!result_) result_ = guardedPattern_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // constantPattern 'when' expression
  private static boolean guardedPattern_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "guardedPattern_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = constantPattern(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, WHEN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // pattern ('when' expression)?
  private static boolean guardedPattern_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "guardedPattern_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = pattern(builder_, level_ + 1);
    result_ = result_ && guardedPattern_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ('when' expression)?
  private static boolean guardedPattern_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "guardedPattern_1_1")) return false;
    guardedPattern_1_1_0(builder_, level_ + 1);
    return true;
  }

  // 'when' expression
  private static boolean guardedPattern_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "guardedPattern_1_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, WHEN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'hide' libraryReferenceList
  public static boolean hideCombinator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hideCombinator")) return false;
    if (!nextTokenIs(builder_, HIDE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, HIDE_COMBINATOR, null);
    result_ = consumeToken(builder_, HIDE);
    pinned_ = result_; // pin = 1
    result_ = result_ && libraryReferenceList(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean id(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "id")) return false;
    if (!nextTokenIs(builder_, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, IDENTIFIER);
    exit_section_(builder_, marker_, ID, result_);
    return result_;
  }

  /* ********************************************************** */
  // referenceExpression
  public static boolean identifierPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "identifierPattern")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, IDENTIFIER_PATTERN, "<identifier pattern>");
    result_ = referenceExpression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // 'if' '(' ifExpressionWithRecoverUntilParen ')' element ('else' element)?
  public static boolean ifElement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifElement")) return false;
    if (!nextTokenIs(builder_, IF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, IF_ELEMENT, null);
    result_ = consumeTokens(builder_, 1, IF, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, ifExpressionWithRecoverUntilParen(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, RPAREN)) && result_;
    result_ = pinned_ && report_error_(builder_, element(builder_, level_ + 1)) && result_;
    result_ = pinned_ && ifElement_5(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // ('else' element)?
  private static boolean ifElement_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifElement_5")) return false;
    ifElement_5_0(builder_, level_ + 1);
    return true;
  }

  // 'else' element
  private static boolean ifElement_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifElement_5_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ELSE);
    result_ = result_ && element(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // expression ('case' guardedPattern)?
  static boolean ifExpressionWithRecoverUntilParen(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifExpressionWithRecoverUntilParen")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && ifExpressionWithRecoverUntilParen_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, DartParser::not_paren_recover);
    return result_;
  }

  // ('case' guardedPattern)?
  private static boolean ifExpressionWithRecoverUntilParen_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifExpressionWithRecoverUntilParen_1")) return false;
    ifExpressionWithRecoverUntilParen_1_0(builder_, level_ + 1);
    return true;
  }

  // 'case' guardedPattern
  private static boolean ifExpressionWithRecoverUntilParen_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifExpressionWithRecoverUntilParen_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CASE);
    result_ = result_ && guardedPattern(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '??' logicOrExpressionWrapper
  public static boolean ifNullExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifNullExpression")) return false;
    if (!nextTokenIs(builder_, QUEST_QUEST)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, IF_NULL_EXPRESSION, null);
    result_ = consumeToken(builder_, QUEST_QUEST);
    result_ = result_ && logicOrExpressionWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // logicOrExpressionWrapper ifNullExpression*
  static boolean ifNullExpressionWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifNullExpressionWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = logicOrExpressionWrapper(builder_, level_ + 1);
    result_ = result_ && ifNullExpressionWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ifNullExpression*
  private static boolean ifNullExpressionWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifNullExpressionWrapper_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!ifNullExpression(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ifNullExpressionWrapper_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // 'if' '(' ifExpressionWithRecoverUntilParen ')' statement ('else' statement)?
  public static boolean ifStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifStatement")) return false;
    if (!nextTokenIs(builder_, IF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, IF_STATEMENT, null);
    result_ = consumeTokens(builder_, 1, IF, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, ifExpressionWithRecoverUntilParen(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, RPAREN)) && result_;
    result_ = pinned_ && report_error_(builder_, statement(builder_, level_ + 1)) && result_;
    result_ = pinned_ && ifStatement_5(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // ('else' statement)?
  private static boolean ifStatement_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifStatement_5")) return false;
    ifStatement_5_0(builder_, level_ + 1);
    return true;
  }

  // 'else' statement
  private static boolean ifStatement_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifStatement_5_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ELSE);
    result_ = result_ && statement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'if' '(' dottedName ('==' stringLiteralExpression)? ')' stringLiteralExpression
  static boolean importConfig(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "importConfig")) return false;
    if (!nextTokenIs(builder_, IF)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, IF, LPAREN);
    result_ = result_ && dottedName(builder_, level_ + 1);
    result_ = result_ && importConfig_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    result_ = result_ && stringLiteralExpression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ('==' stringLiteralExpression)?
  private static boolean importConfig_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "importConfig_3")) return false;
    importConfig_3_0(builder_, level_ + 1);
    return true;
  }

  // '==' stringLiteralExpression
  private static boolean importConfig_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "importConfig_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EQ_EQ);
    result_ = result_ && stringLiteralExpression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // metadata* 'import' 'augment'? uriElement importConfig* ('deferred'? 'as' componentName )? combinator* ';'
  public static boolean importStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "importStatement")) return false;
    if (!nextTokenIs(builder_, "<import statement>", AT, IMPORT)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, IMPORT_STATEMENT, "<import statement>");
    result_ = importStatement_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, IMPORT);
    result_ = result_ && importStatement_2(builder_, level_ + 1);
    result_ = result_ && uriElement(builder_, level_ + 1);
    pinned_ = result_; // pin = 4
    result_ = result_ && report_error_(builder_, importStatement_4(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, importStatement_5(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, importStatement_6(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, SEMICOLON) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean importStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "importStatement_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "importStatement_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean importStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "importStatement_2")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // importConfig*
  private static boolean importStatement_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "importStatement_4")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!importConfig(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "importStatement_4", pos_)) break;
    }
    return true;
  }

  // ('deferred'? 'as' componentName )?
  private static boolean importStatement_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "importStatement_5")) return false;
    importStatement_5_0(builder_, level_ + 1);
    return true;
  }

  // 'deferred'? 'as' componentName
  private static boolean importStatement_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "importStatement_5_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = importStatement_5_0_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, AS);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'deferred'?
  private static boolean importStatement_5_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "importStatement_5_0_0")) return false;
    consumeToken(builder_, DEFERRED);
    return true;
  }

  // combinator*
  private static boolean importStatement_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "importStatement_6")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!combinator(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "importStatement_6", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // metadata* ('external' | 'static' | 'final' | 'const' | 'covariant')* type | metadata+
  public static boolean incompleteDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "incompleteDeclaration")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, INCOMPLETE_DECLARATION, "<incomplete declaration>");
    result_ = incompleteDeclaration_0(builder_, level_ + 1);
    if (!result_) result_ = incompleteDeclaration_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata* ('external' | 'static' | 'final' | 'const' | 'covariant')* type
  private static boolean incompleteDeclaration_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "incompleteDeclaration_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = incompleteDeclaration_0_0(builder_, level_ + 1);
    result_ = result_ && incompleteDeclaration_0_1(builder_, level_ + 1);
    result_ = result_ && type(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean incompleteDeclaration_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "incompleteDeclaration_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "incompleteDeclaration_0_0", pos_)) break;
    }
    return true;
  }

  // ('external' | 'static' | 'final' | 'const' | 'covariant')*
  private static boolean incompleteDeclaration_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "incompleteDeclaration_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!incompleteDeclaration_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "incompleteDeclaration_0_1", pos_)) break;
    }
    return true;
  }

  // 'external' | 'static' | 'final' | 'const' | 'covariant'
  private static boolean incompleteDeclaration_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "incompleteDeclaration_0_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, EXTERNAL);
    if (!result_) result_ = consumeToken(builder_, STATIC);
    if (!result_) result_ = consumeToken(builder_, FINAL);
    if (!result_) result_ = consumeToken(builder_, CONST);
    if (!result_) result_ = consumeToken(builder_, COVARIANT);
    return result_;
  }

  // metadata+
  private static boolean incompleteDeclaration_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "incompleteDeclaration_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = metadata(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "incompleteDeclaration_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '<' + <<nonStrictID>> + '>' <<nonStrictID>>
  static boolean incorrectNormalFormalParameter(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "incorrectNormalFormalParameter")) return false;
    if (!nextTokenIs(builder_, LT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = incorrectNormalFormalParameter_0(builder_, level_ + 1);
    result_ = result_ && incorrectNormalFormalParameter_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, GT);
    result_ = result_ && nonStrictID(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // '<' +
  private static boolean incorrectNormalFormalParameter_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "incorrectNormalFormalParameter_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LT);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, LT)) break;
      if (!empty_element_parsed_guard_(builder_, "incorrectNormalFormalParameter_0", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // <<nonStrictID>> +
  private static boolean incorrectNormalFormalParameter_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "incorrectNormalFormalParameter_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nonStrictID(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!nonStrictID(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "incorrectNormalFormalParameter_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ':' superCallOrFieldInitializer (',' superCallOrFieldInitializer)*
  public static boolean initializers(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "initializers")) return false;
    if (!nextTokenIs(builder_, COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && superCallOrFieldInitializer(builder_, level_ + 1);
    result_ = result_ && initializers_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, INITIALIZERS, result_);
    return result_;
  }

  // (',' superCallOrFieldInitializer)*
  private static boolean initializers_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "initializers_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!initializers_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "initializers_2", pos_)) break;
    }
    return true;
  }

  // ',' superCallOrFieldInitializer
  private static boolean initializers_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "initializers_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && superCallOrFieldInitializer(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'implements' typeList
  public static boolean interfaces(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "interfaces")) return false;
    if (!nextTokenIs(builder_, IMPLEMENTS)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, INTERFACES, null);
    result_ = consumeToken(builder_, IMPLEMENTS);
    pinned_ = result_; // pin = 1
    result_ = result_ && typeList(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // 'is' '!'? type
  public static boolean isExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "isExpression")) return false;
    if (!nextTokenIs(builder_, IS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, IS_EXPRESSION, null);
    result_ = consumeToken(builder_, IS);
    result_ = result_ && isExpression_1(builder_, level_ + 1);
    result_ = result_ && type(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // '!'?
  private static boolean isExpression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "isExpression_1")) return false;
    consumeToken(builder_, NOT);
    return true;
  }

  /* ********************************************************** */
  // componentName ':'
  public static boolean label(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "label")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LABEL, "<label>");
    result_ = componentName(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // <<lazyParseableBlockImpl>>
  static boolean lazyParseableBlock(PsiBuilder builder_, int level_) {
    return lazyParseableBlockImpl(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // << nonStrictID >>
  public static boolean libraryComponentReferenceExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryComponentReferenceExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, LIBRARY_COMPONENT_REFERENCE_EXPRESSION, "<library component reference expression>");
    result_ = nonStrictID(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // <<nonStrictID>> ('.' <<nonStrictID>>)*
  public static boolean libraryId(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryId")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LIBRARY_ID, "<library id>");
    result_ = nonStrictID(builder_, level_ + 1);
    result_ = result_ && libraryId_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ('.' <<nonStrictID>>)*
  private static boolean libraryId_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryId_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!libraryId_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "libraryId_1", pos_)) break;
    }
    return true;
  }

  // '.' <<nonStrictID>>
  private static boolean libraryId_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryId_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && nonStrictID(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // <<nonStrictID>> ('.' <<nonStrictID>>)*
  public static boolean libraryNameElement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryNameElement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LIBRARY_NAME_ELEMENT, "<library name element>");
    result_ = nonStrictID(builder_, level_ + 1);
    result_ = result_ && libraryNameElement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ('.' <<nonStrictID>>)*
  private static boolean libraryNameElement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryNameElement_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!libraryNameElement_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "libraryNameElement_1", pos_)) break;
    }
    return true;
  }

  // '.' <<nonStrictID>>
  private static boolean libraryNameElement_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryNameElement_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && nonStrictID(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // libraryComponentReferenceExpression (',' libraryComponentReferenceExpression)*
  public static boolean libraryReferenceList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryReferenceList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LIBRARY_REFERENCE_LIST, "<library reference list>");
    result_ = libraryComponentReferenceExpression(builder_, level_ + 1);
    result_ = result_ && libraryReferenceList_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (',' libraryComponentReferenceExpression)*
  private static boolean libraryReferenceList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryReferenceList_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!libraryReferenceList_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "libraryReferenceList_1", pos_)) break;
    }
    return true;
  }

  // ',' libraryComponentReferenceExpression
  private static boolean libraryReferenceList_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryReferenceList_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && libraryComponentReferenceExpression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // metadata* ('library' libraryNameElement? | 'augment' 'library' uriElement) ';'
  public static boolean libraryStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryStatement")) return false;
    if (!nextTokenIs(builder_, "<library statement>", AT, AUGMENT, LIBRARY)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LIBRARY_STATEMENT, "<library statement>");
    result_ = libraryStatement_0(builder_, level_ + 1);
    result_ = result_ && libraryStatement_1(builder_, level_ + 1);
    pinned_ = result_; // pin = 2
    result_ = result_ && consumeToken(builder_, SEMICOLON);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean libraryStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryStatement_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "libraryStatement_0", pos_)) break;
    }
    return true;
  }

  // 'library' libraryNameElement? | 'augment' 'library' uriElement
  private static boolean libraryStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryStatement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = libraryStatement_1_0(builder_, level_ + 1);
    if (!result_) result_ = libraryStatement_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'library' libraryNameElement?
  private static boolean libraryStatement_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryStatement_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LIBRARY);
    result_ = result_ && libraryStatement_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // libraryNameElement?
  private static boolean libraryStatement_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryStatement_1_0_1")) return false;
    libraryNameElement(builder_, level_ + 1);
    return true;
  }

  // 'augment' 'library' uriElement
  private static boolean libraryStatement_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "libraryStatement_1_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, AUGMENT, LIBRARY);
    result_ = result_ && uriElement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'const'? typeArguments? '[' elements? ']'
  public static boolean listLiteralExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "listLiteralExpression")) return false;
    if (!nextTokenIs(builder_, "<list literal expression>", CONST, LBRACKET, LT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LIST_LITERAL_EXPRESSION, "<list literal expression>");
    result_ = listLiteralExpression_0(builder_, level_ + 1);
    result_ = result_ && listLiteralExpression_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LBRACKET);
    result_ = result_ && listLiteralExpression_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // 'const'?
  private static boolean listLiteralExpression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "listLiteralExpression_0")) return false;
    consumeToken(builder_, CONST);
    return true;
  }

  // typeArguments?
  private static boolean listLiteralExpression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "listLiteralExpression_1")) return false;
    typeArguments(builder_, level_ + 1);
    return true;
  }

  // elements?
  private static boolean listLiteralExpression_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "listLiteralExpression_3")) return false;
    elements(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // typeArguments? '[' listPatternElements? ']'
  public static boolean listPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "listPattern")) return false;
    if (!nextTokenIs(builder_, "<list pattern>", LBRACKET, LT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LIST_PATTERN, "<list pattern>");
    result_ = listPattern_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LBRACKET);
    result_ = result_ && listPattern_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // typeArguments?
  private static boolean listPattern_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "listPattern_0")) return false;
    typeArguments(builder_, level_ + 1);
    return true;
  }

  // listPatternElements?
  private static boolean listPattern_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "listPattern_2")) return false;
    listPatternElements(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // restPattern | pattern
  public static boolean listPatternElement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "listPatternElement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LIST_PATTERN_ELEMENT, "<list pattern element>");
    result_ = restPattern(builder_, level_ + 1);
    if (!result_) result_ = pattern(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // listPatternElement (',' listPatternElement)* ','?
  static boolean listPatternElements(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "listPatternElements")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = listPatternElement(builder_, level_ + 1);
    result_ = result_ && listPatternElements_1(builder_, level_ + 1);
    result_ = result_ && listPatternElements_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (',' listPatternElement)*
  private static boolean listPatternElements_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "listPatternElements_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!listPatternElements_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "listPatternElements_1", pos_)) break;
    }
    return true;
  }

  // ',' listPatternElement
  private static boolean listPatternElements_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "listPatternElements_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && listPatternElement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean listPatternElements_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "listPatternElements_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // NULL | TRUE | FALSE | NUMBER | HEX_NUMBER | stringLiteralExpression | symbolLiteralExpression |
  //                       <<setOrMapLiteralExpressionWrapper>> | <<listLiteralExpressionWrapper>>
  public static boolean literalExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "literalExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, LITERAL_EXPRESSION, "<literal expression>");
    result_ = consumeToken(builder_, NULL);
    if (!result_) result_ = consumeToken(builder_, TRUE);
    if (!result_) result_ = consumeToken(builder_, FALSE);
    if (!result_) result_ = consumeToken(builder_, NUMBER);
    if (!result_) result_ = consumeToken(builder_, HEX_NUMBER);
    if (!result_) result_ = stringLiteralExpression(builder_, level_ + 1);
    if (!result_) result_ = symbolLiteralExpression(builder_, level_ + 1);
    if (!result_) result_ = setOrMapLiteralExpressionWrapper(builder_, level_ + 1);
    if (!result_) result_ = listLiteralExpressionWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '&&' compareExpressionWrapper
  public static boolean logicAndExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "logicAndExpression")) return false;
    if (!nextTokenIs(builder_, AND_AND)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, LOGIC_AND_EXPRESSION, null);
    result_ = consumeToken(builder_, AND_AND);
    result_ = result_ && compareExpressionWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // compareExpressionWrapper logicAndExpression*
  static boolean logicAndExpressionWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "logicAndExpressionWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = compareExpressionWrapper(builder_, level_ + 1);
    result_ = result_ && logicAndExpressionWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // logicAndExpression*
  private static boolean logicAndExpressionWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "logicAndExpressionWrapper_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!logicAndExpression(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "logicAndExpressionWrapper_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '||' logicAndExpressionWrapper
  public static boolean logicOrExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "logicOrExpression")) return false;
    if (!nextTokenIs(builder_, OR_OR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, LOGIC_OR_EXPRESSION, null);
    result_ = consumeToken(builder_, OR_OR);
    result_ = result_ && logicAndExpressionWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // logicAndExpressionWrapper logicOrExpression*
  static boolean logicOrExpressionWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "logicOrExpressionWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = logicAndExpressionWrapper(builder_, level_ + 1);
    result_ = result_ && logicOrExpressionWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // logicOrExpression*
  private static boolean logicOrExpressionWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "logicOrExpressionWrapper_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!logicOrExpression(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "logicOrExpressionWrapper_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '&&' relationalPatternWrapper
  public static boolean logicalAndPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "logicalAndPattern")) return false;
    if (!nextTokenIs(builder_, AND_AND)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, LOGICAL_AND_PATTERN, null);
    result_ = consumeToken(builder_, AND_AND);
    result_ = result_ && relationalPatternWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // relationalPatternWrapper logicalAndPattern*
  static boolean logicalAndPatternWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "logicalAndPatternWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = relationalPatternWrapper(builder_, level_ + 1);
    result_ = result_ && logicalAndPatternWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // logicalAndPattern*
  private static boolean logicalAndPatternWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "logicalAndPatternWrapper_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!logicalAndPattern(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "logicalAndPatternWrapper_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '||' logicalAndPatternWrapper
  public static boolean logicalOrPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "logicalOrPattern")) return false;
    if (!nextTokenIs(builder_, OR_OR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, LOGICAL_OR_PATTERN, null);
    result_ = consumeToken(builder_, OR_OR);
    result_ = result_ && logicalAndPatternWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // logicalAndPatternWrapper logicalOrPattern*
  static boolean logicalOrPatternWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "logicalOrPatternWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = logicalAndPatternWrapper(builder_, level_ + 1);
    result_ = result_ && logicalOrPatternWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // logicalOrPattern*
  private static boolean logicalOrPatternWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "logicalOrPatternWrapper_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!logicalOrPattern(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "logicalOrPatternWrapper_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // LONG_TEMPLATE_ENTRY_START expression LONG_TEMPLATE_ENTRY_END
  public static boolean longTemplateEntry(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "longTemplateEntry")) return false;
    if (!nextTokenIs(builder_, LONG_TEMPLATE_ENTRY_START)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LONG_TEMPLATE_ENTRY, null);
    result_ = consumeToken(builder_, LONG_TEMPLATE_ENTRY_START);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expression(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, LONG_TEMPLATE_ENTRY_END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // ':' (nullAwareElement | expression)
  public static boolean mapEntry(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapEntry")) return false;
    if (!nextTokenIs(builder_, COLON)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, MAP_ENTRY, null);
    result_ = consumeToken(builder_, COLON);
    pinned_ = result_; // pin = 1
    result_ = result_ && mapEntry_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // nullAwareElement | expression
  private static boolean mapEntry_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapEntry_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nullAwareElement(builder_, level_ + 1);
    if (!result_) result_ = expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // (nullAwareElement | expression) mapEntry?
  static boolean mapEntryOrExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapEntryOrExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = mapEntryOrExpression_0(builder_, level_ + 1);
    result_ = result_ && mapEntryOrExpression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // nullAwareElement | expression
  private static boolean mapEntryOrExpression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapEntryOrExpression_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nullAwareElement(builder_, level_ + 1);
    if (!result_) result_ = expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // mapEntry?
  private static boolean mapEntryOrExpression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapEntryOrExpression_1")) return false;
    mapEntry(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // typeArguments? '{' mapPatternEntries? '}'
  public static boolean mapPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapPattern")) return false;
    if (!nextTokenIs(builder_, "<map pattern>", LBRACE, LT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MAP_PATTERN, "<map pattern>");
    result_ = mapPattern_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LBRACE);
    result_ = result_ && mapPattern_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // typeArguments?
  private static boolean mapPattern_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapPattern_0")) return false;
    typeArguments(builder_, level_ + 1);
    return true;
  }

  // mapPatternEntries?
  private static boolean mapPattern_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapPattern_2")) return false;
    mapPatternEntries(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // mapPatternEntry (',' mapPatternEntry)* ','?
  static boolean mapPatternEntries(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapPatternEntries")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = mapPatternEntry(builder_, level_ + 1);
    result_ = result_ && mapPatternEntries_1(builder_, level_ + 1);
    result_ = result_ && mapPatternEntries_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (',' mapPatternEntry)*
  private static boolean mapPatternEntries_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapPatternEntries_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!mapPatternEntries_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "mapPatternEntries_1", pos_)) break;
    }
    return true;
  }

  // ',' mapPatternEntry
  private static boolean mapPatternEntries_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapPatternEntries_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && mapPatternEntry(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean mapPatternEntries_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapPatternEntries_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // '...' | expression ':' pattern
  public static boolean mapPatternEntry(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapPatternEntry")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MAP_PATTERN_ENTRY, "<map pattern entry>");
    result_ = consumeToken(builder_, DOT_DOT_DOT);
    if (!result_) result_ = mapPatternEntry_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // expression ':' pattern
  private static boolean mapPatternEntry_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mapPatternEntry_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && pattern(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '@' simpleQualifiedReferenceExpression typeArguments? (<<noSpace>> <<argumentsWrapper>>)?
  public static boolean metadata(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "metadata")) return false;
    if (!nextTokenIs(builder_, AT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AT);
    result_ = result_ && simpleQualifiedReferenceExpression(builder_, level_ + 1);
    result_ = result_ && metadata_2(builder_, level_ + 1);
    result_ = result_ && metadata_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, METADATA, result_);
    return result_;
  }

  // typeArguments?
  private static boolean metadata_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "metadata_2")) return false;
    typeArguments(builder_, level_ + 1);
    return true;
  }

  // (<<noSpace>> <<argumentsWrapper>>)?
  private static boolean metadata_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "metadata_3")) return false;
    metadata_3_0(builder_, level_ + 1);
    return true;
  }

  // <<noSpace>> <<argumentsWrapper>>
  private static boolean metadata_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "metadata_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = noSpace(builder_, level_ + 1);
    result_ = result_ && argumentsWrapper(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // metadata* 'augment'? ('external' | 'static' | 'const')* methodDeclarationPrivate initializers? (';' | functionBodyOrNative | redirection)?
  public static boolean methodDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclaration")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, METHOD_DECLARATION, "<method declaration>");
    result_ = methodDeclaration_0(builder_, level_ + 1);
    result_ = result_ && methodDeclaration_1(builder_, level_ + 1);
    result_ = result_ && methodDeclaration_2(builder_, level_ + 1);
    result_ = result_ && methodDeclarationPrivate(builder_, level_ + 1);
    pinned_ = result_; // pin = 4
    result_ = result_ && report_error_(builder_, methodDeclaration_4(builder_, level_ + 1));
    result_ = pinned_ && methodDeclaration_5(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean methodDeclaration_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclaration_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "methodDeclaration_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean methodDeclaration_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclaration_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // ('external' | 'static' | 'const')*
  private static boolean methodDeclaration_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclaration_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!methodDeclaration_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "methodDeclaration_2", pos_)) break;
    }
    return true;
  }

  // 'external' | 'static' | 'const'
  private static boolean methodDeclaration_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclaration_2_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, EXTERNAL);
    if (!result_) result_ = consumeToken(builder_, STATIC);
    if (!result_) result_ = consumeToken(builder_, CONST);
    return result_;
  }

  // initializers?
  private static boolean methodDeclaration_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclaration_4")) return false;
    initializers(builder_, level_ + 1);
    return true;
  }

  // (';' | functionBodyOrNative | redirection)?
  private static boolean methodDeclaration_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclaration_5")) return false;
    methodDeclaration_5_0(builder_, level_ + 1);
    return true;
  }

  // ';' | functionBodyOrNative | redirection
  private static boolean methodDeclaration_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclaration_5_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = functionBodyOrNative(builder_, level_ + 1);
    if (!result_) result_ = redirection(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // returnType <<methodNameWrapper>> typeParameters? formalParameterList | !untypedFunctionType <<methodNameWrapper>> typeParameters? formalParameterList
  static boolean methodDeclarationPrivate(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclarationPrivate")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = methodDeclarationPrivate_0(builder_, level_ + 1);
    if (!result_) result_ = methodDeclarationPrivate_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // returnType <<methodNameWrapper>> typeParameters? formalParameterList
  private static boolean methodDeclarationPrivate_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclarationPrivate_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = returnType(builder_, level_ + 1);
    result_ = result_ && methodNameWrapper(builder_, level_ + 1);
    result_ = result_ && methodDeclarationPrivate_0_2(builder_, level_ + 1);
    result_ = result_ && formalParameterList(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // typeParameters?
  private static boolean methodDeclarationPrivate_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclarationPrivate_0_2")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // !untypedFunctionType <<methodNameWrapper>> typeParameters? formalParameterList
  private static boolean methodDeclarationPrivate_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclarationPrivate_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = methodDeclarationPrivate_1_0(builder_, level_ + 1);
    result_ = result_ && methodNameWrapper(builder_, level_ + 1);
    result_ = result_ && methodDeclarationPrivate_1_2(builder_, level_ + 1);
    result_ = result_ && formalParameterList(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !untypedFunctionType
  private static boolean methodDeclarationPrivate_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclarationPrivate_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !untypedFunctionType(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // typeParameters?
  private static boolean methodDeclarationPrivate_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "methodDeclarationPrivate_1_2")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // '=' type mixins? interfaces? ';'
  public static boolean mixinApplication(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinApplication")) return false;
    if (!nextTokenIs(builder_, EQ)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MIXIN_APPLICATION, null);
    result_ = consumeToken(builder_, EQ);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, type(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, mixinApplication_2(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, mixinApplication_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, SEMICOLON) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // mixins?
  private static boolean mixinApplication_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinApplication_2")) return false;
    mixins(builder_, level_ + 1);
    return true;
  }

  // interfaces?
  private static boolean mixinApplication_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinApplication_3")) return false;
    interfaces(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // 'abstract'? 'base'? 'mixin'
  static boolean mixinClassModifiers(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinClassModifiers")) return false;
    if (!nextTokenIs(builder_, "", ABSTRACT, BASE, MIXIN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = mixinClassModifiers_0(builder_, level_ + 1);
    result_ = result_ && mixinClassModifiers_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, MIXIN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'abstract'?
  private static boolean mixinClassModifiers_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinClassModifiers_0")) return false;
    consumeToken(builder_, ABSTRACT);
    return true;
  }

  // 'base'?
  private static boolean mixinClassModifiers_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinClassModifiers_1")) return false;
    consumeToken(builder_, BASE);
    return true;
  }

  /* ********************************************************** */
  // metadata* 'augment'? mixinModifier? 'mixin' componentName typeParameters? onMixins? interfaces? classBody
  public static boolean mixinDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinDeclaration")) return false;
    if (!nextTokenIs(builder_, "<mixin declaration>", AT, AUGMENT,
      BASE, FINAL, INTERFACE, MIXIN, SEALED)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MIXIN_DECLARATION, "<mixin declaration>");
    result_ = mixinDeclaration_0(builder_, level_ + 1);
    result_ = result_ && mixinDeclaration_1(builder_, level_ + 1);
    result_ = result_ && mixinDeclaration_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, MIXIN);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && mixinDeclaration_5(builder_, level_ + 1);
    result_ = result_ && mixinDeclaration_6(builder_, level_ + 1);
    result_ = result_ && mixinDeclaration_7(builder_, level_ + 1);
    result_ = result_ && classBody(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata*
  private static boolean mixinDeclaration_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinDeclaration_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "mixinDeclaration_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean mixinDeclaration_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinDeclaration_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // mixinModifier?
  private static boolean mixinDeclaration_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinDeclaration_2")) return false;
    mixinModifier(builder_, level_ + 1);
    return true;
  }

  // typeParameters?
  private static boolean mixinDeclaration_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinDeclaration_5")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // onMixins?
  private static boolean mixinDeclaration_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinDeclaration_6")) return false;
    onMixins(builder_, level_ + 1);
    return true;
  }

  // interfaces?
  private static boolean mixinDeclaration_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinDeclaration_7")) return false;
    interfaces(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // 'sealed' | 'base' | 'interface' | 'final'
  static boolean mixinModifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixinModifier")) return false;
    if (!nextTokenIs(builder_, "", BASE, FINAL, INTERFACE, SEALED)) return false;
    boolean result_;
    result_ = consumeToken(builder_, SEALED);
    if (!result_) result_ = consumeToken(builder_, BASE);
    if (!result_) result_ = consumeToken(builder_, INTERFACE);
    if (!result_) result_ = consumeToken(builder_, FINAL);
    return result_;
  }

  /* ********************************************************** */
  // 'with' typeList
  public static boolean mixins(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mixins")) return false;
    if (!nextTokenIs(builder_, WITH)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MIXINS, null);
    result_ = consumeToken(builder_, WITH);
    pinned_ = result_; // pin = 1
    result_ = result_ && typeList(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // multiplicativeOperator prefixExpression
  public static boolean multiplicativeExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiplicativeExpression")) return false;
    if (!nextTokenIs(builder_, "<multiplicative expression>", DIV, INT_DIV, MUL, REM)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, MULTIPLICATIVE_EXPRESSION, "<multiplicative expression>");
    result_ = multiplicativeOperator(builder_, level_ + 1);
    result_ = result_ && prefixExpression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // prefixExpression multiplicativeExpression*
  static boolean multiplicativeExpressionWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiplicativeExpressionWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = prefixExpression(builder_, level_ + 1);
    result_ = result_ && multiplicativeExpressionWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // multiplicativeExpression*
  private static boolean multiplicativeExpressionWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiplicativeExpressionWrapper_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!multiplicativeExpression(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "multiplicativeExpressionWrapper_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '*' | '/' | '%' | '~/'
  public static boolean multiplicativeOperator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiplicativeOperator")) return false;
    if (!nextTokenIs(builder_, "<multiplicative operator>", DIV, INT_DIV, MUL, REM)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MULTIPLICATIVE_OPERATOR, "<multiplicative operator>");
    result_ = consumeToken(builder_, MUL);
    if (!result_) result_ = consumeToken(builder_, DIV);
    if (!result_) result_ = consumeToken(builder_, REM);
    if (!result_) result_ = consumeToken(builder_, INT_DIV);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // parameterNameReferenceExpression ':' expression
  public static boolean namedArgument(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedArgument")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NAMED_ARGUMENT, "<named argument>");
    result_ = parameterNameReferenceExpression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // metadata* 'augment'? ('external' | 'const')* componentName '.' (componentName | 'new') formalParameterList initializers? (';' | functionBodyOrNative | redirection)?
  public static boolean namedConstructorDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedConstructorDeclaration")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NAMED_CONSTRUCTOR_DECLARATION, "<named constructor declaration>");
    result_ = namedConstructorDeclaration_0(builder_, level_ + 1);
    result_ = result_ && namedConstructorDeclaration_1(builder_, level_ + 1);
    result_ = result_ && namedConstructorDeclaration_2(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOT);
    result_ = result_ && namedConstructorDeclaration_5(builder_, level_ + 1);
    result_ = result_ && formalParameterList(builder_, level_ + 1);
    pinned_ = result_; // pin = 7
    result_ = result_ && report_error_(builder_, namedConstructorDeclaration_7(builder_, level_ + 1));
    result_ = pinned_ && namedConstructorDeclaration_8(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean namedConstructorDeclaration_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedConstructorDeclaration_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "namedConstructorDeclaration_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean namedConstructorDeclaration_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedConstructorDeclaration_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // ('external' | 'const')*
  private static boolean namedConstructorDeclaration_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedConstructorDeclaration_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!namedConstructorDeclaration_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "namedConstructorDeclaration_2", pos_)) break;
    }
    return true;
  }

  // 'external' | 'const'
  private static boolean namedConstructorDeclaration_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedConstructorDeclaration_2_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, EXTERNAL);
    if (!result_) result_ = consumeToken(builder_, CONST);
    return result_;
  }

  // componentName | 'new'
  private static boolean namedConstructorDeclaration_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedConstructorDeclaration_5")) return false;
    boolean result_;
    result_ = componentName(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, NEW);
    return result_;
  }

  // initializers?
  private static boolean namedConstructorDeclaration_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedConstructorDeclaration_7")) return false;
    initializers(builder_, level_ + 1);
    return true;
  }

  // (';' | functionBodyOrNative | redirection)?
  private static boolean namedConstructorDeclaration_8(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedConstructorDeclaration_8")) return false;
    namedConstructorDeclaration_8_0(builder_, level_ + 1);
    return true;
  }

  // ';' | functionBodyOrNative | redirection
  private static boolean namedConstructorDeclaration_8_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedConstructorDeclaration_8_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = functionBodyOrNative(builder_, level_ + 1);
    if (!result_) result_ = redirection(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // '{' defaultFormalNamedParameter (',' defaultFormalNamedParameter)* ','? '}'
  static boolean namedFormalParameters(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedFormalParameters")) return false;
    if (!nextTokenIs(builder_, LBRACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACE);
    result_ = result_ && defaultFormalNamedParameter(builder_, level_ + 1);
    result_ = result_ && namedFormalParameters_2(builder_, level_ + 1);
    result_ = result_ && namedFormalParameters_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (',' defaultFormalNamedParameter)*
  private static boolean namedFormalParameters_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedFormalParameters_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!namedFormalParameters_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "namedFormalParameters_2", pos_)) break;
    }
    return true;
  }

  // ',' defaultFormalNamedParameter
  private static boolean namedFormalParameters_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedFormalParameters_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && defaultFormalNamedParameter(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean namedFormalParameters_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedFormalParameters_3")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // '{' 'required'? typedIdentifier (',' 'required'? typedIdentifier)* ','? '}'
  static boolean namedParameterTypes(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedParameterTypes")) return false;
    if (!nextTokenIs(builder_, LBRACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACE);
    result_ = result_ && namedParameterTypes_1(builder_, level_ + 1);
    result_ = result_ && typedIdentifier(builder_, level_ + 1);
    result_ = result_ && namedParameterTypes_3(builder_, level_ + 1);
    result_ = result_ && namedParameterTypes_4(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'required'?
  private static boolean namedParameterTypes_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedParameterTypes_1")) return false;
    consumeToken(builder_, REQUIRED);
    return true;
  }

  // (',' 'required'? typedIdentifier)*
  private static boolean namedParameterTypes_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedParameterTypes_3")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!namedParameterTypes_3_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "namedParameterTypes_3", pos_)) break;
    }
    return true;
  }

  // ',' 'required'? typedIdentifier
  private static boolean namedParameterTypes_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedParameterTypes_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && namedParameterTypes_3_0_1(builder_, level_ + 1);
    result_ = result_ && typedIdentifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'required'?
  private static boolean namedParameterTypes_3_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedParameterTypes_3_0_1")) return false;
    consumeToken(builder_, REQUIRED);
    return true;
  }

  // ','?
  private static boolean namedParameterTypes_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedParameterTypes_4")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // <<nonStrictID>> ':' expression
  public static boolean namedRecordField(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "namedRecordField")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RECORD_FIELD, "<named record field>");
    result_ = nonStrictID(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    pinned_ = result_; // pin = 2
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // shorthandNewExpression | newExpressionWithKeyword | simpleQualifiedReferenceExpression typeArguments '.' (referenceExpression | 'new') <<argumentsWrapper>>
  public static boolean newExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, NEW_EXPRESSION, "<new expression>");
    result_ = shorthandNewExpression(builder_, level_ + 1);
    if (!result_) result_ = newExpressionWithKeyword(builder_, level_ + 1);
    if (!result_) result_ = newExpression_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // simpleQualifiedReferenceExpression typeArguments '.' (referenceExpression | 'new') <<argumentsWrapper>>
  private static boolean newExpression_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newExpression_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = simpleQualifiedReferenceExpression(builder_, level_ + 1);
    result_ = result_ && typeArguments(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOT);
    result_ = result_ && newExpression_2_3(builder_, level_ + 1);
    result_ = result_ && argumentsWrapper(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // referenceExpression | 'new'
  private static boolean newExpression_2_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newExpression_2_3")) return false;
    boolean result_;
    result_ = referenceExpression(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, NEW);
    return result_;
  }

  /* ********************************************************** */
  // ('new' | 'const') type ('.' (referenceExpression | 'new'))? <<argumentsWrapper>>
  static boolean newExpressionWithKeyword(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newExpressionWithKeyword")) return false;
    if (!nextTokenIs(builder_, "", CONST, NEW)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = newExpressionWithKeyword_0(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, type(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, newExpressionWithKeyword_2(builder_, level_ + 1)) && result_;
    result_ = pinned_ && argumentsWrapper(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // 'new' | 'const'
  private static boolean newExpressionWithKeyword_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newExpressionWithKeyword_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, NEW);
    if (!result_) result_ = consumeToken(builder_, CONST);
    return result_;
  }

  // ('.' (referenceExpression | 'new'))?
  private static boolean newExpressionWithKeyword_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newExpressionWithKeyword_2")) return false;
    newExpressionWithKeyword_2_0(builder_, level_ + 1);
    return true;
  }

  // '.' (referenceExpression | 'new')
  private static boolean newExpressionWithKeyword_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newExpressionWithKeyword_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && newExpressionWithKeyword_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // referenceExpression | 'new'
  private static boolean newExpressionWithKeyword_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "newExpressionWithKeyword_2_0_1")) return false;
    boolean result_;
    result_ = referenceExpression(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, NEW);
    return result_;
  }

  /* ********************************************************** */
  // patternAssignment
  //                                | block // Guard to break tie with map literal.
  //                                | functionDeclarationWithBody
  //                                | forStatement
  //                                | whileStatement
  //                                | doWhileStatement
  //                                | switchStatementOrExpression
  //                                | ifStatement
  //                                | rethrowStatement
  //                                | tryStatement
  //                                | breakStatement
  //                                | continueStatement
  //                                | returnStatement
  //                                | assertStatementWithSemicolon
  //                                | patternVariableDeclaration ';'?
  //                                | statementFollowedBySemiColon
  //                                | yieldEachStatement
  //                                | yieldStatement
  //                                | ';'
  //                                | '=>'
  static boolean nonLabelledStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nonLabelledStatement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = patternAssignment(builder_, level_ + 1);
    if (!result_) result_ = block(builder_, level_ + 1);
    if (!result_) result_ = functionDeclarationWithBody(builder_, level_ + 1);
    if (!result_) result_ = forStatement(builder_, level_ + 1);
    if (!result_) result_ = whileStatement(builder_, level_ + 1);
    if (!result_) result_ = doWhileStatement(builder_, level_ + 1);
    if (!result_) result_ = switchStatementOrExpression(builder_, level_ + 1);
    if (!result_) result_ = ifStatement(builder_, level_ + 1);
    if (!result_) result_ = rethrowStatement(builder_, level_ + 1);
    if (!result_) result_ = tryStatement(builder_, level_ + 1);
    if (!result_) result_ = breakStatement(builder_, level_ + 1);
    if (!result_) result_ = continueStatement(builder_, level_ + 1);
    if (!result_) result_ = returnStatement(builder_, level_ + 1);
    if (!result_) result_ = assertStatementWithSemicolon(builder_, level_ + 1);
    if (!result_) result_ = nonLabelledStatement_14(builder_, level_ + 1);
    if (!result_) result_ = statementFollowedBySemiColon(builder_, level_ + 1);
    if (!result_) result_ = yieldEachStatement(builder_, level_ + 1);
    if (!result_) result_ = yieldStatement(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = consumeToken(builder_, EXPRESSION_BODY_DEF);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // patternVariableDeclaration ';'?
  private static boolean nonLabelledStatement_14(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nonLabelledStatement_14")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = patternVariableDeclaration(builder_, level_ + 1);
    result_ = result_ && nonLabelledStatement_14_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ';'?
  private static boolean nonLabelledStatement_14_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nonLabelledStatement_14_1")) return false;
    consumeToken(builder_, SEMICOLON);
    return true;
  }

  /* ********************************************************** */
  // functionFormalParameter
  //                         | fieldFormalParameter
  //                         | simpleFormalParameter
  //                         | incorrectNormalFormalParameter
  public static boolean normalFormalParameter(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalFormalParameter")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NORMAL_FORMAL_PARAMETER, "<normal formal parameter>");
    result_ = functionFormalParameter(builder_, level_ + 1);
    if (!result_) result_ = fieldFormalParameter(builder_, level_ + 1);
    if (!result_) result_ = simpleFormalParameter(builder_, level_ + 1);
    if (!result_) result_ = incorrectNormalFormalParameter(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // normalFormalParameter (',' normalFormalParameter)*
  static boolean normalFormalParameters(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalFormalParameters")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = normalFormalParameter(builder_, level_ + 1);
    result_ = result_ && normalFormalParameters_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (',' normalFormalParameter)*
  private static boolean normalFormalParameters_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalFormalParameters_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!normalFormalParameters_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "normalFormalParameters_1", pos_)) break;
    }
    return true;
  }

  // ',' normalFormalParameter
  private static boolean normalFormalParameters_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalFormalParameters_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && normalFormalParameter(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // typedIdentifier | type
  public static boolean normalParameterType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalParameterType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NORMAL_PARAMETER_TYPE, "<normal parameter type>");
    result_ = typedIdentifier(builder_, level_ + 1);
    if (!result_) result_ = type(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // normalParameterType (',' normalParameterType)*
  static boolean normalParameterTypes(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalParameterTypes")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = normalParameterType(builder_, level_ + 1);
    result_ = result_ && normalParameterTypes_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (',' normalParameterType)*
  private static boolean normalParameterTypes_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalParameterTypes_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!normalParameterTypes_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "normalParameterTypes_1", pos_)) break;
    }
    return true;
  }

  // ',' normalParameterType
  private static boolean normalParameterTypes_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalParameterTypes_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && normalParameterType(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // !(')' | ',')
  static boolean not_paren_or_comma_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "not_paren_or_comma_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !not_paren_or_comma_recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ')' | ','
  private static boolean not_paren_or_comma_recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "not_paren_or_comma_recover_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, RPAREN);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    return result_;
  }

  /* ********************************************************** */
  // !')'
  static boolean not_paren_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "not_paren_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !consumeToken(builder_, RPAREN);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '?' expression
  public static boolean nullAwareElement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nullAwareElement")) return false;
    if (!nextTokenIs(builder_, QUEST)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NULL_AWARE_ELEMENT, null);
    result_ = consumeToken(builder_, QUEST);
    pinned_ = result_; // pin = 1
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // simpleQualifiedReferenceExpression typeArguments? '(' patternFields? ')'
  public static boolean objectPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "objectPattern")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, OBJECT_PATTERN, "<object pattern>");
    result_ = simpleQualifiedReferenceExpression(builder_, level_ + 1);
    result_ = result_ && objectPattern_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LPAREN);
    result_ = result_ && objectPattern_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // typeArguments?
  private static boolean objectPattern_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "objectPattern_1")) return false;
    typeArguments(builder_, level_ + 1);
    return true;
  }

  // patternFields?
  private static boolean objectPattern_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "objectPattern_3")) return false;
    patternFields(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // 'on' typeList
  public static boolean onMixins(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "onMixins")) return false;
    if (!nextTokenIs(builder_, ON)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ON_MIXINS, null);
    result_ = consumeToken(builder_, ON);
    pinned_ = result_; // pin = 1
    result_ = result_ && typeList(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // catchPart block | 'on' type catchPart? block
  public static boolean onPart(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "onPart")) return false;
    if (!nextTokenIs(builder_, "<on part>", CATCH, ON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ON_PART, "<on part>");
    result_ = onPart_0(builder_, level_ + 1);
    if (!result_) result_ = onPart_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // catchPart block
  private static boolean onPart_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "onPart_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = catchPart(builder_, level_ + 1);
    result_ = result_ && block(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'on' type catchPart? block
  private static boolean onPart_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "onPart_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ON);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && onPart_1_2(builder_, level_ + 1);
    result_ = result_ && block(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // catchPart?
  private static boolean onPart_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "onPart_1_2")) return false;
    catchPart(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // optionalPositionalFormalParameters (',' namedFormalParameters)? | namedFormalParameters
  public static boolean optionalFormalParameters(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionalFormalParameters")) return false;
    if (!nextTokenIs(builder_, "<optional formal parameters>", LBRACE, LBRACKET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, OPTIONAL_FORMAL_PARAMETERS, "<optional formal parameters>");
    result_ = optionalFormalParameters_0(builder_, level_ + 1);
    if (!result_) result_ = namedFormalParameters(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // optionalPositionalFormalParameters (',' namedFormalParameters)?
  private static boolean optionalFormalParameters_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionalFormalParameters_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = optionalPositionalFormalParameters(builder_, level_ + 1);
    result_ = result_ && optionalFormalParameters_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (',' namedFormalParameters)?
  private static boolean optionalFormalParameters_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionalFormalParameters_0_1")) return false;
    optionalFormalParameters_0_1_0(builder_, level_ + 1);
    return true;
  }

  // ',' namedFormalParameters
  private static boolean optionalFormalParameters_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionalFormalParameters_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && namedFormalParameters(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // optionalPositionalParameterTypes | namedParameterTypes
  public static boolean optionalParameterTypes(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionalParameterTypes")) return false;
    if (!nextTokenIs(builder_, "<optional parameter types>", LBRACE, LBRACKET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, OPTIONAL_PARAMETER_TYPES, "<optional parameter types>");
    result_ = optionalPositionalParameterTypes(builder_, level_ + 1);
    if (!result_) result_ = namedParameterTypes(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '[' defaultFormalNamedParameter (',' defaultFormalNamedParameter)* ','? ']'
  static boolean optionalPositionalFormalParameters(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionalPositionalFormalParameters")) return false;
    if (!nextTokenIs(builder_, LBRACKET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACKET);
    result_ = result_ && defaultFormalNamedParameter(builder_, level_ + 1);
    result_ = result_ && optionalPositionalFormalParameters_2(builder_, level_ + 1);
    result_ = result_ && optionalPositionalFormalParameters_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (',' defaultFormalNamedParameter)*
  private static boolean optionalPositionalFormalParameters_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionalPositionalFormalParameters_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!optionalPositionalFormalParameters_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "optionalPositionalFormalParameters_2", pos_)) break;
    }
    return true;
  }

  // ',' defaultFormalNamedParameter
  private static boolean optionalPositionalFormalParameters_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionalPositionalFormalParameters_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && defaultFormalNamedParameter(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean optionalPositionalFormalParameters_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionalPositionalFormalParameters_3")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // '[' normalParameterTypes ','? ']'
  static boolean optionalPositionalParameterTypes(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionalPositionalParameterTypes")) return false;
    if (!nextTokenIs(builder_, LBRACKET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACKET);
    result_ = result_ && normalParameterTypes(builder_, level_ + 1);
    result_ = result_ && optionalPositionalParameterTypes_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean optionalPositionalParameterTypes_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "optionalPositionalParameterTypes_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // listPattern | mapPattern | recordPattern | parenthesizedPattern | objectPattern
  static boolean outerPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "outerPattern")) return false;
    boolean result_;
    result_ = listPattern(builder_, level_ + 1);
    if (!result_) result_ = mapPattern(builder_, level_ + 1);
    if (!result_) result_ = recordPattern(builder_, level_ + 1);
    if (!result_) result_ = parenthesizedPattern(builder_, level_ + 1);
    if (!result_) result_ = objectPattern(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // << nonStrictID >>
  public static boolean parameterNameReferenceExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameterNameReferenceExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, PARAMETER_NAME_REFERENCE_EXPRESSION, "<parameter name reference expression>");
    result_ = nonStrictID(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '(' ')'
  //                     | '(' normalParameterTypes ','? ')'
  //                     | '(' normalParameterTypes ',' optionalParameterTypes ')'
  //                     | '(' optionalParameterTypes ')'
  public static boolean parameterTypeList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameterTypeList")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parseTokens(builder_, 0, LPAREN, RPAREN);
    if (!result_) result_ = parameterTypeList_1(builder_, level_ + 1);
    if (!result_) result_ = parameterTypeList_2(builder_, level_ + 1);
    if (!result_) result_ = parameterTypeList_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, PARAMETER_TYPE_LIST, result_);
    return result_;
  }

  // '(' normalParameterTypes ','? ')'
  private static boolean parameterTypeList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameterTypeList_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && normalParameterTypes(builder_, level_ + 1);
    result_ = result_ && parameterTypeList_1_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean parameterTypeList_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameterTypeList_1_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  // '(' normalParameterTypes ',' optionalParameterTypes ')'
  private static boolean parameterTypeList_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameterTypeList_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && normalParameterTypes(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && optionalParameterTypes(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // '(' optionalParameterTypes ')'
  private static boolean parameterTypeList_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameterTypeList_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && optionalParameterTypes(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // !')'
  static boolean parenthesesRecovery(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parenthesesRecovery")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !consumeToken(builder_, RPAREN);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '(' expressionInParentheses ')'
  public static boolean parenthesizedExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parenthesizedExpression")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PARENTHESIZED_EXPRESSION, null);
    result_ = consumeToken(builder_, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expressionInParentheses(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // '(' pattern ')'
  public static boolean parenthesizedPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parenthesizedPattern")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && pattern(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, PARENTHESIZED_PATTERN, result_);
    return result_;
  }

  /* ********************************************************** */
  // metadata* 'part' 'of' (libraryId | uriElement)';'
  public static boolean partOfStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "partOfStatement")) return false;
    if (!nextTokenIs(builder_, "<part of statement>", AT, PART)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PART_OF_STATEMENT, "<part of statement>");
    result_ = partOfStatement_0(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, PART, OF);
    result_ = result_ && partOfStatement_3(builder_, level_ + 1);
    pinned_ = result_; // pin = 4
    result_ = result_ && consumeToken(builder_, SEMICOLON);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean partOfStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "partOfStatement_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "partOfStatement_0", pos_)) break;
    }
    return true;
  }

  // libraryId | uriElement
  private static boolean partOfStatement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "partOfStatement_3")) return false;
    boolean result_;
    result_ = libraryId(builder_, level_ + 1);
    if (!result_) result_ = uriElement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // metadata* 'part' uriElement ';'
  public static boolean partStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "partStatement")) return false;
    if (!nextTokenIs(builder_, "<part statement>", AT, PART)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PART_STATEMENT, "<part statement>");
    result_ = partStatement_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, PART);
    result_ = result_ && uriElement(builder_, level_ + 1);
    pinned_ = result_; // pin = 3
    result_ = result_ && consumeToken(builder_, SEMICOLON);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean partStatement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "partStatement_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "partStatement_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // logicalOrPatternWrapper
  static boolean pattern(PsiBuilder builder_, int level_) {
    return logicalOrPatternWrapper(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // outerPattern '=' expression
  public static boolean patternAssignment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "patternAssignment")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PATTERN_ASSIGNMENT, "<pattern assignment>");
    result_ = outerPattern(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EQ);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // 'case' guardedPattern ':'
  static boolean patternCase(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "patternCase")) return false;
    if (!nextTokenIs(builder_, CASE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CASE);
    result_ = result_ && guardedPattern(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // (referenceExpression? ':')? pattern
  public static boolean patternField(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "patternField")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PATTERN_FIELD, "<pattern field>");
    result_ = patternField_0(builder_, level_ + 1);
    result_ = result_ && pattern(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (referenceExpression? ':')?
  private static boolean patternField_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "patternField_0")) return false;
    patternField_0_0(builder_, level_ + 1);
    return true;
  }

  // referenceExpression? ':'
  private static boolean patternField_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "patternField_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = patternField_0_0_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // referenceExpression?
  private static boolean patternField_0_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "patternField_0_0_0")) return false;
    referenceExpression(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // patternField (',' patternField)* ','?
  static boolean patternFields(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "patternFields")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = patternField(builder_, level_ + 1);
    result_ = result_ && patternFields_1(builder_, level_ + 1);
    result_ = result_ && patternFields_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (',' patternField)*
  private static boolean patternFields_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "patternFields_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!patternFields_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "patternFields_1", pos_)) break;
    }
    return true;
  }

  // ',' patternField
  private static boolean patternFields_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "patternFields_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && patternField(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean patternFields_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "patternFields_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // metadata* ('final' | 'var') outerPattern '=' expression
  public static boolean patternVariableDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "patternVariableDeclaration")) return false;
    if (!nextTokenIs(builder_, "<pattern variable declaration>", AT, FINAL, VAR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PATTERN_VARIABLE_DECLARATION, "<pattern variable declaration>");
    result_ = patternVariableDeclaration_0(builder_, level_ + 1);
    result_ = result_ && patternVariableDeclaration_1(builder_, level_ + 1);
    result_ = result_ && outerPattern(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EQ);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata*
  private static boolean patternVariableDeclaration_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "patternVariableDeclaration_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "patternVariableDeclaration_0", pos_)) break;
    }
    return true;
  }

  // 'final' | 'var'
  private static boolean patternVariableDeclaration_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "patternVariableDeclaration_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, FINAL);
    if (!result_) result_ = consumeToken(builder_, VAR);
    return result_;
  }

  /* ********************************************************** */
  // (prefixOperator prefixExpression) | awaitExpression | suffixExpressionWrapper
  public static boolean prefixExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "prefixExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, PREFIX_EXPRESSION, "<prefix expression>");
    result_ = prefixExpression_0(builder_, level_ + 1);
    if (!result_) result_ = awaitExpression(builder_, level_ + 1);
    if (!result_) result_ = suffixExpressionWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // prefixOperator prefixExpression
  private static boolean prefixExpression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "prefixExpression_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = prefixOperator(builder_, level_ + 1);
    result_ = result_ && prefixExpression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '-' | '+' | '--' | '++' | '!' | '~'
  public static boolean prefixOperator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "prefixOperator")) return false;
    if (!nextTokenIs(builder_, "<prefix operator>", BIN_NOT, MINUS,
      MINUS_MINUS, NOT, PLUS, PLUS_PLUS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PREFIX_OPERATOR, "<prefix operator>");
    result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS_MINUS);
    if (!result_) result_ = consumeToken(builder_, PLUS_PLUS);
    if (!result_) result_ = consumeToken(builder_, NOT);
    if (!result_) result_ = consumeToken(builder_, BIN_NOT);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // functionExpression |
  //                      literalExpression |
  //                      recordOrParenthesizedExpressionWrapper |
  //                      newExpression | // constant object expression is also parsed as newExpression
  //                      shorthandExpression |
  //                      refOrThisOrSuperOrParenExpression |
  //                      throwExpression |
  //                      switchExpressionWrapper
  static boolean primary(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "primary")) return false;
    boolean result_;
    result_ = functionExpression(builder_, level_ + 1);
    if (!result_) result_ = literalExpression(builder_, level_ + 1);
    if (!result_) result_ = recordOrParenthesizedExpressionWrapper(builder_, level_ + 1);
    if (!result_) result_ = newExpression(builder_, level_ + 1);
    if (!result_) result_ = shorthandExpression(builder_, level_ + 1);
    if (!result_) result_ = refOrThisOrSuperOrParenExpression(builder_, level_ + 1);
    if (!result_) result_ = throwExpression(builder_, level_ + 1);
    if (!result_) result_ = switchExpressionWrapper(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // listPattern | mapPattern | recordPattern | variablePattern | parenthesizedPattern | objectPattern | constantPattern | identifierPattern
  static boolean primaryPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "primaryPattern")) return false;
    boolean result_;
    result_ = listPattern(builder_, level_ + 1);
    if (!result_) result_ = mapPattern(builder_, level_ + 1);
    if (!result_) result_ = recordPattern(builder_, level_ + 1);
    if (!result_) result_ = variablePattern(builder_, level_ + 1);
    if (!result_) result_ = parenthesizedPattern(builder_, level_ + 1);
    if (!result_) result_ = objectPattern(builder_, level_ + 1);
    if (!result_) result_ = constantPattern(builder_, level_ + 1);
    if (!result_) result_ = identifierPattern(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // '.' (referenceExpression | 'new') | '?.' referenceExpression
  public static boolean qualifiedReferenceExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "qualifiedReferenceExpression")) return false;
    if (!nextTokenIs(builder_, "<qualified reference expression>", DOT, QUEST_DOT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, REFERENCE_EXPRESSION, "<qualified reference expression>");
    result_ = qualifiedReferenceExpression_0(builder_, level_ + 1);
    if (!result_) result_ = qualifiedReferenceExpression_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // '.' (referenceExpression | 'new')
  private static boolean qualifiedReferenceExpression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "qualifiedReferenceExpression_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && qualifiedReferenceExpression_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // referenceExpression | 'new'
  private static boolean qualifiedReferenceExpression_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "qualifiedReferenceExpression_0_1")) return false;
    boolean result_;
    result_ = referenceExpression(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, NEW);
    return result_;
  }

  // '?.' referenceExpression
  private static boolean qualifiedReferenceExpression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "qualifiedReferenceExpression_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, QUEST_DOT);
    result_ = result_ && referenceExpression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  public static boolean record(PsiBuilder builder_, int level_) {
    Marker marker_ = enter_section_(builder_);
    exit_section_(builder_, marker_, RECORD, true);
    return true;
  }

  /* ********************************************************** */
  // (<<nonStrictID>> ':' )? expression
  public static boolean recordField(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordField")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RECORD_FIELD, "<record field>");
    result_ = recordField_0(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (<<nonStrictID>> ':' )?
  private static boolean recordField_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordField_0")) return false;
    recordField_0_0(builder_, level_ + 1);
    return true;
  }

  // <<nonStrictID>> ':'
  private static boolean recordField_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordField_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nonStrictID(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ( ',' recordField )* ','?
  static boolean recordFieldsRest(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordFieldsRest")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = recordFieldsRest_0(builder_, level_ + 1);
    result_ = result_ && recordFieldsRest_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ( ',' recordField )*
  private static boolean recordFieldsRest_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordFieldsRest_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!recordFieldsRest_0_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "recordFieldsRest_0", pos_)) break;
    }
    return true;
  }

  // ',' recordField
  private static boolean recordFieldsRest_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordFieldsRest_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && recordField(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean recordFieldsRest_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordFieldsRest_1")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // <<parseRecordOrParenthesizedExpression>>
  static boolean recordOrParenthesizedExpressionWrapper(PsiBuilder builder_, int level_) {
    return parseRecordOrParenthesizedExpression(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // '(' patternFields? ')'
  public static boolean recordPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordPattern")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && recordPattern_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, RECORD_PATTERN, result_);
    return result_;
  }

  // patternFields?
  private static boolean recordPattern_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordPattern_1")) return false;
    patternFields(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // '(' !(recordTypeField ')') recordTypeInner ')' ('?' !(expression ':'))?
  public static boolean recordType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordType")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && recordType_1(builder_, level_ + 1);
    result_ = result_ && recordTypeInner(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    result_ = result_ && recordType_4(builder_, level_ + 1);
    exit_section_(builder_, marker_, RECORD_TYPE, result_);
    return result_;
  }

  // !(recordTypeField ')')
  private static boolean recordType_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordType_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !recordType_1_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // recordTypeField ')'
  private static boolean recordType_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordType_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = recordTypeField(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ('?' !(expression ':'))?
  private static boolean recordType_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordType_4")) return false;
    recordType_4_0(builder_, level_ + 1);
    return true;
  }

  // '?' !(expression ':')
  private static boolean recordType_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordType_4_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, QUEST);
    result_ = result_ && recordType_4_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !(expression ':')
  private static boolean recordType_4_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordType_4_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !recordType_4_0_1_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // expression ':'
  private static boolean recordType_4_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordType_4_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // metadata* type <<nonStrictID>>?
  public static boolean recordTypeField(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeField")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RECORD_TYPE_FIELD, "<record type field>");
    result_ = recordTypeField_0(builder_, level_ + 1);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && recordTypeField_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata*
  private static boolean recordTypeField_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeField_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "recordTypeField_0", pos_)) break;
    }
    return true;
  }

  // <<nonStrictID>>?
  private static boolean recordTypeField_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeField_2")) return false;
    nonStrictID(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // recordTypeField ( ',' recordTypeField )*
  static boolean recordTypeFields(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeFields")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = recordTypeField(builder_, level_ + 1);
    result_ = result_ && recordTypeFields_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ( ',' recordTypeField )*
  private static boolean recordTypeFields_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeFields_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!recordTypeFields_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "recordTypeFields_1", pos_)) break;
    }
    return true;
  }

  // ',' recordTypeField
  private static boolean recordTypeFields_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeFields_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && recordTypeField(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // recordTypeFields ',' recordTypeNamedFields  |
  //                             recordTypeFields ','?  |
  //                             recordTypeNamedFields?
  static boolean recordTypeInner(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeInner")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = recordTypeInner_0(builder_, level_ + 1);
    if (!result_) result_ = recordTypeInner_1(builder_, level_ + 1);
    if (!result_) result_ = recordTypeInner_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // recordTypeFields ',' recordTypeNamedFields
  private static boolean recordTypeInner_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeInner_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = recordTypeFields(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && recordTypeNamedFields(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // recordTypeFields ','?
  private static boolean recordTypeInner_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeInner_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = recordTypeFields(builder_, level_ + 1);
    result_ = result_ && recordTypeInner_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean recordTypeInner_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeInner_1_1")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  // recordTypeNamedFields?
  private static boolean recordTypeInner_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeInner_2")) return false;
    recordTypeNamedFields(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // metadata* type <<nonStrictID>>
  public static boolean recordTypeNamedField(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeNamedField")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RECORD_TYPE_NAMED_FIELD, "<record type named field>");
    result_ = recordTypeNamedField_0(builder_, level_ + 1);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && nonStrictID(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata*
  private static boolean recordTypeNamedField_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeNamedField_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "recordTypeNamedField_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '{' recordTypeNamedField ( ',' recordTypeNamedField )* ','? '}'
  public static boolean recordTypeNamedFields(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeNamedFields")) return false;
    if (!nextTokenIs(builder_, LBRACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACE);
    result_ = result_ && recordTypeNamedField(builder_, level_ + 1);
    result_ = result_ && recordTypeNamedFields_2(builder_, level_ + 1);
    result_ = result_ && recordTypeNamedFields_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, RECORD_TYPE_NAMED_FIELDS, result_);
    return result_;
  }

  // ( ',' recordTypeNamedField )*
  private static boolean recordTypeNamedFields_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeNamedFields_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!recordTypeNamedFields_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "recordTypeNamedFields_2", pos_)) break;
    }
    return true;
  }

  // ',' recordTypeNamedField
  private static boolean recordTypeNamedFields_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeNamedFields_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && recordTypeNamedField(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean recordTypeNamedFields_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "recordTypeNamedFields_3")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  static boolean recoverUntilRBrace(PsiBuilder builder_, int level_) {
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    exit_section_(builder_, level_, marker_, true, false, DartParser::simple_scope_recover);
    return true;
  }

  /* ********************************************************** */
  // ':' 'this' ('.' (referenceExpression | 'new'))? <<argumentsWrapper>>
  public static boolean redirection(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "redirection")) return false;
    if (!nextTokenIs(builder_, COLON)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, REDIRECTION, null);
    result_ = consumeTokens(builder_, 2, COLON, THIS);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, redirection_2(builder_, level_ + 1));
    result_ = pinned_ && argumentsWrapper(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // ('.' (referenceExpression | 'new'))?
  private static boolean redirection_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "redirection_2")) return false;
    redirection_2_0(builder_, level_ + 1);
    return true;
  }

  // '.' (referenceExpression | 'new')
  private static boolean redirection_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "redirection_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && redirection_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // referenceExpression | 'new'
  private static boolean redirection_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "redirection_2_0_1")) return false;
    boolean result_;
    result_ = referenceExpression(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, NEW);
    return result_;
  }

  /* ********************************************************** */
  // referenceExpression | thisExpression | superExpression | << parenthesizedExpressionWrapper >>
  static boolean refOrThisOrSuperOrParenExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "refOrThisOrSuperOrParenExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = referenceExpression(builder_, level_ + 1);
    if (!result_) result_ = thisExpression(builder_, level_ + 1);
    if (!result_) result_ = superExpression(builder_, level_ + 1);
    if (!result_) result_ = parenthesizedExpressionWrapper(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // << nonStrictID >>
  public static boolean referenceExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "referenceExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, REFERENCE_EXPRESSION, "<reference expression>");
    result_ = nonStrictID(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // <<gtEq>> | '>' | '<=' | '<'
  public static boolean relationalOperator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationalOperator")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RELATIONAL_OPERATOR, "<relational operator>");
    result_ = gtEq(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, GT);
    if (!result_) result_ = consumeToken(builder_, LT_EQ);
    if (!result_) result_ = consumeToken(builder_, LT);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // (equalityOperator | relationalOperator) bitwiseExpressionWrapper
  public static boolean relationalPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationalPattern")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RELATIONAL_PATTERN, "<relational pattern>");
    result_ = relationalPattern_0(builder_, level_ + 1);
    result_ = result_ && bitwiseExpressionWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // equalityOperator | relationalOperator
  private static boolean relationalPattern_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationalPattern_0")) return false;
    boolean result_;
    result_ = equalityOperator(builder_, level_ + 1);
    if (!result_) result_ = relationalOperator(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // relationalPattern | unaryPatternWrapper
  static boolean relationalPatternWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "relationalPatternWrapper")) return false;
    boolean result_;
    result_ = relationalPattern(builder_, level_ + 1);
    if (!result_) result_ = unaryPatternWrapper(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // ('.' (componentName | 'new'))? '(' metadata* type componentName ')'
  static boolean representationDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "representationDeclaration")) return false;
    if (!nextTokenIs(builder_, "", DOT, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = representationDeclaration_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LPAREN);
    result_ = result_ && representationDeclaration_2(builder_, level_ + 1);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ('.' (componentName | 'new'))?
  private static boolean representationDeclaration_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "representationDeclaration_0")) return false;
    representationDeclaration_0_0(builder_, level_ + 1);
    return true;
  }

  // '.' (componentName | 'new')
  private static boolean representationDeclaration_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "representationDeclaration_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && representationDeclaration_0_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // componentName | 'new'
  private static boolean representationDeclaration_0_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "representationDeclaration_0_0_1")) return false;
    boolean result_;
    result_ = componentName(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, NEW);
    return result_;
  }

  // metadata*
  private static boolean representationDeclaration_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "representationDeclaration_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "representationDeclaration_2", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '...' pattern?
  public static boolean restPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "restPattern")) return false;
    if (!nextTokenIs(builder_, DOT_DOT_DOT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT_DOT_DOT);
    result_ = result_ && restPattern_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, REST_PATTERN, result_);
    return result_;
  }

  // pattern?
  private static boolean restPattern_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "restPattern_1")) return false;
    pattern(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // 'rethrow' ';'
  public static boolean rethrowStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "rethrowStatement")) return false;
    if (!nextTokenIs(builder_, RETHROW)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RETHROW_STATEMENT, null);
    result_ = consumeTokens(builder_, 1, RETHROW, SEMICOLON);
    pinned_ = result_; // pin = 1
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // 'return' expression? ';'
  public static boolean returnStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnStatement")) return false;
    if (!nextTokenIs(builder_, RETURN)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RETURN_STATEMENT, null);
    result_ = consumeToken(builder_, RETURN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, returnStatement_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, SEMICOLON) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // expression?
  private static boolean returnStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnStatement_1")) return false;
    expression(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // 'void' !untypedFunctionType | type
  public static boolean returnType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RETURN_TYPE, "<return type>");
    result_ = returnType_0(builder_, level_ + 1);
    if (!result_) result_ = type(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // 'void' !untypedFunctionType
  private static boolean returnType_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnType_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, VOID);
    result_ = result_ && returnType_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !untypedFunctionType
  private static boolean returnType_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "returnType_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !untypedFunctionType(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // 'const'? typeArguments? '{' elements? '}'
  public static boolean setOrMapLiteralExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setOrMapLiteralExpression")) return false;
    if (!nextTokenIs(builder_, "<set or map literal expression>", CONST, LBRACE, LT)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SET_OR_MAP_LITERAL_EXPRESSION, "<set or map literal expression>");
    result_ = setOrMapLiteralExpression_0(builder_, level_ + 1);
    result_ = result_ && setOrMapLiteralExpression_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LBRACE);
    pinned_ = result_; // pin = 3
    result_ = result_ && report_error_(builder_, setOrMapLiteralExpression_3(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RBRACE) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // 'const'?
  private static boolean setOrMapLiteralExpression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setOrMapLiteralExpression_0")) return false;
    consumeToken(builder_, CONST);
    return true;
  }

  // typeArguments?
  private static boolean setOrMapLiteralExpression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setOrMapLiteralExpression_1")) return false;
    typeArguments(builder_, level_ + 1);
    return true;
  }

  // elements?
  private static boolean setOrMapLiteralExpression_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setOrMapLiteralExpression_3")) return false;
    elements(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // setterDeclarationWithReturnType | setterDeclarationWithoutReturnType
  public static boolean setterDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setterDeclaration")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SETTER_DECLARATION, "<setter declaration>");
    result_ = setterDeclarationWithReturnType(builder_, level_ + 1);
    if (!result_) result_ = setterDeclarationWithoutReturnType(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // metadata* 'augment'? ('external' | 'static')* returnType 'set' componentName formalParameterList (';' | functionBodyOrNative)
  static boolean setterDeclarationWithReturnType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setterDeclarationWithReturnType")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = setterDeclarationWithReturnType_0(builder_, level_ + 1);
    result_ = result_ && setterDeclarationWithReturnType_1(builder_, level_ + 1);
    result_ = result_ && setterDeclarationWithReturnType_2(builder_, level_ + 1);
    result_ = result_ && returnType(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, SET);
    result_ = result_ && componentName(builder_, level_ + 1);
    pinned_ = result_; // pin = 6
    result_ = result_ && report_error_(builder_, formalParameterList(builder_, level_ + 1));
    result_ = pinned_ && setterDeclarationWithReturnType_7(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean setterDeclarationWithReturnType_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setterDeclarationWithReturnType_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "setterDeclarationWithReturnType_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean setterDeclarationWithReturnType_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setterDeclarationWithReturnType_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // ('external' | 'static')*
  private static boolean setterDeclarationWithReturnType_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setterDeclarationWithReturnType_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!setterDeclarationWithReturnType_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "setterDeclarationWithReturnType_2", pos_)) break;
    }
    return true;
  }

  // 'external' | 'static'
  private static boolean setterDeclarationWithReturnType_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setterDeclarationWithReturnType_2_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, EXTERNAL);
    if (!result_) result_ = consumeToken(builder_, STATIC);
    return result_;
  }

  // ';' | functionBodyOrNative
  private static boolean setterDeclarationWithReturnType_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setterDeclarationWithReturnType_7")) return false;
    boolean result_;
    result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = functionBodyOrNative(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // metadata* 'augment'? ('external' | 'static')*            'set' componentName formalParameterList (';' | functionBodyOrNative)
  static boolean setterDeclarationWithoutReturnType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setterDeclarationWithoutReturnType")) return false;
    if (!nextTokenIs(builder_, "", AT, AUGMENT,
      EXTERNAL, SET, STATIC)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = setterDeclarationWithoutReturnType_0(builder_, level_ + 1);
    result_ = result_ && setterDeclarationWithoutReturnType_1(builder_, level_ + 1);
    result_ = result_ && setterDeclarationWithoutReturnType_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, SET);
    result_ = result_ && componentName(builder_, level_ + 1);
    pinned_ = result_; // pin = 5
    result_ = result_ && report_error_(builder_, formalParameterList(builder_, level_ + 1));
    result_ = pinned_ && setterDeclarationWithoutReturnType_6(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // metadata*
  private static boolean setterDeclarationWithoutReturnType_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setterDeclarationWithoutReturnType_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "setterDeclarationWithoutReturnType_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean setterDeclarationWithoutReturnType_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setterDeclarationWithoutReturnType_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // ('external' | 'static')*
  private static boolean setterDeclarationWithoutReturnType_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setterDeclarationWithoutReturnType_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!setterDeclarationWithoutReturnType_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "setterDeclarationWithoutReturnType_2", pos_)) break;
    }
    return true;
  }

  // 'external' | 'static'
  private static boolean setterDeclarationWithoutReturnType_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setterDeclarationWithoutReturnType_2_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, EXTERNAL);
    if (!result_) result_ = consumeToken(builder_, STATIC);
    return result_;
  }

  // ';' | functionBodyOrNative
  private static boolean setterDeclarationWithoutReturnType_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setterDeclarationWithoutReturnType_6")) return false;
    boolean result_;
    result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = functionBodyOrNative(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // shiftOperator additiveExpressionWrapper
  public static boolean shiftExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shiftExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, SHIFT_EXPRESSION, "<shift expression>");
    result_ = shiftOperator(builder_, level_ + 1);
    result_ = result_ && additiveExpressionWrapper(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // additiveExpressionWrapper shiftExpression*
  static boolean shiftExpressionWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shiftExpressionWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = additiveExpressionWrapper(builder_, level_ + 1);
    result_ = result_ && shiftExpressionWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // shiftExpression*
  private static boolean shiftExpressionWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shiftExpressionWrapper_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!shiftExpression(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "shiftExpressionWrapper_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '<<' | <<gtGtGt>> | <<gtGt>>
  public static boolean shiftOperator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shiftOperator")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SHIFT_OPERATOR, "<shift operator>");
    result_ = consumeToken(builder_, LT_LT);
    if (!result_) result_ = gtGtGt(builder_, level_ + 1);
    if (!result_) result_ = gtGt(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // SHORT_TEMPLATE_ENTRY_START (thisExpression | referenceExpression)
  public static boolean shortTemplateEntry(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shortTemplateEntry")) return false;
    if (!nextTokenIs(builder_, SHORT_TEMPLATE_ENTRY_START)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SHORT_TEMPLATE_ENTRY, null);
    result_ = consumeToken(builder_, SHORT_TEMPLATE_ENTRY_START);
    pinned_ = result_; // pin = 1
    result_ = result_ && shortTemplateEntry_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // thisExpression | referenceExpression
  private static boolean shortTemplateEntry_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shortTemplateEntry_1")) return false;
    boolean result_;
    result_ = thisExpression(builder_, level_ + 1);
    if (!result_) result_ = referenceExpression(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // '.' referenceExpression
  public static boolean shorthandExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shorthandExpression")) return false;
    if (!nextTokenIs(builder_, DOT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && referenceExpression(builder_, level_ + 1);
    exit_section_(builder_, marker_, SHORTHAND_EXPRESSION, result_);
    return result_;
  }

  /* ********************************************************** */
  // shorthandNewExpressionPrefix typeArguments? <<argumentsWrapper>>?
  static boolean shorthandNewExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shorthandNewExpression")) return false;
    if (!nextTokenIs(builder_, "", CONST, DOT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = shorthandNewExpressionPrefix(builder_, level_ + 1);
    result_ = result_ && shorthandNewExpression_1(builder_, level_ + 1);
    result_ = result_ && shorthandNewExpression_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // typeArguments?
  private static boolean shorthandNewExpression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shorthandNewExpression_1")) return false;
    typeArguments(builder_, level_ + 1);
    return true;
  }

  // <<argumentsWrapper>>?
  private static boolean shorthandNewExpression_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shorthandNewExpression_2")) return false;
    argumentsWrapper(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // '.' 'new' | 'const' '.' ('new' | referenceExpression)
  static boolean shorthandNewExpressionPrefix(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shorthandNewExpressionPrefix")) return false;
    if (!nextTokenIs(builder_, "", CONST, DOT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parseTokens(builder_, 0, DOT, NEW);
    if (!result_) result_ = shorthandNewExpressionPrefix_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'const' '.' ('new' | referenceExpression)
  private static boolean shorthandNewExpressionPrefix_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shorthandNewExpressionPrefix_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, CONST, DOT);
    result_ = result_ && shorthandNewExpressionPrefix_1_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'new' | referenceExpression
  private static boolean shorthandNewExpressionPrefix_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shorthandNewExpressionPrefix_1_2")) return false;
    boolean result_;
    result_ = consumeToken(builder_, NEW);
    if (!result_) result_ = referenceExpression(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // 'show' libraryReferenceList
  public static boolean showCombinator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "showCombinator")) return false;
    if (!nextTokenIs(builder_, SHOW)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SHOW_COMBINATOR, null);
    result_ = consumeToken(builder_, SHOW);
    pinned_ = result_; // pin = 1
    result_ = result_ && libraryReferenceList(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // declaredIdentifier | metadata* 'required'? componentName | metadata* 'required'? 'covariant' componentName
  public static boolean simpleFormalParameter(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleFormalParameter")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SIMPLE_FORMAL_PARAMETER, "<simple formal parameter>");
    result_ = declaredIdentifier(builder_, level_ + 1);
    if (!result_) result_ = simpleFormalParameter_1(builder_, level_ + 1);
    if (!result_) result_ = simpleFormalParameter_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata* 'required'? componentName
  private static boolean simpleFormalParameter_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleFormalParameter_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = simpleFormalParameter_1_0(builder_, level_ + 1);
    result_ = result_ && simpleFormalParameter_1_1(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean simpleFormalParameter_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleFormalParameter_1_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "simpleFormalParameter_1_0", pos_)) break;
    }
    return true;
  }

  // 'required'?
  private static boolean simpleFormalParameter_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleFormalParameter_1_1")) return false;
    consumeToken(builder_, REQUIRED);
    return true;
  }

  // metadata* 'required'? 'covariant' componentName
  private static boolean simpleFormalParameter_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleFormalParameter_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = simpleFormalParameter_2_0(builder_, level_ + 1);
    result_ = result_ && simpleFormalParameter_2_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COVARIANT);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // metadata*
  private static boolean simpleFormalParameter_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleFormalParameter_2_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "simpleFormalParameter_2_0", pos_)) break;
    }
    return true;
  }

  // 'required'?
  private static boolean simpleFormalParameter_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleFormalParameter_2_1")) return false;
    consumeToken(builder_, REQUIRED);
    return true;
  }

  /* ********************************************************** */
  // referenceExpression qualifiedReferenceExpression*
  public static boolean simpleQualifiedReferenceExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleQualifiedReferenceExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, REFERENCE_EXPRESSION, "<simple qualified reference expression>");
    result_ = referenceExpression(builder_, level_ + 1);
    result_ = result_ && simpleQualifiedReferenceExpression_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // qualifiedReferenceExpression*
  private static boolean simpleQualifiedReferenceExpression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleQualifiedReferenceExpression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!qualifiedReferenceExpression(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "simpleQualifiedReferenceExpression_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // simpleQualifiedReferenceExpression typeArguments? ('?' !(expression ':'))?
  public static boolean simpleType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SIMPLE_TYPE, "<simple type>");
    result_ = simpleQualifiedReferenceExpression(builder_, level_ + 1);
    result_ = result_ && simpleType_1(builder_, level_ + 1);
    result_ = result_ && simpleType_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // typeArguments?
  private static boolean simpleType_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleType_1")) return false;
    typeArguments(builder_, level_ + 1);
    return true;
  }

  // ('?' !(expression ':'))?
  private static boolean simpleType_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleType_2")) return false;
    simpleType_2_0(builder_, level_ + 1);
    return true;
  }

  // '?' !(expression ':')
  private static boolean simpleType_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleType_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, QUEST);
    result_ = result_ && simpleType_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !(expression ':')
  private static boolean simpleType_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleType_2_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !simpleType_2_0_1_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // expression ':'
  private static boolean simpleType_2_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleType_2_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // !'}'
  static boolean simple_scope_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simple_scope_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !consumeToken(builder_, RBRACE);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // ('...' | '...?') expression
  public static boolean spreadElement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "spreadElement")) return false;
    if (!nextTokenIs(builder_, "<spread element>", DOT_DOT_DOT, DOT_DOT_DOT_QUEST)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SPREAD_ELEMENT, "<spread element>");
    result_ = spreadElement_0(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // '...' | '...?'
  private static boolean spreadElement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "spreadElement_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, DOT_DOT_DOT);
    if (!result_) result_ = consumeToken(builder_, DOT_DOT_DOT_QUEST);
    return result_;
  }

  /* ********************************************************** */
  // superclass? mixins? interfaces? ('native' stringLiteralExpression?)? classBody?
  static boolean standardClassDeclarationTail(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "standardClassDeclarationTail")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = standardClassDeclarationTail_0(builder_, level_ + 1);
    result_ = result_ && standardClassDeclarationTail_1(builder_, level_ + 1);
    result_ = result_ && standardClassDeclarationTail_2(builder_, level_ + 1);
    result_ = result_ && standardClassDeclarationTail_3(builder_, level_ + 1);
    result_ = result_ && standardClassDeclarationTail_4(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // superclass?
  private static boolean standardClassDeclarationTail_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "standardClassDeclarationTail_0")) return false;
    superclass(builder_, level_ + 1);
    return true;
  }

  // mixins?
  private static boolean standardClassDeclarationTail_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "standardClassDeclarationTail_1")) return false;
    mixins(builder_, level_ + 1);
    return true;
  }

  // interfaces?
  private static boolean standardClassDeclarationTail_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "standardClassDeclarationTail_2")) return false;
    interfaces(builder_, level_ + 1);
    return true;
  }

  // ('native' stringLiteralExpression?)?
  private static boolean standardClassDeclarationTail_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "standardClassDeclarationTail_3")) return false;
    standardClassDeclarationTail_3_0(builder_, level_ + 1);
    return true;
  }

  // 'native' stringLiteralExpression?
  private static boolean standardClassDeclarationTail_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "standardClassDeclarationTail_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NATIVE);
    result_ = result_ && standardClassDeclarationTail_3_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // stringLiteralExpression?
  private static boolean standardClassDeclarationTail_3_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "standardClassDeclarationTail_3_0_1")) return false;
    stringLiteralExpression(builder_, level_ + 1);
    return true;
  }

  // classBody?
  private static boolean standardClassDeclarationTail_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "standardClassDeclarationTail_4")) return false;
    classBody(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // label* nonLabelledStatement
  static boolean statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = statement_0(builder_, level_ + 1);
    result_ = result_ && nonLabelledStatement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // label*
  private static boolean statement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!label(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "statement_0", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // (varDeclarationList | expression) ';'
  static boolean statementFollowedBySemiColon(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statementFollowedBySemiColon")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = statementFollowedBySemiColon_0(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && consumeToken(builder_, SEMICOLON);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // varDeclarationList | expression
  private static boolean statementFollowedBySemiColon_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statementFollowedBySemiColon_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = varDeclarationList(builder_, level_ + 1);
    if (!result_) result_ = expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // statement*
  public static boolean statements(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statements")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STATEMENTS, "<statements>");
    while (true) {
      int pos_ = current_position_(builder_);
      if (!statement(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "statements", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // shorthandExpression | 'const' '.' ('new' | referenceExpression) arguments
  static boolean staticMemberShorthandValue(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "staticMemberShorthandValue")) return false;
    if (!nextTokenIs(builder_, "", CONST, DOT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = shorthandExpression(builder_, level_ + 1);
    if (!result_) result_ = staticMemberShorthandValue_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'const' '.' ('new' | referenceExpression) arguments
  private static boolean staticMemberShorthandValue_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "staticMemberShorthandValue_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, CONST, DOT);
    result_ = result_ && staticMemberShorthandValue_1_2(builder_, level_ + 1);
    result_ = result_ && arguments(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'new' | referenceExpression
  private static boolean staticMemberShorthandValue_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "staticMemberShorthandValue_1_2")) return false;
    boolean result_;
    result_ = consumeToken(builder_, NEW);
    if (!result_) result_ = referenceExpression(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // (RAW_SINGLE_QUOTED_STRING | RAW_TRIPLE_QUOTED_STRING | stringTemplate)+
  public static boolean stringLiteralExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stringLiteralExpression")) return false;
    if (!nextTokenIs(builder_, "<string literal expression>", OPEN_QUOTE, RAW_SINGLE_QUOTED_STRING, RAW_TRIPLE_QUOTED_STRING)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STRING_LITERAL_EXPRESSION, "<string literal expression>");
    result_ = stringLiteralExpression_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!stringLiteralExpression_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "stringLiteralExpression", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // RAW_SINGLE_QUOTED_STRING | RAW_TRIPLE_QUOTED_STRING | stringTemplate
  private static boolean stringLiteralExpression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stringLiteralExpression_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, RAW_SINGLE_QUOTED_STRING);
    if (!result_) result_ = consumeToken(builder_, RAW_TRIPLE_QUOTED_STRING);
    if (!result_) result_ = stringTemplate(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // OPEN_QUOTE (REGULAR_STRING_PART | shortTemplateEntry | longTemplateEntry)* CLOSING_QUOTE
  static boolean stringTemplate(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stringTemplate")) return false;
    if (!nextTokenIs(builder_, OPEN_QUOTE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, OPEN_QUOTE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, stringTemplate_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, CLOSING_QUOTE) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (REGULAR_STRING_PART | shortTemplateEntry | longTemplateEntry)*
  private static boolean stringTemplate_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stringTemplate_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!stringTemplate_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "stringTemplate_1", pos_)) break;
    }
    return true;
  }

  // REGULAR_STRING_PART | shortTemplateEntry | longTemplateEntry
  private static boolean stringTemplate_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "stringTemplate_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, REGULAR_STRING_PART);
    if (!result_) result_ = shortTemplateEntry(builder_, level_ + 1);
    if (!result_) result_ = longTemplateEntry(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // '--' | '++'
  public static boolean suffixExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "suffixExpression")) return false;
    if (!nextTokenIs(builder_, "<suffix expression>", MINUS_MINUS, PLUS_PLUS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, SUFFIX_EXPRESSION, "<suffix expression>");
    result_ = consumeToken(builder_, MINUS_MINUS);
    if (!result_) result_ = consumeToken(builder_, PLUS_PLUS);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // valueExpression suffixExpression*
  static boolean suffixExpressionWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "suffixExpressionWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = valueExpression(builder_, level_ + 1);
    result_ = result_ && suffixExpressionWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // suffixExpression*
  private static boolean suffixExpressionWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "suffixExpressionWrapper_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!suffixExpression(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "suffixExpressionWrapper_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // (superExpression | thisExpression) ('.' (referenceExpression | 'new'))? <<argumentsWrapper>>
  //                               | fieldInitializer
  //                               | assertStatement
  public static boolean superCallOrFieldInitializer(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "superCallOrFieldInitializer")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SUPER_CALL_OR_FIELD_INITIALIZER, "<super call or field initializer>");
    result_ = superCallOrFieldInitializer_0(builder_, level_ + 1);
    if (!result_) result_ = fieldInitializer(builder_, level_ + 1);
    if (!result_) result_ = assertStatement(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, DartParser::super_call_or_field_initializer_recover);
    return result_;
  }

  // (superExpression | thisExpression) ('.' (referenceExpression | 'new'))? <<argumentsWrapper>>
  private static boolean superCallOrFieldInitializer_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "superCallOrFieldInitializer_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = superCallOrFieldInitializer_0_0(builder_, level_ + 1);
    result_ = result_ && superCallOrFieldInitializer_0_1(builder_, level_ + 1);
    result_ = result_ && argumentsWrapper(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // superExpression | thisExpression
  private static boolean superCallOrFieldInitializer_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "superCallOrFieldInitializer_0_0")) return false;
    boolean result_;
    result_ = superExpression(builder_, level_ + 1);
    if (!result_) result_ = thisExpression(builder_, level_ + 1);
    return result_;
  }

  // ('.' (referenceExpression | 'new'))?
  private static boolean superCallOrFieldInitializer_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "superCallOrFieldInitializer_0_1")) return false;
    superCallOrFieldInitializer_0_1_0(builder_, level_ + 1);
    return true;
  }

  // '.' (referenceExpression | 'new')
  private static boolean superCallOrFieldInitializer_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "superCallOrFieldInitializer_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && superCallOrFieldInitializer_0_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // referenceExpression | 'new'
  private static boolean superCallOrFieldInitializer_0_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "superCallOrFieldInitializer_0_1_0_1")) return false;
    boolean result_;
    result_ = referenceExpression(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, NEW);
    return result_;
  }

  /* ********************************************************** */
  // 'super'
  public static boolean superExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "superExpression")) return false;
    if (!nextTokenIs(builder_, SUPER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SUPER);
    exit_section_(builder_, marker_, SUPER_EXPRESSION, result_);
    return result_;
  }

  /* ********************************************************** */
  // !(<<nonStrictID>> | 'sync' | 'async' | '=>' | '{' | 'operator' |
  //                                                     '(' | ',' | ':' | ';' | '@' | 'abstract' | 'base' | 'class' | 'const' | 'covariant' |
  //                                                     'enum' | 'export' | 'extension' | 'external' | 'factory' | 'final' | 'get' | 'import' |
  //                                                     'interface' | 'late' | 'library' | 'mixin' | 'native' | 'part' | 'sealed' | 'set' |
  //                                                     'static' | 'typedef' | 'var' | 'void' | '}' )
  static boolean super_call_or_field_initializer_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "super_call_or_field_initializer_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !super_call_or_field_initializer_recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // <<nonStrictID>> | 'sync' | 'async' | '=>' | '{' | 'operator' |
  //                                                     '(' | ',' | ':' | ';' | '@' | 'abstract' | 'base' | 'class' | 'const' | 'covariant' |
  //                                                     'enum' | 'export' | 'extension' | 'external' | 'factory' | 'final' | 'get' | 'import' |
  //                                                     'interface' | 'late' | 'library' | 'mixin' | 'native' | 'part' | 'sealed' | 'set' |
  //                                                     'static' | 'typedef' | 'var' | 'void' | '}'
  private static boolean super_call_or_field_initializer_recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "super_call_or_field_initializer_recover_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nonStrictID(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, SYNC);
    if (!result_) result_ = consumeToken(builder_, ASYNC);
    if (!result_) result_ = consumeToken(builder_, EXPRESSION_BODY_DEF);
    if (!result_) result_ = consumeToken(builder_, LBRACE);
    if (!result_) result_ = consumeToken(builder_, OPERATOR);
    if (!result_) result_ = consumeToken(builder_, LPAREN);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = consumeToken(builder_, COLON);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = consumeToken(builder_, AT);
    if (!result_) result_ = consumeToken(builder_, ABSTRACT);
    if (!result_) result_ = consumeToken(builder_, BASE);
    if (!result_) result_ = consumeToken(builder_, CLASS);
    if (!result_) result_ = consumeToken(builder_, CONST);
    if (!result_) result_ = consumeToken(builder_, COVARIANT);
    if (!result_) result_ = consumeToken(builder_, ENUM);
    if (!result_) result_ = consumeToken(builder_, EXPORT);
    if (!result_) result_ = consumeToken(builder_, EXTENSION);
    if (!result_) result_ = consumeToken(builder_, EXTERNAL);
    if (!result_) result_ = consumeToken(builder_, FACTORY);
    if (!result_) result_ = consumeToken(builder_, FINAL);
    if (!result_) result_ = consumeToken(builder_, GET);
    if (!result_) result_ = consumeToken(builder_, IMPORT);
    if (!result_) result_ = consumeToken(builder_, INTERFACE);
    if (!result_) result_ = consumeToken(builder_, LATE);
    if (!result_) result_ = consumeToken(builder_, LIBRARY);
    if (!result_) result_ = consumeToken(builder_, MIXIN);
    if (!result_) result_ = consumeToken(builder_, NATIVE);
    if (!result_) result_ = consumeToken(builder_, PART);
    if (!result_) result_ = consumeToken(builder_, SEALED);
    if (!result_) result_ = consumeToken(builder_, SET);
    if (!result_) result_ = consumeToken(builder_, STATIC);
    if (!result_) result_ = consumeToken(builder_, TYPEDEF);
    if (!result_) result_ = consumeToken(builder_, VAR);
    if (!result_) result_ = consumeToken(builder_, VOID);
    if (!result_) result_ = consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'extends' type
  public static boolean superclass(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "superclass")) return false;
    if (!nextTokenIs(builder_, EXTENDS)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SUPERCLASS, null);
    result_ = consumeToken(builder_, EXTENDS);
    pinned_ = result_; // pin = 1
    result_ = result_ && type(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // label* (patternCase | expressionCase) statements
  public static boolean switchCase(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchCase")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SWITCH_CASE, "<switch case>");
    result_ = switchCase_0(builder_, level_ + 1);
    result_ = result_ && switchCase_1(builder_, level_ + 1);
    result_ = result_ && statements(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // label*
  private static boolean switchCase_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchCase_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!label(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "switchCase_0", pos_)) break;
    }
    return true;
  }

  // patternCase | expressionCase
  private static boolean switchCase_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchCase_1")) return false;
    boolean result_;
    result_ = patternCase(builder_, level_ + 1);
    if (!result_) result_ = expressionCase(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // '{' switchExpressionCase (',' switchExpressionCase)* ','? recoverUntilRBrace '}'
  public static boolean switchExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchExpression")) return false;
    if (!nextTokenIs(builder_, LBRACE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _UPPER_, SWITCH_EXPRESSION, null);
    result_ = consumeToken(builder_, LBRACE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, switchExpressionCase(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, switchExpression_2(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, switchExpression_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, recoverUntilRBrace(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, RBRACE) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (',' switchExpressionCase)*
  private static boolean switchExpression_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchExpression_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!switchExpression_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "switchExpression_2", pos_)) break;
    }
    return true;
  }

  // ',' switchExpressionCase
  private static boolean switchExpression_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchExpression_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && switchExpressionCase(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ','?
  private static boolean switchExpression_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchExpression_3")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // guardedPattern '=>' expression
  public static boolean switchExpressionCase(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchExpressionCase")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SWITCH_EXPRESSION_CASE, "<switch expression case>");
    result_ = guardedPattern(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EXPRESSION_BODY_DEF);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // 'switch' '(' expressionWithRecoverUntilParen ')' switchExpression
  public static boolean switchExpressionWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchExpressionWrapper")) return false;
    if (!nextTokenIs(builder_, SWITCH)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SWITCH_EXPRESSION_WRAPPER, null);
    result_ = consumeTokens(builder_, 1, SWITCH, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expressionWithRecoverUntilParen(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, RPAREN)) && result_;
    result_ = pinned_ && switchExpression(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // '{' switchCase* defaultCase? '}'
  public static boolean switchStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchStatement")) return false;
    if (!nextTokenIs(builder_, LBRACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _UPPER_, SWITCH_STATEMENT, null);
    result_ = consumeToken(builder_, LBRACE);
    result_ = result_ && switchStatement_1(builder_, level_ + 1);
    result_ = result_ && switchStatement_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // switchCase*
  private static boolean switchStatement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchStatement_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!switchCase(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "switchStatement_1", pos_)) break;
    }
    return true;
  }

  // defaultCase?
  private static boolean switchStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchStatement_2")) return false;
    defaultCase(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // 'switch' '(' expressionWithRecoverUntilParen ')' (switchStatement | switchExpression)
  public static boolean switchStatementOrExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchStatementOrExpression")) return false;
    if (!nextTokenIs(builder_, SWITCH)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SWITCH_STATEMENT_OR_EXPRESSION, null);
    result_ = consumeTokens(builder_, 1, SWITCH, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expressionWithRecoverUntilParen(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, RPAREN)) && result_;
    result_ = pinned_ && switchStatementOrExpression_4(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // switchStatement | switchExpression
  private static boolean switchStatementOrExpression_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "switchStatementOrExpression_4")) return false;
    boolean result_;
    result_ = switchStatement(builder_, level_ + 1);
    if (!result_) result_ = switchExpression(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // '#' ('void' | userDefinableOperator | simpleQualifiedReferenceExpression)
  public static boolean symbolLiteralExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "symbolLiteralExpression")) return false;
    if (!nextTokenIs(builder_, HASH)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SYMBOL_LITERAL_EXPRESSION, null);
    result_ = consumeToken(builder_, HASH);
    pinned_ = result_; // pin = 1
    result_ = result_ && symbolLiteralExpression_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // 'void' | userDefinableOperator | simpleQualifiedReferenceExpression
  private static boolean symbolLiteralExpression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "symbolLiteralExpression_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, VOID);
    if (!result_) result_ = userDefinableOperator(builder_, level_ + 1);
    if (!result_) result_ = simpleQualifiedReferenceExpression(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // '?' expression ':' ternaryExpressionWrapper
  public static boolean ternaryExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ternaryExpression")) return false;
    if (!nextTokenIs(builder_, QUEST)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, TERNARY_EXPRESSION, null);
    result_ = consumeToken(builder_, QUEST);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expression(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, COLON)) && result_;
    result_ = pinned_ && ternaryExpressionWrapper(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // ifNullExpressionWrapper ternaryExpression?
  static boolean ternaryExpressionWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ternaryExpressionWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = ifNullExpressionWrapper(builder_, level_ + 1);
    result_ = result_ && ternaryExpressionWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ternaryExpression?
  private static boolean ternaryExpressionWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ternaryExpressionWrapper_1")) return false;
    ternaryExpression(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // 'this'
  public static boolean thisExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "thisExpression")) return false;
    if (!nextTokenIs(builder_, THIS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, THIS);
    exit_section_(builder_, marker_, THIS_EXPRESSION, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'throw' expression
  public static boolean throwExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "throwExpression")) return false;
    if (!nextTokenIs(builder_, THROW)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, THROW);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, THROW_EXPRESSION, result_);
    return result_;
  }

  /* ********************************************************** */
  // libraryStatement
  //                              | partOfStatement
  //                              | importStatement
  //                              | exportStatement
  //                              | partStatement
  //                              | classDefinition
  //                              | mixinDeclaration
  //                              | enumDefinition
  //                              | extensionTypeDeclaration
  //                              | extensionDeclaration
  //                              | functionTypeAlias
  //                              | getterOrSetterDeclaration
  //                              | functionDeclarationWithBodyOrNative
  //                              | varDeclarationListWithSemicolon
  //                              | incompleteDeclaration
  static boolean topLevelDefinition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "topLevelDefinition")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = libraryStatement(builder_, level_ + 1);
    if (!result_) result_ = partOfStatement(builder_, level_ + 1);
    if (!result_) result_ = importStatement(builder_, level_ + 1);
    if (!result_) result_ = exportStatement(builder_, level_ + 1);
    if (!result_) result_ = partStatement(builder_, level_ + 1);
    if (!result_) result_ = classDefinition(builder_, level_ + 1);
    if (!result_) result_ = mixinDeclaration(builder_, level_ + 1);
    if (!result_) result_ = enumDefinition(builder_, level_ + 1);
    if (!result_) result_ = extensionTypeDeclaration(builder_, level_ + 1);
    if (!result_) result_ = extensionDeclaration(builder_, level_ + 1);
    if (!result_) result_ = functionTypeAlias(builder_, level_ + 1);
    if (!result_) result_ = getterOrSetterDeclaration(builder_, level_ + 1);
    if (!result_) result_ = functionDeclarationWithBodyOrNative(builder_, level_ + 1);
    if (!result_) result_ = varDeclarationListWithSemicolon(builder_, level_ + 1);
    if (!result_) result_ = incompleteDeclaration(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, DartParser::top_level_recover);
    return result_;
  }

  /* ********************************************************** */
  // !(<<nonStrictID>> |
  //                               '(' | '@' | 'abstract' | 'base' | 'class' | 'const' | 'covariant' | 'enum' | 'export' | 'extension' |
  //                               'external' | 'final' | 'get' | 'import' | 'interface' | 'late' | 'library' | 'mixin' | 'part' | 'sealed' |
  //                               'set' | 'static' | 'typedef' | 'var' | 'void')
  static boolean top_level_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "top_level_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !top_level_recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // <<nonStrictID>> |
  //                               '(' | '@' | 'abstract' | 'base' | 'class' | 'const' | 'covariant' | 'enum' | 'export' | 'extension' |
  //                               'external' | 'final' | 'get' | 'import' | 'interface' | 'late' | 'library' | 'mixin' | 'part' | 'sealed' |
  //                               'set' | 'static' | 'typedef' | 'var' | 'void'
  private static boolean top_level_recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "top_level_recover_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nonStrictID(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, LPAREN);
    if (!result_) result_ = consumeToken(builder_, AT);
    if (!result_) result_ = consumeToken(builder_, ABSTRACT);
    if (!result_) result_ = consumeToken(builder_, BASE);
    if (!result_) result_ = consumeToken(builder_, CLASS);
    if (!result_) result_ = consumeToken(builder_, CONST);
    if (!result_) result_ = consumeToken(builder_, COVARIANT);
    if (!result_) result_ = consumeToken(builder_, ENUM);
    if (!result_) result_ = consumeToken(builder_, EXPORT);
    if (!result_) result_ = consumeToken(builder_, EXTENSION);
    if (!result_) result_ = consumeToken(builder_, EXTERNAL);
    if (!result_) result_ = consumeToken(builder_, FINAL);
    if (!result_) result_ = consumeToken(builder_, GET);
    if (!result_) result_ = consumeToken(builder_, IMPORT);
    if (!result_) result_ = consumeToken(builder_, INTERFACE);
    if (!result_) result_ = consumeToken(builder_, LATE);
    if (!result_) result_ = consumeToken(builder_, LIBRARY);
    if (!result_) result_ = consumeToken(builder_, MIXIN);
    if (!result_) result_ = consumeToken(builder_, PART);
    if (!result_) result_ = consumeToken(builder_, SEALED);
    if (!result_) result_ = consumeToken(builder_, SET);
    if (!result_) result_ = consumeToken(builder_, STATIC);
    if (!result_) result_ = consumeToken(builder_, TYPEDEF);
    if (!result_) result_ = consumeToken(builder_, VAR);
    if (!result_) result_ = consumeToken(builder_, VOID);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'try' block (onPart+ finallyPart? | finallyPart)
  public static boolean tryStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tryStatement")) return false;
    if (!nextTokenIs(builder_, TRY)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TRY_STATEMENT, null);
    result_ = consumeToken(builder_, TRY);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, block(builder_, level_ + 1));
    result_ = pinned_ && tryStatement_2(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // onPart+ finallyPart? | finallyPart
  private static boolean tryStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tryStatement_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = tryStatement_2_0(builder_, level_ + 1);
    if (!result_) result_ = finallyPart(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // onPart+ finallyPart?
  private static boolean tryStatement_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tryStatement_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = tryStatement_2_0_0(builder_, level_ + 1);
    result_ = result_ && tryStatement_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // onPart+
  private static boolean tryStatement_2_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tryStatement_2_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = onPart(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!onPart(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "tryStatement_2_0_0", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // finallyPart?
  private static boolean tryStatement_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tryStatement_2_0_1")) return false;
    finallyPart(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // 'void' !untypedFunctionType | functionTypeWrapper | simpleType | recordType
  public static boolean type(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TYPE, "<type>");
    result_ = type_0(builder_, level_ + 1);
    if (!result_) result_ = functionTypeWrapper(builder_, level_ + 1);
    if (!result_) result_ = simpleType(builder_, level_ + 1);
    if (!result_) result_ = recordType(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // 'void' !untypedFunctionType
  private static boolean type_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, VOID);
    result_ = result_ && type_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !untypedFunctionType
  private static boolean type_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !untypedFunctionType(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // '<' typeList '>'
  public static boolean typeArguments(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeArguments")) return false;
    if (!nextTokenIs(builder_, LT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LT);
    result_ = result_ && typeList(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, GT);
    exit_section_(builder_, marker_, TYPE_ARGUMENTS, result_);
    return result_;
  }

  /* ********************************************************** */
  // type? (',' type)*
  public static boolean typeList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TYPE_LIST, "<type list>");
    result_ = typeList_0(builder_, level_ + 1);
    result_ = result_ && typeList_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // type?
  private static boolean typeList_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeList_0")) return false;
    type(builder_, level_ + 1);
    return true;
  }

  // (',' type)*
  private static boolean typeList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeList_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!typeList_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "typeList_1", pos_)) break;
    }
    return true;
  }

  // ',' type
  private static boolean typeList_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeList_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && type(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // metadata* componentName ('extends' type)?
  public static boolean typeParameter(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeParameter")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TYPE_PARAMETER, "<type parameter>");
    result_ = typeParameter_0(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && typeParameter_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, DartParser::type_parameter_recover);
    return result_;
  }

  // metadata*
  private static boolean typeParameter_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeParameter_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "typeParameter_0", pos_)) break;
    }
    return true;
  }

  // ('extends' type)?
  private static boolean typeParameter_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeParameter_2")) return false;
    typeParameter_2_0(builder_, level_ + 1);
    return true;
  }

  // 'extends' type
  private static boolean typeParameter_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeParameter_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, EXTENDS);
    result_ = result_ && type(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // '<' typeParameter? (',' typeParameter)* '>'
  public static boolean typeParameters(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeParameters")) return false;
    if (!nextTokenIs(builder_, LT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LT);
    result_ = result_ && typeParameters_1(builder_, level_ + 1);
    result_ = result_ && typeParameters_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, GT);
    exit_section_(builder_, marker_, TYPE_PARAMETERS, result_);
    return result_;
  }

  // typeParameter?
  private static boolean typeParameters_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeParameters_1")) return false;
    typeParameter(builder_, level_ + 1);
    return true;
  }

  // (',' typeParameter)*
  private static boolean typeParameters_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeParameters_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!typeParameters_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "typeParameters_2", pos_)) break;
    }
    return true;
  }

  // ',' typeParameter
  private static boolean typeParameters_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeParameters_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && typeParameter(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // !(<<nonStrictID>> | '(' | ')' | ',' | ':' | '=' | '>' | '@' | ']' | 'abstract' | 'base' | 'class' |
  //                                    'const' | 'covariant' | 'enum' | 'export' | 'extends' | 'extension' | 'external' | 'final' | 'get' |
  //                                    'implements' | 'import' | 'interface' | 'late' | 'library' | 'mixin' | 'native' | 'on' | 'part' |
  //                                    'sealed' | 'set' | 'static' | 'typedef' | 'var' | 'void' | 'with' | '{' | '}')
  static boolean type_parameter_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_parameter_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !type_parameter_recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // <<nonStrictID>> | '(' | ')' | ',' | ':' | '=' | '>' | '@' | ']' | 'abstract' | 'base' | 'class' |
  //                                    'const' | 'covariant' | 'enum' | 'export' | 'extends' | 'extension' | 'external' | 'final' | 'get' |
  //                                    'implements' | 'import' | 'interface' | 'late' | 'library' | 'mixin' | 'native' | 'on' | 'part' |
  //                                    'sealed' | 'set' | 'static' | 'typedef' | 'var' | 'void' | 'with' | '{' | '}'
  private static boolean type_parameter_recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_parameter_recover_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nonStrictID(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, LPAREN);
    if (!result_) result_ = consumeToken(builder_, RPAREN);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = consumeToken(builder_, COLON);
    if (!result_) result_ = consumeToken(builder_, EQ);
    if (!result_) result_ = consumeToken(builder_, GT);
    if (!result_) result_ = consumeToken(builder_, AT);
    if (!result_) result_ = consumeToken(builder_, RBRACKET);
    if (!result_) result_ = consumeToken(builder_, ABSTRACT);
    if (!result_) result_ = consumeToken(builder_, BASE);
    if (!result_) result_ = consumeToken(builder_, CLASS);
    if (!result_) result_ = consumeToken(builder_, CONST);
    if (!result_) result_ = consumeToken(builder_, COVARIANT);
    if (!result_) result_ = consumeToken(builder_, ENUM);
    if (!result_) result_ = consumeToken(builder_, EXPORT);
    if (!result_) result_ = consumeToken(builder_, EXTENDS);
    if (!result_) result_ = consumeToken(builder_, EXTENSION);
    if (!result_) result_ = consumeToken(builder_, EXTERNAL);
    if (!result_) result_ = consumeToken(builder_, FINAL);
    if (!result_) result_ = consumeToken(builder_, GET);
    if (!result_) result_ = consumeToken(builder_, IMPLEMENTS);
    if (!result_) result_ = consumeToken(builder_, IMPORT);
    if (!result_) result_ = consumeToken(builder_, INTERFACE);
    if (!result_) result_ = consumeToken(builder_, LATE);
    if (!result_) result_ = consumeToken(builder_, LIBRARY);
    if (!result_) result_ = consumeToken(builder_, MIXIN);
    if (!result_) result_ = consumeToken(builder_, NATIVE);
    if (!result_) result_ = consumeToken(builder_, ON);
    if (!result_) result_ = consumeToken(builder_, PART);
    if (!result_) result_ = consumeToken(builder_, SEALED);
    if (!result_) result_ = consumeToken(builder_, SET);
    if (!result_) result_ = consumeToken(builder_, STATIC);
    if (!result_) result_ = consumeToken(builder_, TYPEDEF);
    if (!result_) result_ = consumeToken(builder_, VAR);
    if (!result_) result_ = consumeToken(builder_, VOID);
    if (!result_) result_ = consumeToken(builder_, WITH);
    if (!result_) result_ = consumeToken(builder_, LBRACE);
    if (!result_) result_ = consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // <<functionId>> typeParameters? parameterTypeList ('?' !(expression ':'))?
  public static boolean typedFunctionType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typedFunctionType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, TYPED_FUNCTION_TYPE, "<typed function type>");
    result_ = functionId(builder_, level_ + 1);
    result_ = result_ && typedFunctionType_1(builder_, level_ + 1);
    result_ = result_ && parameterTypeList(builder_, level_ + 1);
    result_ = result_ && typedFunctionType_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // typeParameters?
  private static boolean typedFunctionType_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typedFunctionType_1")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // ('?' !(expression ':'))?
  private static boolean typedFunctionType_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typedFunctionType_3")) return false;
    typedFunctionType_3_0(builder_, level_ + 1);
    return true;
  }

  // '?' !(expression ':')
  private static boolean typedFunctionType_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typedFunctionType_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, QUEST);
    result_ = result_ && typedFunctionType_3_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !(expression ':')
  private static boolean typedFunctionType_3_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typedFunctionType_3_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !typedFunctionType_3_0_1_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // expression ':'
  private static boolean typedFunctionType_3_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typedFunctionType_3_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // type <<nonStrictID>>
  static boolean typedIdentifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typedIdentifier")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = type(builder_, level_ + 1);
    result_ = result_ && nonStrictID(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'as' type | '?' | '!'
  public static boolean unaryPattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unaryPattern")) return false;
    if (!nextTokenIs(builder_, "<unary pattern>", AS, NOT, QUEST)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _LEFT_, UNARY_PATTERN, "<unary pattern>");
    result_ = unaryPattern_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, QUEST);
    if (!result_) result_ = consumeToken(builder_, NOT);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // 'as' type
  private static boolean unaryPattern_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unaryPattern_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AS);
    result_ = result_ && type(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // primaryPattern unaryPattern?
  static boolean unaryPatternWrapper(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unaryPatternWrapper")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = primaryPattern(builder_, level_ + 1);
    result_ = result_ && unaryPatternWrapper_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // unaryPattern?
  private static boolean unaryPatternWrapper_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unaryPatternWrapper_1")) return false;
    unaryPattern(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // <<functionId>> typeParameters? parameterTypeList ('?' !(expression ':'))?
  public static boolean untypedFunctionType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "untypedFunctionType")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, UNTYPED_FUNCTION_TYPE, "<untyped function type>");
    result_ = functionId(builder_, level_ + 1);
    result_ = result_ && untypedFunctionType_1(builder_, level_ + 1);
    result_ = result_ && parameterTypeList(builder_, level_ + 1);
    result_ = result_ && untypedFunctionType_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // typeParameters?
  private static boolean untypedFunctionType_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "untypedFunctionType_1")) return false;
    typeParameters(builder_, level_ + 1);
    return true;
  }

  // ('?' !(expression ':'))?
  private static boolean untypedFunctionType_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "untypedFunctionType_3")) return false;
    untypedFunctionType_3_0(builder_, level_ + 1);
    return true;
  }

  // '?' !(expression ':')
  private static boolean untypedFunctionType_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "untypedFunctionType_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, QUEST);
    result_ = result_ && untypedFunctionType_3_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !(expression ':')
  private static boolean untypedFunctionType_3_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "untypedFunctionType_3_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !untypedFunctionType_3_0_1_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // expression ':'
  private static boolean untypedFunctionType_3_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "untypedFunctionType_3_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // stringLiteralExpression
  public static boolean uriElement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "uriElement")) return false;
    if (!nextTokenIs(builder_, "<uri element>", OPEN_QUOTE, RAW_SINGLE_QUOTED_STRING, RAW_TRIPLE_QUOTED_STRING)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, URI_ELEMENT, "<uri element>");
    result_ = stringLiteralExpression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // binaryOperator |
  //                           '~' |
  //                           '[' ']' '='?
  public static boolean userDefinableOperator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "userDefinableOperator")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, USER_DEFINABLE_OPERATOR, "<user definable operator>");
    result_ = binaryOperator(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, BIN_NOT);
    if (!result_) result_ = userDefinableOperator_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // '[' ']' '='?
  private static boolean userDefinableOperator_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "userDefinableOperator_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, LBRACKET, RBRACKET);
    result_ = result_ && userDefinableOperator_2_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // '='?
  private static boolean userDefinableOperator_2_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "userDefinableOperator_2_2")) return false;
    consumeToken(builder_, EQ);
    return true;
  }

  /* ********************************************************** */
  // primary callOrArrayAccessOrQualifiedRefExpression (isExpression | asExpression)? cascadeReferenceExpression*
  public static boolean valueExpression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "valueExpression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, VALUE_EXPRESSION, "<value expression>");
    result_ = primary(builder_, level_ + 1);
    result_ = result_ && callOrArrayAccessOrQualifiedRefExpression(builder_, level_ + 1);
    result_ = result_ && valueExpression_2(builder_, level_ + 1);
    result_ = result_ && valueExpression_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (isExpression | asExpression)?
  private static boolean valueExpression_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "valueExpression_2")) return false;
    valueExpression_2_0(builder_, level_ + 1);
    return true;
  }

  // isExpression | asExpression
  private static boolean valueExpression_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "valueExpression_2_0")) return false;
    boolean result_;
    result_ = isExpression(builder_, level_ + 1);
    if (!result_) result_ = asExpression(builder_, level_ + 1);
    return result_;
  }

  // cascadeReferenceExpression*
  private static boolean valueExpression_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "valueExpression_3")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!cascadeReferenceExpression(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "valueExpression_3", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // metadata* 'augment'? 'external'? 'abstract'? 'static'?
  //                                              ('covariant'? 'late'? finalOrConst type               componentName |
  //                                               'covariant'? 'late'? finalOrConst                    componentName <<failIfItLooksLikeConstantObjectExpression>> |
  //                                               'covariant'? 'late'? 'var'                           componentName |
  //                                               'covariant'  'late'               type !asExpression componentName |
  //                                               'covariant'                       type !asExpression componentName |
  //                                                            'late'               type !asExpression componentName |
  //                                                                                 type !asExpression componentName) !'.' !':'
  public static boolean varAccessDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VAR_ACCESS_DECLARATION, "<var access declaration>");
    result_ = varAccessDeclaration_0(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_1(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_2(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_3(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_4(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_5(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_6(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_7(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // metadata*
  private static boolean varAccessDeclaration_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!metadata(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "varAccessDeclaration_0", pos_)) break;
    }
    return true;
  }

  // 'augment'?
  private static boolean varAccessDeclaration_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_1")) return false;
    consumeToken(builder_, AUGMENT);
    return true;
  }

  // 'external'?
  private static boolean varAccessDeclaration_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_2")) return false;
    consumeToken(builder_, EXTERNAL);
    return true;
  }

  // 'abstract'?
  private static boolean varAccessDeclaration_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_3")) return false;
    consumeToken(builder_, ABSTRACT);
    return true;
  }

  // 'static'?
  private static boolean varAccessDeclaration_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_4")) return false;
    consumeToken(builder_, STATIC);
    return true;
  }

  // 'covariant'? 'late'? finalOrConst type               componentName |
  //                                               'covariant'? 'late'? finalOrConst                    componentName <<failIfItLooksLikeConstantObjectExpression>> |
  //                                               'covariant'? 'late'? 'var'                           componentName |
  //                                               'covariant'  'late'               type !asExpression componentName |
  //                                               'covariant'                       type !asExpression componentName |
  //                                                            'late'               type !asExpression componentName |
  //                                                                                 type !asExpression componentName
  private static boolean varAccessDeclaration_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = varAccessDeclaration_5_0(builder_, level_ + 1);
    if (!result_) result_ = varAccessDeclaration_5_1(builder_, level_ + 1);
    if (!result_) result_ = varAccessDeclaration_5_2(builder_, level_ + 1);
    if (!result_) result_ = varAccessDeclaration_5_3(builder_, level_ + 1);
    if (!result_) result_ = varAccessDeclaration_5_4(builder_, level_ + 1);
    if (!result_) result_ = varAccessDeclaration_5_5(builder_, level_ + 1);
    if (!result_) result_ = varAccessDeclaration_5_6(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'covariant'? 'late'? finalOrConst type               componentName
  private static boolean varAccessDeclaration_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = varAccessDeclaration_5_0_0(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_5_0_1(builder_, level_ + 1);
    result_ = result_ && finalOrConst(builder_, level_ + 1);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'covariant'?
  private static boolean varAccessDeclaration_5_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_0_0")) return false;
    consumeToken(builder_, COVARIANT);
    return true;
  }

  // 'late'?
  private static boolean varAccessDeclaration_5_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_0_1")) return false;
    consumeToken(builder_, LATE);
    return true;
  }

  // 'covariant'? 'late'? finalOrConst                    componentName <<failIfItLooksLikeConstantObjectExpression>>
  private static boolean varAccessDeclaration_5_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = varAccessDeclaration_5_1_0(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_5_1_1(builder_, level_ + 1);
    result_ = result_ && finalOrConst(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    result_ = result_ && failIfItLooksLikeConstantObjectExpression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'covariant'?
  private static boolean varAccessDeclaration_5_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_1_0")) return false;
    consumeToken(builder_, COVARIANT);
    return true;
  }

  // 'late'?
  private static boolean varAccessDeclaration_5_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_1_1")) return false;
    consumeToken(builder_, LATE);
    return true;
  }

  // 'covariant'? 'late'? 'var'                           componentName
  private static boolean varAccessDeclaration_5_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = varAccessDeclaration_5_2_0(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_5_2_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, VAR);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'covariant'?
  private static boolean varAccessDeclaration_5_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_2_0")) return false;
    consumeToken(builder_, COVARIANT);
    return true;
  }

  // 'late'?
  private static boolean varAccessDeclaration_5_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_2_1")) return false;
    consumeToken(builder_, LATE);
    return true;
  }

  // 'covariant'  'late'               type !asExpression componentName
  private static boolean varAccessDeclaration_5_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COVARIANT, LATE);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_5_3_3(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !asExpression
  private static boolean varAccessDeclaration_5_3_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_3_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !asExpression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // 'covariant'                       type !asExpression componentName
  private static boolean varAccessDeclaration_5_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COVARIANT);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_5_4_2(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !asExpression
  private static boolean varAccessDeclaration_5_4_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_4_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !asExpression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // 'late'               type !asExpression componentName
  private static boolean varAccessDeclaration_5_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LATE);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_5_5_2(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !asExpression
  private static boolean varAccessDeclaration_5_5_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_5_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !asExpression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // type !asExpression componentName
  private static boolean varAccessDeclaration_5_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_6")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = type(builder_, level_ + 1);
    result_ = result_ && varAccessDeclaration_5_6_1(builder_, level_ + 1);
    result_ = result_ && componentName(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !asExpression
  private static boolean varAccessDeclaration_5_6_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_5_6_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !asExpression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // !'.'
  private static boolean varAccessDeclaration_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_6")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !consumeToken(builder_, DOT);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // !':'
  private static boolean varAccessDeclaration_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varAccessDeclaration_7")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !consumeToken(builder_, COLON);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // varAccessDeclaration varInit? (',' varDeclarationListPart)*
  public static boolean varDeclarationList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varDeclarationList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VAR_DECLARATION_LIST, "<var declaration list>");
    result_ = varAccessDeclaration(builder_, level_ + 1);
    result_ = result_ && varDeclarationList_1(builder_, level_ + 1);
    result_ = result_ && varDeclarationList_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // varInit?
  private static boolean varDeclarationList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varDeclarationList_1")) return false;
    varInit(builder_, level_ + 1);
    return true;
  }

  // (',' varDeclarationListPart)*
  private static boolean varDeclarationList_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varDeclarationList_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!varDeclarationList_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "varDeclarationList_2", pos_)) break;
    }
    return true;
  }

  // ',' varDeclarationListPart
  private static boolean varDeclarationList_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varDeclarationList_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && varDeclarationListPart(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // componentName varInit?
  public static boolean varDeclarationListPart(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varDeclarationListPart")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VAR_DECLARATION_LIST_PART, "<var declaration list part>");
    result_ = componentName(builder_, level_ + 1);
    result_ = result_ && varDeclarationListPart_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // varInit?
  private static boolean varDeclarationListPart_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varDeclarationListPart_1")) return false;
    varInit(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // varDeclarationList ';'
  static boolean varDeclarationListWithSemicolon(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varDeclarationListWithSemicolon")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = varDeclarationList(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && consumeToken(builder_, SEMICOLON);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // '=' type ['.' (referenceExpression | 'new')]
  static boolean varFactoryDeclaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varFactoryDeclaration")) return false;
    if (!nextTokenIs(builder_, EQ)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, EQ);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, type(builder_, level_ + 1));
    result_ = pinned_ && varFactoryDeclaration_2(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // ['.' (referenceExpression | 'new')]
  private static boolean varFactoryDeclaration_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varFactoryDeclaration_2")) return false;
    varFactoryDeclaration_2_0(builder_, level_ + 1);
    return true;
  }

  // '.' (referenceExpression | 'new')
  private static boolean varFactoryDeclaration_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varFactoryDeclaration_2_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, DOT);
    pinned_ = result_; // pin = 1
    result_ = result_ && varFactoryDeclaration_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // referenceExpression | 'new'
  private static boolean varFactoryDeclaration_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varFactoryDeclaration_2_0_1")) return false;
    boolean result_;
    result_ = referenceExpression(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, NEW);
    return result_;
  }

  /* ********************************************************** */
  // '=' expression
  public static boolean varInit(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "varInit")) return false;
    if (!nextTokenIs(builder_, EQ)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VAR_INIT, null);
    result_ = consumeToken(builder_, EQ);
    pinned_ = result_; // pin = 1
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // 'final' type !('when' expression) referenceExpression | 'final' referenceExpression | 'var' referenceExpression | type !'as' referenceExpression
  public static boolean variablePattern(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "variablePattern")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VARIABLE_PATTERN, "<variable pattern>");
    result_ = variablePattern_0(builder_, level_ + 1);
    if (!result_) result_ = variablePattern_1(builder_, level_ + 1);
    if (!result_) result_ = variablePattern_2(builder_, level_ + 1);
    if (!result_) result_ = variablePattern_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // 'final' type !('when' expression) referenceExpression
  private static boolean variablePattern_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "variablePattern_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, FINAL);
    result_ = result_ && type(builder_, level_ + 1);
    result_ = result_ && variablePattern_0_2(builder_, level_ + 1);
    result_ = result_ && referenceExpression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !('when' expression)
  private static boolean variablePattern_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "variablePattern_0_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !variablePattern_0_2_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // 'when' expression
  private static boolean variablePattern_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "variablePattern_0_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, WHEN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'final' referenceExpression
  private static boolean variablePattern_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "variablePattern_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, FINAL);
    result_ = result_ && referenceExpression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // 'var' referenceExpression
  private static boolean variablePattern_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "variablePattern_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, VAR);
    result_ = result_ && referenceExpression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // type !'as' referenceExpression
  private static boolean variablePattern_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "variablePattern_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = type(builder_, level_ + 1);
    result_ = result_ && variablePattern_3_1(builder_, level_ + 1);
    result_ = result_ && referenceExpression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // !'as'
  private static boolean variablePattern_3_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "variablePattern_3_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !consumeToken(builder_, AS);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // 'void'
  public static boolean voidTypeFunctionType(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "voidTypeFunctionType")) return false;
    if (!nextTokenIs(builder_, VOID)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, VOID);
    exit_section_(builder_, marker_, VOID_TYPE_FUNCTION_TYPE, result_);
    return result_;
  }

  /* ********************************************************** */
  // 'while' '(' expressionWithRecoverUntilParen ')' statement
  public static boolean whileStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "whileStatement")) return false;
    if (!nextTokenIs(builder_, WHILE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, WHILE_STATEMENT, null);
    result_ = consumeTokens(builder_, 1, WHILE, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expressionWithRecoverUntilParen(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, RPAREN)) && result_;
    result_ = pinned_ && statement(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // 'yield' '*' expression ';'
  public static boolean yieldEachStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yieldEachStatement")) return false;
    if (!nextTokenIs(builder_, YIELD)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, YIELD_EACH_STATEMENT, null);
    result_ = consumeTokens(builder_, 2, YIELD, MUL);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, expression(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, SEMICOLON) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // 'yield' expression ';'
  public static boolean yieldStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yieldStatement")) return false;
    if (!nextTokenIs(builder_, YIELD)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, YIELD_STATEMENT, null);
    result_ = consumeToken(builder_, YIELD);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expression(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, SEMICOLON) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

}
