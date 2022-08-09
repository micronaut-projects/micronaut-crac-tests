package io.micronaut.guides

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.micronaut.core.annotation.NonNull
import io.micronaut.starter.api.TestFramework
import io.micronaut.starter.options.BuildTool
import io.micronaut.starter.options.Language

@Canonical
@CompileStatic
class CracOption {

    @NonNull
    BuildTool buildTool

    @NonNull
    Language language

    @NonNull
    TestFramework testFramework
}
