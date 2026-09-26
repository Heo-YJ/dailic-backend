package graduation_project.Dailic.controller;

import graduation_project.Dailic.controller.DTO.AIResponse;
import graduation_project.Dailic.controller.DTO.AskRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import graduation_project.Dailic.controller.DTO.ApiResponse;
import graduation_project.Dailic.controller.DTO.ProblemDto;
import graduation_project.Dailic.service.AIService;
import graduation_project.Dailic.service.LicenseService;
import graduation_project.Dailic.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIService aiService;
    private final ProblemService problemService; // 🔥 여기에 추가
    private final LicenseService licenseService;

    /**
     * 자유 질문 API
     */
    @PostMapping("/ask/{problemId}")
    // 반환 타입을 ApiResponse<AIResponse>로 변경
    public ResponseEntity<ApiResponse<AIResponse>> ask(
            @PathVariable @Positive Long problemId,
            @RequestParam @Positive Long userId,
            @Valid @RequestBody AskRequest request
    ) {
        String licenseName = licenseService.getCurrentLicenseSelection(userId)
                .getLicense()
                .getName();

        // AIService에서 String 응답을 받음
        String aiResponseText = aiService.ask(problemId, userId, licenseName, request.getQuestion());

        // String 응답을 AIResponse DTO로 래핑
        AIResponse aiResponseDto = new AIResponse(aiResponseText);

        ApiResponse<AIResponse> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "AI 문맥 기반 답변 조회가 완료되었습니다.",
                aiResponseDto
        );
        return ResponseEntity.ok(response);
    }

    //--- 문제 해설 API ---
    @PostMapping("/explain/{problemId}")
    // 반환 타입을 ApiResponse<AIResponse>로 변경
    public ResponseEntity<ApiResponse<AIResponse>> explain(
            @PathVariable @Positive Long problemId,
            @RequestParam @Positive Long userId,
            @RequestParam(defaultValue = "false") boolean includeSolution
    ) {
        String licenseName = licenseService.getCurrentLicenseSelection(userId)
                .getLicense()
                .getName();

        ProblemDto dto = problemService.getProblemDtoById(problemId, includeSolution, userId);

        // AIService에서 String 해설을 받음
        String aiExplanationText = aiService.explain(dto, includeSolution, licenseName);

        // String 해설을 AIResponse DTO로 래핑
        AIResponse explanationDto = new AIResponse(aiExplanationText);

        ApiResponse<AIResponse> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "AI 해설 조회가 완료되었습니다.",
                explanationDto
        );
        return ResponseEntity.ok(response);
    }

}
