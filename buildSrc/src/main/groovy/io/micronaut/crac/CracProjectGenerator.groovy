package io.micronaut.crac

import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.starter.api.TestFramework
import io.micronaut.starter.application.ApplicationType
import io.micronaut.starter.options.BuildTool
import io.micronaut.starter.options.JdkVersion
import io.micronaut.starter.options.Language
import org.gradle.api.GradleException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

import static io.micronaut.starter.api.TestFramework.JUNIT
import static io.micronaut.starter.api.TestFramework.SPOCK
import static io.micronaut.starter.options.Language.GROOVY
import static java.nio.file.FileVisitResult.CONTINUE
import static java.nio.file.Files.walkFileTree
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING

@CompileStatic
class CracProjectGenerator implements AutoCloseable {

    public static final String DEFAULT_APP_NAME = 'default'

    private static final Pattern GROOVY_JAVA_OR_KOTLIN = ~/.*\.java|.*\.groovy|.*\.kt/
    private static final Logger LOG = LoggerFactory.getLogger(this)
    private static final String APP_NAME = 'micronautguide'
    private static final String BASE_PACKAGE = 'example.micronaut'

    private final ApplicationContext applicationContext
    private final CracGenerator generator

    CracProjectGenerator() {
        applicationContext = ApplicationContext.run()
        generator = applicationContext.getBean(CracGenerator)
    }

    @Override
    void close() throws Exception {
        applicationContext.close()
    }

    @CompileDynamic
    static List<CracMetadata> parseAllMetadata(File cracDir, String metadataConfigName) {
        List<CracMetadata> metadatas = []

        cracDir.eachDir { dir ->
            parseCracMetadata(dir, metadataConfigName).ifPresent(metadatas::add)
        }

        mergeMetadataList(metadatas)

        metadatas
    }

    @CompileDynamic
    static Optional<CracMetadata> parseCracMetadata(File dir, String metadataConfigName) {
        File configFile = new File(dir, metadataConfigName)
        if (!configFile.exists()) {
            LOG.warn('metadata file not found for {}', dir.name)
            return Optional.empty()
        }

        Map config = new JsonSlurper().parse(configFile) as Map

        Optional.ofNullable(new CracMetadata(
                slug: dir.name,
                base: config.base,
                skip: config.skip ?: false,
                languages: config.languages ?: ['java', 'groovy', 'kotlin'],
                buildTools: config.buildTools ?: ['gradle', 'maven'],
                testFramework: config.testFramework,
                skipGradleTests: config.skipGradleTests ?: false,
                skipMavenTests: config.skipMavenTests ?: false,
                minimumJavaVersion: config.minimumJavaVersion,
                maximumJavaVersion: config.maximumJavaVersion,
                requirements: config.requirements,
                apps: config.apps.collect { it ->
                    new CracMetadata.App(
                            framework: it.framework,
                            testFramework: it.testFramework?.toUpperCase(),
                            name: it.name,
                            visibleFeatures: it.features ?: [],
                            invisibleFeatures: it.invisibleFeatures ?: [],
                            javaFeatures: it.javaFeatures ?: [],
                            kotlinFeatures: it.kotlinFeatures ?: [],
                            groovyFeatures: it.groovyFeatures ?: [],
                            applicationType: it.applicationType ? ApplicationType.valueOf(it.applicationType.toUpperCase()) : ApplicationType.DEFAULT,
                            excludeSource: it.excludeSource,
                            excludeTest: it.excludeTest
                    )
                }
        ))
    }

    void generateOne(CracMetadata metadata, File inputDir, File outputDir) {
        if (!outputDir.exists()) {
            assert outputDir.mkdir()
        }

        String packageAndName = BASE_PACKAGE + '.' + APP_NAME

        guidesOptions(metadata).each { option ->
            BuildTool buildTool = option.buildTool
            TestFramework testFramework = option.testFramework
            Language lang = option.language

            for (CracMetadata.App app : metadata.apps) {
                List<String> appFeatures = ([] as List<String>) + app.getFeatures(lang)

                // typical guides use 'default' as name, multi-project guides have different modules
                String appName = app.name == DEFAULT_APP_NAME ? "" : app.name
                String folder = folderName(metadata.slug, option)

                Path destinationPath = Paths.get(outputDir.absolutePath, folder, appName)
                File destination = destinationPath.toFile()
                destination.mkdir()

                generator.generateAppIntoDirectory(
                        destination,
                        app.applicationType,
                        packageAndName,
                        app.framework,
                        appFeatures,
                        buildTool,
                        app.testFramework ?: testFramework,
                        lang,
                        JdkVersion.JDK_17
                )

                if (metadata.base) {
                    File baseDir = new File(inputDir.parentFile, metadata.base)
                    copyGuideSourceFiles(baseDir, destinationPath, appName, option.language.toString(), true)
                }

                copyGuideSourceFiles(inputDir, destinationPath, appName, option.language.toString())

            }
        }
    }

    static String folderName(String slug, CracOption option) {
        "${slug}-${option.buildTool}-${option.language}"
    }

