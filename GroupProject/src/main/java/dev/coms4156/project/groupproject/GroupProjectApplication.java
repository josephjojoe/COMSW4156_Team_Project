package dev.coms4156.project.groupproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * This class contains the startup of the application.
 */
@SpringBootApplication
public final class GroupProjectApplication {
  private GroupProjectApplication() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static void main(String[] args) {
    SpringApplication.run(GroupProjectApplication.class, args);
  }
}
