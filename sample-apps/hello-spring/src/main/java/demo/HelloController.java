package demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;

@RestController
public class HelloController {

    /** Change this and redeploy to demonstrate a rolling update. */
    static final String VERSION = "v2";

    @GetMapping("/")
    public Map<String, String> hello() throws Exception {
        return Map.of(
                "message", "Hello from DeployHub!",
                "version", VERSION,
                // Shows which pod answered: refresh after scaling to see load balancing
                "pod", InetAddress.getLocalHost().getHostName(),
                "time", Instant.now().toString());
    }
}
