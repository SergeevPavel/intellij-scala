package org.jetbrains.plugins.scala
package project.gradle

import java.io.File
import java.util

import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException, ProjectKeys}
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationSource, NotificationCategory, NotificationData}
import com.intellij.openapi.externalSystem.service.project.{PlatformFacade, ProjectStructureHelper}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.model.data.ScalaModelData
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.project._
import org.jetbrains.sbt.project.data.service.{SafeProjectStructureHelper, AbstractDataService}

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class ScalaGradleDataService(val helper: ProjectStructureHelper)
        extends AbstractDataService[ScalaModelData, Library](ScalaModelData.KEY)
        with SafeProjectStructureHelper {

  import ScalaGradleDataService._

  def doImportData(toImport: util.Collection[DataNode[ScalaModelData]], project: Project) {
    toImport.asScala.foreach(doImport(_, project))
  }

  private def doImport(scalaNode: DataNode[ScalaModelData], project: Project): Unit =
    for {
      module <- getIdeModuleByNode(scalaNode, project)
      compilerOptions = compilerOptionsFrom(scalaNode.getData)
      compilerClasspath = scalaNode.getData.getScalaClasspath.asScala.toSeq
    } {
      module.configureScalaCompilerSettingsFrom("Gradle", compilerOptions)
      configureScalaSdk(project, module, project.scalaLibraries, compilerClasspath)
    }

  private def configureScalaSdk(project: Project, module: Module, scalaLibraries: Seq[Library], compilerClasspath: Seq[File]): Unit = {
    val compilerVersionOption = findScalaLibraryIn(compilerClasspath).flatMap(getVersionFromJar)
    if (compilerVersionOption.isEmpty) {
      showWarning(project, ScalaBundle.message("gradle.dataService.scalaVersionCantBeDetected", module.getName))
      return
    }
    val compilerVersion = compilerVersionOption.get

    val scalaLibraryOption = scalaLibraries.find(_.scalaVersion.contains(compilerVersion))
    if (scalaLibraryOption.isEmpty) {
      showWarning(project, ScalaBundle.message("gradle.dataService.scalaLibraryIsNotFound", compilerVersion.number, module.getName))
      return
    }
    val scalaLibrary = scalaLibraryOption.get

    if (!scalaLibrary.isScalaSdk) {
      val languageLevel = scalaLibrary.scalaLanguageLevel.getOrElse(ScalaLanguageLevel.Default)
      scalaLibrary.convertToScalaSdkWith(languageLevel, compilerClasspath)
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: Library], project: Project) {}
}

private object ScalaGradleDataService {

  private def findScalaLibraryIn(classpath: Seq[File]): Option[File] =
    classpath.find(_.getName.startsWith(ScalaLibraryName))

  private def getVersionFromJar(scalaLibrary: File): Option[Version] =
    JarVersion.findFirstIn(scalaLibrary.getName).map(Version(_))

  private def compilerOptionsFrom(data: ScalaModelData): Seq[String] = {
    val options = data.getScalaCompileOptions

    val presentations = Seq(
      options.isDeprecation -> "-deprecation",
      options.isUnchecked -> "-unchecked",
      options.isOptimize -> "-optimise",
      !isEmpty(options.getDebugLevel) -> s"-g:${options.getDebugLevel}",
      !isEmpty(options.getEncoding) -> s"-encoding ${options.getEncoding}",
      !isEmpty(data.getTargetCompatibility) -> s"-target:jvm-${data.getTargetCompatibility}")

    val additionalOptions =
      if (options.getAdditionalParameters != null) options.getAdditionalParameters.asScala else Seq.empty

    presentations.flatMap((include _).tupled) ++ additionalOptions
  }

  private def isEmpty(s: String) = s == null || s.isEmpty

  private def include(b: Boolean, s: String): Seq[String] = if (b) Seq(s) else Seq.empty

  private def showWarning(project: Project, message: String): Unit = {
    val notification = new NotificationData("Gradle Sync", message, NotificationCategory.WARNING, NotificationSource.PROJECT_SYNC);
    ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notification);
  }
}
