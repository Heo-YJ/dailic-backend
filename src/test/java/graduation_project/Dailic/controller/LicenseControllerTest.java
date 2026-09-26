package graduation_project.Dailic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import graduation_project.Dailic.controller.DTO.LicenseSelectionRequestDto;
import graduation_project.Dailic.domain.LicenseSelection;
import graduation_project.Dailic.domain.License;
import graduation_project.Dailic.domain.Occupation;
import graduation_project.Dailic.domain.User;
import graduation_project.Dailic.service.LicenseService;
import graduation_project.Dailic.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static ch.qos.logback.classic.spi.ThrowableProxyVO.build;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LicenseController.class)
@AutoConfigureMockMvc
public class LicenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    UserService userService;

    @MockBean
    LicenseService licenseService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void selectLicense_success() throws Exception {
        //given
        Long userId = 1L;
        LicenseSelectionRequestDto requestDto = new LicenseSelectionRequestDto();
        requestDto.setOccupation(Occupation.COMMON);
        requestDto.setLicense("정보처리기사");

        User mockUser = new User();
        mockUser.setId(userId);

        LicenseSelection mockSelection = LicenseSelection.builder()
                .user(mockUser)
                .occupation(Occupation.COMMON)
                .license(new License(1L, "정보처리기사"))
                .build();

        Mockito.when(userService.getCreateUser(userId)).thenReturn(mockUser);
        Mockito.when(licenseService.register(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(mockSelection);

        //when + then
        mockMvc.perform(post("/licenses")
                        .param("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.occupation").value("COMMON"))
                .andExpect(jsonPath("$.data.license").value("정보처리기사"))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("학습 자격증이 설정되었습니다."));
    }


}
