package graduation_project.Dailic.controller;

import graduation_project.Dailic.config.OpenAIProperties;
import graduation_project.Dailic.controller.DTO.ProblemDto;
import graduation_project.Dailic.domain.License;
import graduation_project.Dailic.domain.LicenseSelection;
import graduation_project.Dailic.domain.Problem;
import graduation_project.Dailic.service.AIService;
import graduation_project.Dailic.service.LicenseService;
import graduation_project.Dailic.service.ProblemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.net.ConnectException;
import org.hamcrest.Matchers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AIControllerContractTest {
    private MockMvc mvc;
    private MockRestServiceServer upstream;
    private ProblemService problems;
    private OpenAIProperties properties;

    @BeforeEach
    void setUp() {
        RestTemplate http = new RestTemplate();
        upstream = MockRestServiceServer.bindTo(http).build();
        properties = new OpenAIProperties();
        properties.setKey("test-key-not-real");
        properties.setUrl("https://example.invalid/chat/completions");
        properties.setModel("test-model");
        problems = mock(ProblemService.class);
        Problem problem = new Problem();
        problem.setQuestionText("테스트 문제");
        problem.setOption1("보기 1");
        problem.setCorrectAnswer("1");
        problem.setSolution("기존 해설");
        when(problems.getProblemDtoById(1L, false, 1L)).thenReturn(ProblemDto.from(problem, false, false));
        when(problems.getProblemDtoById(1L, true, 1L)).thenReturn(ProblemDto.from(problem, true, false));
        LicenseService licenses = mock(LicenseService.class);
        LicenseSelection selection = new LicenseSelection();
        selection.setLicense(new License(1L, "SQLD"));
        when(licenses.getCurrentLicenseSelection(1L)).thenReturn(selection);
        AIService service = new AIService(http, properties, problems);
        mvc = MockMvcBuilders.standaloneSetup(new AIController(service, problems, licenses))
                .setControllerAdvice(new AIExceptionHandler()).build();
    }

    @Test
    void successKeepsExistingResponseContract() throws Exception {
        upstream.expect(requestTo(properties.getUrl()))
                .andExpect(header("Authorization", "Bearer test-key-not-real"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.model").value("test-model"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"테스트 답변\"}}]}", MediaType.APPLICATION_JSON));
        mvc.perform(post("/ai/ask/1").param("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"설명해 주세요\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.answer").value("테스트 답변"));
        upstream.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"question\":null}", "{\"question\":\"  \"}"})
    void invalidQuestionDoesNotCallProvider(String body) throws Exception {
        mvc.perform(post("/ai/ask/1").param("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(problems);
        upstream.verify();
    }

    @Test
    void providerRateLimitIsNotASuccessfulAnswer() throws Exception {
        // ① 외부 API가 429 오류를 반환하는 상황을 만듦
        upstream.expect(requestTo(properties.getUrl()))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("{\"error\":{\"message\":\"private upstream detail\"}}")
                        .contentType(MediaType.APPLICATION_JSON));
        // ② 백엔드에 질문 요청을 보냄
        mvc.perform(post("/ai/ask/1")
                        .param("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"설명\"}"))
                // ③ 백엔드가 이렇게 응답해야 한다고 검사
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isServiceUnavailable()) //503
                .andExpect(jsonPath("$.status").value(503)) //503
                .andExpect(jsonPath("$.data").isEmpty()); //정상 답변이 없어야 함
        upstream.verify();
    }

    @Test
    void timeoutReturnsGatewayTimeout() throws Exception {
        upstream.expect(requestTo(properties.getUrl())).andRespond(withException(new SocketTimeoutException("test timeout")));
        mvc.perform(post("/ai/ask/1").param("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"설명\"}"))
                .andExpect(status().isGatewayTimeout());
        upstream.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"choices\":null}", "{\"choices\":[]}", "{\"choices\":[null]}",
            "{\"choices\":[{\"message\":null}]}", "{\"choices\":[{\"message\":{\"content\":\" \"}}]}"})
    void incompleteResponseIsNotASuccessfulAnswer(String body) throws Exception {
        upstream.expect(requestTo(properties.getUrl())).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        mvc.perform(post("/ai/explain/1").param("userId", "1"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.data").isEmpty());
        upstream.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 500, 502, 503})
    void providerErrorsAreSanitizedAndNotRetried(int code) throws Exception {
        upstream.expect(requestTo(properties.getUrl())).andRespond(withStatus(HttpStatus.valueOf(code))
                .body("private upstream detail").contentType(MediaType.TEXT_PLAIN));
        mvc.perform(post("/ai/explain/1").param("userId", "1"))
                .andExpect(status().is(code >= 500 ? 503 : 502))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(content().string(Matchers.not(Matchers.containsString("private upstream detail"))));
        upstream.verify();
    }

    @Test
    void questionLengthLimitRejectsBeforeDatabaseOrProvider() throws Exception {
        mvc.perform(post("/ai/ask/1").param("userId", "1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + "가".repeat(4001) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(problems);
        upstream.verify();
    }

    @Test
    void questionAtLengthLimitIsAccepted() throws Exception {
        upstream.expect(requestTo(properties.getUrl())).andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"content\":\"답변\"}}]}", MediaType.APPLICATION_JSON));
        mvc.perform(post("/ai/ask/1").param("userId", "1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + "가".repeat(4000) + "\"}"))
                .andExpect(status().isOk());
        upstream.verify();
    }

    @Test
    void missingConfigurationFailsWithoutOutboundCall() throws Exception {
        properties.setKey(null);
        mvc.perform(post("/ai/explain/1").param("userId", "1"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data").isEmpty());
        upstream.verify();
    }

    @Test
    void connectionFailureIsUnavailable() throws Exception {
        upstream.expect(requestTo(properties.getUrl())).andRespond(withException(new ConnectException("connection refused")));
        mvc.perform(post("/ai/explain/1").param("userId", "1"))
                .andExpect(status().isServiceUnavailable());
        upstream.verify();
    }

    @Test
    void malformedProviderJsonIsBadGateway() throws Exception {
        upstream.expect(requestTo(properties.getUrl())).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        mvc.perform(post("/ai/explain/1").param("userId", "1"))
                .andExpect(status().isBadGateway());
        upstream.verify();
    }

    @Test
    void missingProblemIsClientErrorWithoutOutboundCall() throws Exception {
        when(problems.getProblemDtoById(1L, false, 1L)).thenThrow(new IllegalArgumentException("missing problem"));
        mvc.perform(post("/ai/explain/1").param("userId", "1"))
                .andExpect(status().isBadRequest());
        upstream.verify();
    }

    @Test
    void missingQuestionTextIsUnprocessableWithoutOutboundCall() throws Exception {
        when(problems.getProblemDtoById(1L, false, 1L)).thenReturn(ProblemDto.from(new Problem(), false, false));
        mvc.perform(post("/ai/explain/1").param("userId", "1"))
                .andExpect(status().isUnprocessableEntity());
        upstream.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void explanationPreservesSolutionFlag(String includeSolution) throws Exception {
        upstream.expect(requestTo(properties.getUrl()))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.messages[1].content")
                        .value(Boolean.parseBoolean(includeSolution) ? Matchers.containsString("기존 해설: 기존 해설")
                                : Matchers.not(Matchers.containsString("기존 해설:"))))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"해설\"}}]}", MediaType.APPLICATION_JSON));
        mvc.perform(post("/ai/explain/1").param("userId", "1").param("includeSolution", includeSolution))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.answer").value("해설"));
        upstream.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-number"})
    void invalidProblemIdDoesNotReachService(String id) throws Exception {
        mvc.perform(post("/ai/explain/" + id).param("userId", "1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(problems);
        upstream.verify();
    }

    @Test
    void missingUserIdIsBadRequest() throws Exception {
        mvc.perform(post("/ai/explain/1")).andExpect(status().isBadRequest());
        verifyNoInteractions(problems);
        upstream.verify();
    }

    @Test
    void malformedClientJsonIsBadRequest() throws Exception {
        mvc.perform(post("/ai/ask/1").param("userId", "1").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(problems);
        upstream.verify();
    }
}
