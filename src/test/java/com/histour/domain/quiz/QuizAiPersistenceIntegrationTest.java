package com.histour.domain.quiz;

import com.histour.client.QuizAiClient;
import com.histour.domain.quiz.dto.AiQuizGenerateRequest;
import com.histour.domain.quiz.dto.AiQuizQuestion;
import com.histour.domain.quiz.dto.AiVisitedHeritage;
import com.histour.domain.quiz.entity.Quiz;
import com.histour.domain.quiz.entity.QuizChoice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/histour?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul",
        "spring.datasource.username=root",
        "spring.datasource.password=${DB_PASSWORD:}"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QuizAiPersistenceIntegrationTest {

    private static final long HERITAGE_ID = 990002L;

    @Autowired
    private QuizMapper quizMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("""
                INSERT INTO heritage (
                    id, name, category, period, location,
                    ccba_kdcd, ccba_asno, ccba_ctcd
                )
                VALUES (
                    ?, ?, ?, ?,
                    ST_GeomFromText('POINT(37.5665 126.9780)', 4326),
                    ?, ?, ?
                )
                """, HERITAGE_ID, "AI 저장 테스트 유적지", "사적", "JOSEON",
                "QA", "990002", "99");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @EnabledIfSystemProperty(named = "quiz.ai.integration", matches = "true")
    void generatedAiQuizCanBeParsedAndSavedToDatabase() {
        String apiKey = resolveGmsApiKey();
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "GMS API key is not configured");

        QuizAiClient client = new QuizAiClient();
        ReflectionTestUtils.setField(
                client,
                "anthropicUrl",
                "https://gms.ssafy.io/gmsapi/api.anthropic.com/v1/messages"
        );
        ReflectionTestUtils.setField(client, "apiKey", apiKey);

        AiQuizQuestion question = client.generateQuestions(new AiQuizGenerateRequest(
                1,
                List.of(new AiVisitedHeritage(
                        HERITAGE_ID,
                        "AI 저장 테스트 유적지",
                        "조선 시대 도성 방어와 국가 의례의 의미를 복습할 수 있는 역사 현장입니다."
                ))
        )).getFirst();

        System.out.println("AI generated quiz:");
        System.out.println("heritageId = " + question.heritageId());
        System.out.println("title = " + question.title());
        System.out.println("content = " + question.content());
        System.out.println("choices = " + question.choices());
        System.out.println("answerIndex = " + question.answerIndex());
        System.out.println("explanation = " + question.explanation());
        System.out.println("difficulty = " + question.difficulty());

        assertThat(question.heritageId()).isEqualTo(HERITAGE_ID);
        assertThat(question.choices()).hasSize(4);
        assertThat(question.answerIndex()).isBetween(0, 3);

        Quiz quiz = Quiz.builder()
                .heritageId(question.heritageId())
                .title(question.title())
                .content(question.content())
                .correctAnswer(question.choices().get(question.answerIndex()))
                .explanation(question.explanation())
                .source("AI_GENERATED")
                .difficulty(question.difficulty())
                .build();
        quizMapper.insertQuiz(quiz);

        for (int i = 0; i < question.choices().size(); i++) {
            quizMapper.insertChoice(QuizChoice.builder()
                    .quizId(quiz.getId())
                    .content(question.choices().get(i))
                    .correct(i == question.answerIndex())
                    .build());
        }

        List<Quiz> savedQuizzes = quizMapper.findQuizzesByHeritageIds(List.of(HERITAGE_ID));
        assertThat(savedQuizzes).hasSize(1);
        assertThat(savedQuizzes.getFirst().getId()).isEqualTo(quiz.getId());
        assertThat(savedQuizzes.getFirst().getTitle()).isEqualTo(question.title());
        assertThat(savedQuizzes.getFirst().getContent()).isEqualTo(question.content());
        assertThat(savedQuizzes.getFirst().getCorrectAnswer()).isEqualTo(question.choices().get(question.answerIndex()));
        assertThat(savedQuizzes.getFirst().getExplanation()).isEqualTo(question.explanation());
        assertThat(savedQuizzes.getFirst().getSource()).isEqualTo("AI_GENERATED");

        List<QuizChoice> savedChoices = quizMapper.findChoicesByQuizIds(List.of(quiz.getId()));
        assertThat(savedChoices).hasSize(4);
        assertThat(savedChoices)
                .filteredOn(QuizChoice::isCorrect)
                .singleElement()
                .extracting(QuizChoice::getContent)
                .isEqualTo(question.choices().get(question.answerIndex()));
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

    private void cleanUp() {
        jdbcTemplate.update("""
                DELETE qc FROM quiz_choices qc
                JOIN quiz q ON qc.quiz_id = q.id
                WHERE q.heritage_id = ?
                """, HERITAGE_ID);
        jdbcTemplate.update("DELETE FROM quiz WHERE heritage_id = ?", HERITAGE_ID);
        jdbcTemplate.update("DELETE FROM heritage WHERE id = ?", HERITAGE_ID);
    }
}
