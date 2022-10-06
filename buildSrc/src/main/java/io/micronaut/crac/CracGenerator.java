package io.micronaut.crac;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.starter.api.TestFramework;
import io.micronaut.starter.application.ApplicationType;
import io.micronaut.starter.application.Project;
import io.micronaut.starter.application.generator.GeneratorContext;
import io.micronaut.starter.application.generator.ProjectGenerator;
import io.micronaut.starter.build.dependencies.Dependency;
import io.micronaut.starter.io.ConsoleOutput;
import io.micronaut.starter.io.FileSystemOutputHandler;
import io.micronaut.starter.options.BuildTool;
import io.micronaut.starter.options.JdkVersion;
import io.micronaut.starter.options.Language;
import io.micronaut.starter.options.Options;
import io.micronaut.starter.util.NameUtils;
import jakarta.inject.Singleton;

import javax.validation.constraints.Pattern;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.starter.options.BuildTool.GRADLE;
import static io.micronaut.starter.options.JdkVersion.JDK_17;

@Singleton
public class CracGenerator {

    public static final String SNAPSHOT_REPO = "https://s01.oss.sonatype.org/content/repositories/snapshots";
    public static final String GRADLE_SNAPSHOT_REPO = "    maven { url = '" + SNAPSHOT_REPO + "' }";
    public static final String MAVEN_SONATYPE_ID = "      <id>sonatype</id>";

    private final ProjectGenerator projectGenerator;

    public CracGenerator(ProjectGenerator projectGenerator) {
        this.projectGenerator = projectGenerator;
    }

    public void generateAppIntoDirectory(
         @NonNull File directory,
         @NonNull ApplicationType type,
         @NonNull String packageAndName,
         @Nullable List<String> features,
         @Nullable BuildTool buildTool,
         @Nullable TestFramework testFramework,
         @Nullable Language lang,
         @Nullable JdkVersion javaVersion) throws IOException {
        GeneratorContext generatorContext = createProjectGeneratorContext(type, packageAndName, features, buildTool, testFramework, lang, javaVersion);
        try {
            projectGenerator.generate(type,
                    generatorContext.getProject(),
                    new FileSystemOutputHandler(directory, ConsoleOutput.NOOP),
                    generatorContext);
            Optional<Dependency> micronautCrac = generatorContext
                .getDependencies()
                .stream()
                .filter(dependency -> "micronaut-crac".equals(dependency.getArtifactId()))
                .findFirst();
            if (micronautCrac.map(Dependency::getVersion).map(v -> v.endsWith("-SNAPSHOT")).orElse(false)) {
                addSnapshotDependency(buildTool, directory);
            }
        } catch (Exception e) {
            throw new IOException("Error generating application: " + e.getMessage(), e);
        }
    }

    private void addSnapshotDependency(BuildTool buildTool, File directory) throws IOException {
        if (buildTool == null || buildTool.isGradle()) {
            Path script = directory.toPath().resolve("build.gradle");
            List<String> lines = Files.readAllLines(script);
            if (!lines.contains(GRADLE_SNAPSHOT_REPO)) {
                int index = lines.indexOf("repositories {");
                lines.add(index + 1, GRADLE_SNAPSHOT_REPO);
                Files.writeString(script, String.join("\n", lines));
            }
        } else {
            Path script = directory.toPath().resolve("pom.xml");
            List<String> lines = Files.readAllLines(script);
            if (!lines.contains(MAVEN_SONATYPE_ID)) {
                int index = lines.indexOf("  <repositories>");
                lines.add(index + 1, "    <repository>\n" +
                    MAVEN_SONATYPE_ID + "\n" +
                    "      <url>" + SNAPSHOT_REPO + "</url>\n" +
                    "    </repository>");
                Files.writeString(script, String.join("\n", lines));
            }
        }
    }

    GeneratorContext createProjectGeneratorContext(
            ApplicationType type,
            @Pattern(regexp = "[\\w-.]+") String packageAndName,
            @Nullable List<String> features,
            @Nullable BuildTool buildTool,
            @Nullable TestFramework testFramework,
            @Nullable Language lang,
            @Nullable JdkVersion javaVersion) throws IllegalArgumentException {
        Project project;
        try {
            project = NameUtils.parse(packageAndName);
        } catch (IllegalArgumentException e) {
            throw new HttpStatusException(BAD_REQUEST, "Invalid project name: " + e.getMessage());
        }

        return projectGenerator.createGeneratorContext(
                type,
                project,
                new Options(
                        lang,
                        testFramework != null ? testFramework.toTestFramework() : null,
                        buildTool == null ? GRADLE : buildTool,
                        javaVersion != null ? javaVersion : JDK_17),
                null,
                features != null ? features : Collections.emptyList(),
                ConsoleOutput.NOOP
        );
    }
}
