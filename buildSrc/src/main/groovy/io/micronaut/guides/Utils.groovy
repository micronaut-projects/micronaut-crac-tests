package io.micronaut.guides

import io.micronaut.starter.options.JdkVersion
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion

class Utils {

    private static final String SYS_PROP_MICRONAUT_GUIDE = 'micronaut.guide'
    private static final String ENV_JDK_VERSION = 'JDK_VERSION'
    private static final JdkVersion DEFAULT_JAVA_VERSION = JdkVersion.JDK_17

    static String singleGuide() {
        System.getProperty(SYS_PROP_MICRONAUT_GUIDE)
    }

    static boolean process(CracMetadata metadata, boolean checkJdk = true) {

        if (metadata.skip) {
            return false
        }

        boolean processGuide = singleGuide() == null || singleGuide() == metadata.slug
        if (!processGuide) {
            return false
        }

        if (checkJdk && skipBecauseOfJavaVersion(metadata)) {
            println "not processing $metadata.slug, JDK not between $metadata.minimumJavaVersion and $metadata.maximumJavaVersion"
            return false
        }

        return true
    }

    static boolean skipBecauseOfJavaVersion(CracMetadata metadata) {
        int jdkVersion = parseJdkVersion().majorVersion()
        return (metadata.minimumJavaVersion != null && jdkVersion < metadata.minimumJavaVersion) ||
                (metadata.maximumJavaVersion != null && jdkVersion > metadata.maximumJavaVersion)
    }

    static JdkVersion parseJdkVersion() {
        JdkVersion javaVersion
        if (System.getenv(ENV_JDK_VERSION)) {
            try {
                int majorVersion = Integer.valueOf(System.getenv(ENV_JDK_VERSION))
                javaVersion = JdkVersion.valueOf(majorVersion)
            } catch (NumberFormatException ignored) {
                throw new GradleException("Could not parse env " + ENV_JDK_VERSION + " to JdkVersion")
            }
        } else {
            try {
                javaVersion = JdkVersion.valueOf(JavaVersion.current().majorVersion as Integer)
            } catch (IllegalArgumentException ex) {
                println "WARNING: $ex.message: Defaulting to $DEFAULT_JAVA_VERSION"
                javaVersion = DEFAULT_JAVA_VERSION
            }
        }
        javaVersion
    }
}
