package com.medsim.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.medsim.backend.dto.response.SimulationResult;
import com.medsim.backend.service.SimulationService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Spring 컨텍스트 없이 SimulationService를 직접 실행해
 * 강남구/내과 기준 36개월 결과를 static 리소스로 저장한다.
 *
 * 실행: ./gradlew test --tests "com.medsim.backend.MockResultGeneratorTest"
 */
class MockResultGeneratorTest {

    @Test
    void generateMockResultJson() throws Exception {
        SimulationService service = new SimulationService();
        SimulationResult result = service.getMockResult();

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        Path outputPath = Paths.get("src/main/resources/static/mock-simulation-result.json");
        mapper.writeValue(outputPath.toFile(), result);

        System.out.println("Generated: " + outputPath.toAbsolutePath());
    }
}
