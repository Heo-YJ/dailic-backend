package graduation_project.Dailic.controller.DTO;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class AskRequest {
    @NotBlank(message = "질문을 입력해 주세요.")
    @Size(max = 4000, message = "질문은 4000자 이내로 입력해 주세요.")
    private String question;
}
