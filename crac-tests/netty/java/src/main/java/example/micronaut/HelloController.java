package example.micronaut;

@Controller
public class HelloController {

    @Get("/hello")
    public String hello() {
        return "Hello World!";
    }
}
