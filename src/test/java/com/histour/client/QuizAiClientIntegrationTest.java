package com.histour.client;

import com.histour.domain.quiz.dto.AiQuizGenerateRequest;
import com.histour.domain.quiz.dto.AiQuizQuestion;
import com.histour.domain.quiz.dto.AiVisitedHeritage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class QuizAiClientIntegrationTest {

    @Test
    @EnabledIfSystemProperty(named = "quiz.ai.integration", matches = "true")
    void generateQuestionsReturnsValidQuizQuestionFromGms() {
        String apiKey = resolveGmsApiKey();
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "GMS API key is not configured");

        QuizAiClient client = new QuizAiClient();
        ReflectionTestUtils.setField(
                client,
                "anthropicUrl",
                "https://gms.ssafy.io/gmsapi/api.anthropic.com/v1/messages"
        );
        ReflectionTestUtils.setField(client, "apiKey", apiKey);

        List<AiQuizQuestion> questions = client.generateQuestions(new AiQuizGenerateRequest(
                1,
                List.of(new AiVisitedHeritage(
                        1L,
                        "서울 숭례문",
                        "지금 당신이 서 있는 이 곳에서는 조선 한양도성의 남쪽 관문이었던 숭례문을 볼 수 있습니다. " +
                                "숭례문은 도성의 출입을 관리하고 국가 의례와 도시 방어의 상징으로 기능했습니다."
                ))
        ));

        assertThat(questions).isNotEmpty();
        AiQuizQuestion question = questions.getFirst();
        assertThat(question.heritageId()).isEqualTo(1L);
        assertThat(question.title()).isNotBlank();
        assertThat(question.content()).isNotBlank();
        assertThat(question.choices()).hasSize(4);
        assertThat(question.answerIndex()).isBetween(0, 3);
        assertThat(question.explanation()).isNotBlank();
        assertThat(question.difficulty()).isIn("EASY", "MEDIUM", "HARD");
    }

    private String resolveGmsApiKey() {
        String envKey = System.getenv("GMS_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey;
        }

        ClassPathResource localConfig = new ClassPathResource("application-local.yaml");
        if (!localConfig.exists()) {
            return null;
        }

        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(localConfig);
        Properties properties = yaml.getObject();
        if (properties == null) {
            return null;
        }
        return resolvePlaceholder(properties.getProperty("gms.api.key"));
    }

    private String resolvePlaceholder(String value) {
        if (value == null || !value.startsWith("${") || !value.endsWith("}")) {
            return value;
        }

        String expression = value.substring(2, value.length() - 1);
        String[] parts = expression.split(":", 2);
        String envValue = System.getenv(parts[0]);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return parts.length == 2 ? parts[1] : null;
    }
}
