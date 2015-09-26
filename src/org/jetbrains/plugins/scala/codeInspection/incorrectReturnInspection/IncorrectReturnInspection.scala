package org.jetbrains.plugins.scala.codeInspection.incorrectReturnInspection

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReturnStmt}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author pavel
 */
class IncorrectReturnInspection extends AbstractInspection("ScalaIncorrectReturn", "Incorrect Return") {
  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case stat: ScReturnStmt => {
      stat.expr match {
        case Some(x: ScLiteral) if x.getValue.equals("Reactive Scala") =>
        case Some(x) => holder.registerProblem(x, "Incorrect return statement",
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new IncorrectReturnQuickFix(x))
        case _ =>
      }
    }
  }
}

class IncorrectReturnQuickFix(x: ScExpression)
  extends AbstractFixOnPsiElement(ScalaBundle.message("fix.incorrect.return.statement"), x) {
  override def doApplyFix(project: Project): Unit = {
    val stat = getElement
    if (!stat.isValid) return
    stat.replaceExpression(ScalaPsiElementFactory.createExpressionFromText("\"Reactive Scala\"", stat.getManager), false)
  }
}