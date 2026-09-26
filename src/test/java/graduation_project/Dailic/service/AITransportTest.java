package graduation_project.Dailic.service;

import com.sun.net.httpserver.HttpServer;
import graduation_project.Dailic.config.OpenAIConfig;
import graduation_project.Dailic.config.OpenAIProperties;
import graduation_project.Dailic.controller.DTO.ProblemDto;
import graduation_project.Dailic.domain.Problem;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AITransportTest {
    @Test
    void configuredReadTimeoutStopsWaitingForAnActualHttpServer() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            try {
                release.await(6, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            OpenAIProperties properties = new OpenAIProperties();
            properties.setKey("test-key-not-real");
            properties.setModel("test-model");
            properties.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            properties.setConnectTimeoutMs(1000);
            properties.setReadTimeoutMs(150);
            AIService service = new AIService(new OpenAIConfig().openAiRestTemplate(
                    new RestTemplateBuilder(), properties), properties, mock(ProblemService.class));
            Problem problem = new Problem();
            problem.setQuestionText("테스트 문제");
            long start = System.nanoTime();
            assertThatThrownBy(() -> service.explain(ProblemDto.from(problem, false, false), false, "SQLD"))
                    .isInstanceOfSatisfying(AIServiceException.class,
                            error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(4000);
            assertThat(requests.get()).isEqualTo(1);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
}
