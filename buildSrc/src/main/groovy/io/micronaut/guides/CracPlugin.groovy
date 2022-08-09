package io.micronaut.guides

import groovy.transform.CompileStatic
import io.micronaut.guides.tasks.SampleProjectGenerationTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

@CompileStatic
class CracPlugin implements Plugin<Project> {

    private static final String TASK_SUFFIX_GENERATE_PROJECTS = "GenerateProjects"

    @Override
    void apply(Project project) {
        def projectGenerator = new CracProjectGenerator()
        def cracTestDir = project.layout.projectDirectory.dir("crac-tests")
        def codeDir = project.layout.buildDirectory.dir("code")
        List<CracMetadata> metadatas = CracProjectGenerator.parseAllMetadata(
                cracTestDir.asFile,
                project.extensions.extraProperties.get("metadataConfigName").toString()
        )
        metadatas
                .findAll {Utils.process(it, false) }
                .each { metadata ->
                    String taskSlug = kebabCaseToGradleName(metadata.slug)

                    TaskProvider<SampleProjectGenerationTask> generateTask = registerGenerateTask(project, metadata, projectGenerator, cracTestDir, codeDir, taskSlug)
                }
    }

    private static String kebabCaseToGradleName(String name) {
        name.split("-").with {
            "${it.head().toLowerCase()}${it.drop(1)*.capitalize().join('')}"
        }
    }

    private static TaskProvider<SampleProjectGenerationTask> registerGenerateTask(Project project,
                                                                                  CracMetadata metadata,
                                                                                  CracProjectGenerator projectGenerator,
                                                                                  Directory guidesDir,
                                                                                  Provider<Directory> codeDir,
                                                                                  String taskSlug) {
        project.tasks.register("${taskSlug}${TASK_SUFFIX_GENERATE_PROJECTS}", SampleProjectGenerationTask) { SampleProjectGenerationTask it ->
            it.group = "guides $metadata.slug"
            it.description = "Generate test project for '${metadata.slug}'"
            it.guidesGenerator = projectGenerator
            it.slug.set(metadata.slug)
            it.inputDirectory.set(guidesDir.dir(metadata.slug))
            it.outputDir.set(codeDir.map(s -> s.dir(metadata.slug)))
            it.guidesGenerator = projectGenerator
            it.metadata = metadata
        }
    }
}