    private static void copyGuideSourceFiles(File inputDir, Path destinationPath,
                                             String appName, String language,
                                             boolean ignoreMissingDirectories = false) {

        // look for a common 'src' directory shared by multiple languages and copy those files first
        final String srcFolder = 'src'
        Path srcPath = Paths.get(inputDir.absolutePath, appName, srcFolder)
        if (Files.exists(srcPath)) {
            walkFileTree(srcPath, new CopyFileVisitor(Paths.get(destinationPath.toString(), srcFolder)))
        }

        Path sourcePath = Paths.get(inputDir.absolutePath, appName, language)
        if (!Files.exists(sourcePath)) {
            sourcePath.toFile().mkdir()
        }
        if (Files.exists(sourcePath)) {
            // copy source/resource files for the current language
            walkFileTree(sourcePath, new CopyFileVisitor(destinationPath))
        } else if (!ignoreMissingDirectories) {
            throw new GradleException("source directory ${sourcePath.toFile().absolutePath} does not exist")
        }
    }

    static void mergeMetadataList(List<CracMetadata> metadatas) {
        Map<String, CracMetadata> metadatasByDirectory = new TreeMap<>()
        for (CracMetadata metadata : metadatas) {
            metadatasByDirectory[metadata.slug] = metadata
        }

        mergeMetadataMap(metadatasByDirectory)

        metadatas.clear()
        metadatas.addAll metadatasByDirectory.values()
    }

    private static void mergeMetadataMap(Map<String, CracMetadata> metadatasByDirectory) {
        for (String dir : [] + metadatasByDirectory.keySet()) {
            CracMetadata metadata = metadatasByDirectory[dir]
            if (metadata.base) {
                CracMetadata base = metadatasByDirectory[metadata.base]
                CracMetadata merged = mergeMetadatas(base, metadata)
                metadatasByDirectory[dir] = merged
            }
        }
    }

    private static CracMetadata mergeMetadatas(CracMetadata base, CracMetadata metadata) {
        new CracMetadata().tap {
            slug = metadata.slug
            it.base = metadata.base
            skip = metadata.skip
            buildTools = metadata.buildTools ?: base.buildTools
            languages = metadata.languages ?: base.languages
            testFramework = metadata.testFramework ?: base.testFramework
            skipGradleTests = base.skipGradleTests || metadata.skipGradleTests
            skipMavenTests = base.skipMavenTests || metadata.skipMavenTests
            minimumJavaVersion = metadata.minimumJavaVersion ?: base.minimumJavaVersion
            maximumJavaVersion = metadata.maximumJavaVersion ?: base.maximumJavaVersion
            zipIncludes = metadata.zipIncludes // TODO support merging from base
            requirements = mergeLists(base.requirements, metadata.requirements)
            apps = mergeApps(base, metadata)
        }
    }

    private static List<CracMetadata.App> mergeApps(CracMetadata base, CracMetadata metadata) {

        Map<String, CracMetadata.App> baseApps = base.apps.collectEntries { [(it.name): it] }
        Map<String, CracMetadata.App> guideApps = metadata.apps.collectEntries { [(it.name): it] }

        Set<String> baseOnly = baseApps.keySet() - guideApps.keySet()
        Set<String> guideOnly = guideApps.keySet() - baseApps.keySet()
        Collection<String> inBoth = baseApps.keySet().intersect(guideApps.keySet())

        List<CracMetadata.App> merged = []
        merged.addAll(baseOnly.collect { baseApps[it] })
        merged.addAll(guideOnly.collect { guideApps[it] })

        for (String name : inBoth) {
            CracMetadata.App baseApp = baseApps[name]
            CracMetadata.App guideApp = guideApps[name]
            guideApp.visibleFeatures.addAll baseApp.visibleFeatures
            guideApp.invisibleFeatures.addAll baseApp.invisibleFeatures
            guideApp.javaFeatures.addAll baseApp.javaFeatures
            guideApp.kotlinFeatures.addAll baseApp.kotlinFeatures
            guideApp.groovyFeatures.addAll baseApp.groovyFeatures
            merged << guideApp
        }

        merged
    }

    private static List mergeLists(Collection base, Collection others) {
        List merged = []
        if (base) {
            merged.addAll base
        }
        if (others) {
            merged.addAll others
        }
        merged
    }

    static List<CracOption> guidesOptions(CracMetadata guideMetadata) {
        List<String> buildTools = guideMetadata.buildTools
        List<String> languages = guideMetadata.languages
        String testFramework = guideMetadata.testFramework
        List<CracOption> guidesOptionList = []

        for (BuildTool buildTool : BuildTool.values()) {
            if (buildTools.contains(buildTool.toString())) {
                for (Language language : Language.values()) {
                    if (languages.contains(language.toString())) {
                        guidesOptionList << new CracOption(buildTool, language, testFrameworkOption(language, testFramework))
                    }
                }
            }
        }

        guidesOptionList
    }

    private static TestFramework testFrameworkOption(@NonNull Language language,
                                                     @Nullable String testFramework) {
        if (testFramework != null) {
            TestFramework.valueOf(testFramework.toUpperCase())
        } else if (language == GROOVY) {
            SPOCK
        } else {
            JUNIT
        }
    }

    static class CopyFileVisitor extends SimpleFileVisitor<Path> {

        private final Path targetPath
        private Path sourcePath

        CopyFileVisitor(Path targetPath) {
            this.targetPath = targetPath
        }

        @Override
        FileVisitResult preVisitDirectory(final Path dir,
                                                 final BasicFileAttributes attrs) throws IOException {
            if (sourcePath == null) {
                sourcePath = dir
            } else {
                Files.createDirectories(targetPath.resolve(sourcePath.relativize(dir)))
            }
            CONTINUE
        }

        @Override
        FileVisitResult visitFile(final Path file,
                                         final BasicFileAttributes attrs) throws IOException {
            Files.copy(
                    file,
                    targetPath.resolve(sourcePath.relativize(file)),
                    REPLACE_EXISTING
            )
            CONTINUE
        }
    }
}
