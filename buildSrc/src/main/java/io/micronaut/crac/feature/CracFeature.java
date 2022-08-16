package io.micronaut.crac.feature;

import io.micronaut.starter.application.ApplicationType;
import io.micronaut.starter.application.generator.GeneratorContext;
import io.micronaut.starter.build.dependencies.Dependency;
import io.micronaut.starter.feature.Category;
import io.micronaut.starter.feature.Feature;
import jakarta.inject.Singleton;

@Singleton
public class CracFeature implements Feature {

    public static final String NAME = "crac";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getTitle() {
        return "CRaC support";
    }

    @Override
    public String getDescription() {
        return "Adds the Java CRaC library to the Micronaut application to enable CRaC support";
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public String getCategory() {
        return Category.PACKAGING;
    }

    @Override
    public String getThirdPartyDocumentation() {
        return "https://www.azul.com/blog/superfast-application-startup-java-on-crac/";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
            .lookupArtifactId("micronaut-crac")
            .runtime());
    }
}
