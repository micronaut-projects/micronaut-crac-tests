package io.micronaut.crac.tasks

import groovy.transform.CompileStatic
import io.micronaut.crac.CracMetadata
import io.micronaut.crac.CracProjectGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CompileStatic
@CacheableTask
abstract class SampleProjectGenerationTask extends DefaultTask {

    @Internal
    CracProjectGenerator guidesGenerator

    @Internal
    CracMetadata metadata

    @Input
    abstract Property<String> getSlug()

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getInputDirectory()

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    @TaskAction
    def perform() {
        guidesGenerator.generateOne(metadata, inputDirectory.get().asFile, outputDir.get().asFile)
    }
}
