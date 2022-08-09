package example.micronaut;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;

import java.util.Optional;

@Controller
public class HelloController {

    @Get("/hello{/name}")
    public String hello(@Nullable String name) {
        return "Hello " + (name == null ? "world" : name) + "!";
    }
}
