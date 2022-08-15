package io.micronaut.crac

import groovy.transform.CompileStatic
import io.micronaut.crac.tasks.SampleProjectGenerationTask
import io.micronaut.crac.tasks.TestScriptRunnerTask
import io.micronaut.crac.tasks.TestScriptTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

import java.util.concurrent.atomic.AtomicBoolean

@CompileStatic
class CracPlugin implements Plugin<Project> {

    private static final String TASK_SUFFIX_GENERATE_PROJECTS = "GenerateProjects"
    private static final String TASK_SUFFIX_BUILD = "Build"

    private static final AtomicBoolean firstTestRunnerTask = new AtomicBoolean(true)

    @Override
    void apply(Project project) {
        def projectGenerator = new CracProjectGenerator()
        def cracTestDir = project.layout.projectDirectory.dir("crac-tests")
        def codeDir = project.layout.buildDirectory.dir("code")
        def cracBuildTasks = CracProjectGenerator.parseAllMetadata(
                cracTestDir.asFile,
                project.extensions.extraProperties.get("metadataConfigName").toString()
        )
                .findAll { !it.skip }
                .collect { metadata ->
                    String taskSlug = kebabCaseToGradleName(metadata.slug)

                    def generateTask = registerGenerateTask(project, metadata, projectGenerator, cracTestDir, codeDir, taskSlug)
                    def testScriptTask = registerTestScriptTask(project, taskSlug, metadata, generateTask)
                    def testScriptRunnerTask = registerTestScriptRunnerTask(project, taskSlug, metadata, testScriptTask)
                    registerCracBuild(project, taskSlug, metadata, testScriptTask, testScriptRunnerTask)
                }

        project.tasks.register("runAllCracTests") { Task it ->
            it.group = 'CRaC'
            it.description = 'Runs all CRaC test scripts'
            it.dependsOn(cracBuildTasks)
        }
    }

    private static String kebabCaseToGradleName(String name) {
        name.split("-").with {
            "${it.head().toLowerCase()}${it.drop(1)*.capitalize().join('')}"
        }
    }

    private static TaskProvider<Task> registerCracBuild(Project project,
                                                        String taskSlug,
                                                        CracMetadata metadata,
                                                        TaskProvider<? extends Task>... dependsOnTasks) {
        project.tasks.register("${taskSlug}${TASK_SUFFIX_BUILD}") { Task it ->
            it.group = "CRaC $metadata.slug"
            it.dependsOn(dependsOnTasks)
        }
    }

    private static TaskProvider<SampleProjectGenerationTask> registerGenerateTask(Project project,
                                                                                  CracMetadata metadata,
                                                                                  CracProjectGenerator projectGenerator,
                                                                                  Directory guidesDir,
                                                                                  Provider<Directory> codeDir,
                                                                                  String taskSlug) {
        project.tasks.register("${taskSlug}${TASK_SUFFIX_GENERATE_PROJECTS}", SampleProjectGenerationTask) { SampleProjectGenerationTask it ->
            it.group = "CRaC $metadata.slug"
            it.description = "Generate test project for '${metadata.slug}'"
            it.guidesGenerator = projectGenerator
            it.slug.set(metadata.slug)
            it.inputDirectory.set(guidesDir.dir(metadata.slug))
            it.outputDir.set(codeDir.map(s -> s.dir(metadata.slug)))
            it.guidesGenerator = projectGenerator
            it.metadata = metadata
        }
    }

    private static TaskProvider<TestScriptTask> registerTestScriptTask(Project project,
                                                                       String taskSlug,
                                                                       CracMetadata metadata,
                                                                       TaskProvider<SampleProjectGenerationTask> generateTask) {
        project.tasks.register("${taskSlug}GenerateTestScript", TestScriptTask) { TestScriptTask it ->
            it.group = "CRaC $metadata.slug"
            it.description = "Create a test.sh script for the projects generated by $metadata.slug"
            it.metadata = metadata
            it.guideSlug.set(metadata.slug)
            it.metadataFile.set(project.layout.projectDirectory.dir("crac-tests/${metadata.slug}").file("metadata.json"))
            it.scriptFile.set(project.layout.buildDirectory.dir("code/${metadata.slug}").map(d -> d.file("test.sh")))
            it.dependsOn(generateTask)
        }
    }

    private static TaskProvider<TestScriptRunnerTask> registerTestScriptRunnerTask(Project project,
                                                                                   String taskSlug,
                                                                                   CracMetadata metadata,
                                                                                   TaskProvider<TestScriptTask> testScriptTask) {
        project.tasks.register("${taskSlug}RunTestScript", TestScriptRunnerTask) { TestScriptRunnerTask it ->
            Provider<Directory> codeDirectory = project.layout.buildDirectory.dir("code/${metadata.slug}")

            it.group = "CRaC $metadata.slug"
            it.description = "Run the tests for all projects generated by $metadata.slug"

            it.testScript.set(testScriptTask.flatMap { t -> t.scriptFile })
            it.cracTestSourceDirectory.set(project.layout.projectDirectory.dir("crac-tests/${metadata.slug}"))

            it.firstTask.set(firstTestRunnerTask)

            // We tee the script output to a file, this is the cached result
            it.outputFile.set(codeDirectory.map(d -> d.file("output.log")))
        }
    }
}