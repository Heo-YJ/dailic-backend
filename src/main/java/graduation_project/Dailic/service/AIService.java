package graduation_project.Dailic.service;

import graduation_project.Dailic.config.OpenAIProperties;
import graduation_project.Dailic.controller.DTO.OpenAiRequest;
import graduation_project.Dailic.controller.DTO.OpenAiResponse;
import graduation_project.Dailic.controller.DTO.ProblemDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.*;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AIService {
    private final RestTemplate openAiRestTemplate;
    private final OpenAIProperties properties;
    private final ProblemService problemService;

    public AIService(@Qualifier("openAiRestTemplate") RestTemplate openAiRestTemplate,
                     OpenAIProperties properties, ProblemService problemService) {
        this.openAiRestTemplate = openAiRestTemplate;
        this.properties = properties;
        this.problemService = problemService;
    }

    public String ask(Long problemId, Long userId, String licenseName, String question) {
        if (!StringUtils.hasText(question) || question.length() > 4000) {
            throw new IllegalArgumentException("Invalid question");
        }
        ProblemDto problem = problemService.getProblemDtoById(problemId, false, userId);
        String systemPrompt = String.format(
                "당신은 '%s' 시험 대비 학습 멘토입니다. 사용자의 질문에 대해 참조 문제를 기반으로 간결하고 명확하게 답변해 주세요.", licenseName);
        String userPrompt = problemPrompt(problem) + "\n사용자 질문: " + question.strip();
        return complete(systemPrompt, userPrompt);
    }

    public String explain(ProblemDto problem, boolean includeSolution, String licenseName) {
        String systemPrompt = String.format(
                "당신은 '%s' 시험 대비 학습 멘토입니다. 다음 객관식 문제를 단계별로 상세하고 친절하게 한국어로 해설해 주세요.", licenseName);
        StringBuilder prompt = new StringBuilder(problemPrompt(problem));
        if (includeSolution && StringUtils.hasText(problem.getCorrectAnswer())) {
            prompt.append("\n정답: ").append(problem.getCorrectAnswer().replace("option", ""));
        }
        if (includeSolution && StringUtils.hasText(problem.getSolution())) {
            prompt.append("\n기존 해설: ").append(problem.getSolution());
        }
        return complete(systemPrompt, prompt.toString());
    }

    private String problemPrompt(ProblemDto problem) {
        if (problem == null || !StringUtils.hasText(problem.getQuestionText())) {
            throw new AIServiceException(HttpStatus.UNPROCESSABLE_ENTITY, "참조할 문제 내용이 없습니다.");
        }
        StringBuilder prompt = new StringBuilder("문제: ").append(problem.getQuestionText()).append("\n보기:\n");
        if (problem.getOptions() != null) {
            for (int i = 0; i < problem.getOptions().size(); i++) {
                prompt.append(i + 1).append(") ").append(problem.getOptions().get(i)).append("\n");
            }
        }
        return prompt.toString();
    }

    private String complete(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(properties.getKey()) || !StringUtils.hasText(properties.getModel())
                || !StringUtils.hasText(properties.getUrl())) {
            throw new AIServiceException(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스 설정을 확인 중입니다.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getKey());
        OpenAiRequest request = new OpenAiRequest(properties.getModel(), List.of(
                new OpenAiRequest.Message("system", systemPrompt), new OpenAiRequest.Message("user", userPrompt)));
        long started = System.nanoTime();
        String outcome = "failure";
        try {
            // One attempt: automatic retries can generate duplicate paid completions.
            ResponseEntity<OpenAiResponse> response = openAiRestTemplate.exchange(properties.getUrl(),
                    HttpMethod.POST, new HttpEntity<>(request, headers), OpenAiResponse.class);
            OpenAiResponse body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.getChoices() == null
                    || body.getChoices().isEmpty() || body.getChoices().get(0) == null
                    || body.getChoices().get(0).getMessage() == null
                    || !StringUtils.hasText(body.getChoices().get(0).getMessage().getContent())) {
                throw new AIServiceException(HttpStatus.BAD_GATEWAY, "AI가 유효한 답변을 반환하지 않았습니다.");
            }
            outcome = "success";
            return body.getChoices().get(0).getMessage().getContent();
        } catch (RestClientResponseException error) {
            // Never log upstream bodies: they can contain user input or account details.
            log.warn("AI provider HTTP status={}", error.getStatusCode().value());
            if (error.getStatusCode().value() == 429 || error.getStatusCode().is5xxServerError()) {
                throw new AIServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                        "AI 서비스를 일시적으로 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.");
            }
            throw new AIServiceException(HttpStatus.BAD_GATEWAY, "AI 서비스 요청을 처리하지 못했습니다.");
        } catch (ResourceAccessException error) {
            if (isTimeout(error)) {
                throw new AIServiceException(HttpStatus.GATEWAY_TIMEOUT,
                        "AI 응답 대기시간을 초과했습니다. 잠시 후 다시 시도해 주세요.");
            }
            throw new AIServiceException(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스에 연결할 수 없습니다.");
        } catch (RestClientException error) {
            throw new AIServiceException(HttpStatus.BAD_GATEWAY, "AI 응답을 처리하지 못했습니다.");
        } finally {
            // Provider round-trip and decoding time, not end-to-end app latency.
            log.info("AI call outcome={} duration_ms={}", outcome,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    private boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) return true;
        }
        return false;
    }
}
