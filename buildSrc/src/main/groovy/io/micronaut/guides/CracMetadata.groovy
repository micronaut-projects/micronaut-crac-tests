package io.micronaut.guides

import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.micronaut.starter.application.ApplicationType

import java.time.LocalDate

@ToString(includeNames = true)
@CompileStatic
class CracMetadata {

    String slug

    boolean skip
    String base

    List<String> buildTools
    List<String> languages
    String testFramework

    List<App> apps

    @ToString(includeNames = true)
    @CompileStatic
    static class App {
        ApplicationType applicationType
        String name
        List<String> features
        List<String> excludeSource
        List<String> excludeTest
    }
}
