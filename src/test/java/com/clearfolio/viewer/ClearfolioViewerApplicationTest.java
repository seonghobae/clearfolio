package com.clearfolio.viewer;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class ClearfolioViewerApplicationTest {

    @Test
    void mainDelegatesToSpringApplicationRun() {
        String[] args = {"--spring.main.web-application-type=none"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            ClearfolioViewerApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(ClearfolioViewerApplication.class, args));
        }
    }
}
