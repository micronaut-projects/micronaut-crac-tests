package io.micronaut.crac.tasks

import groovy.transform.CompileStatic
import io.micronaut.crac.CracMetadata
import io.micronaut.crac.TestScriptGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CompileStatic
@CacheableTask
abstract class TestScriptTask extends DefaultTask {

    @Input
    abstract Property<String> getGuideSlug()

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getMetadataFile()

    @Internal
    CracMetadata metadata

    @OutputFile
    abstract RegularFileProperty getScriptFile()

    @TaskAction
    def perform() {
        TestScriptGenerator.generateTestScript(scriptFile.get().asFile.parentFile, Collections.singletonList(metadata))
    }
}
