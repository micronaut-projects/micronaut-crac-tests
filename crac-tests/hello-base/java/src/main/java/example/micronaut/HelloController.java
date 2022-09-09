package example.micronaut;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;

import java.util.Optional;

@Controller
public class HelloController {

    private final AppConfig appConfig;

    public HelloController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Get("/hello{/name}")
    public String hello(@Nullable String name) {
        return "Hello " + (name == null ? appConfig.getSuffix() : name) + "!";
    }
}
