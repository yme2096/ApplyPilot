package ai.applypilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApplyPilotApplication {
    public static void main(String[] args) { SpringApplication.run(ApplyPilotApplication.class, args); }
}
