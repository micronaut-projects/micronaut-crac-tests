package example.micronaut;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;

@Controller
public class HelloController {

    private final AppConfig appConfig;

    public HelloController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Get
    public String hello(@QueryValue @Nullable String name) {
        return "Hello " + (name == null ? appConfig.getSuffix() : name) + "!";
    }
}
