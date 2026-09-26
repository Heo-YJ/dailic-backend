package graduation_project.Dailic.repository;

import graduation_project.Dailic.domain.Problem;
import graduation_project.Dailic.domain.License;
import graduation_project.Dailic.domain.User;
import graduation_project.Dailic.domain.UserProblemStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DataJpaTest
public class UserProblemStatusRepositoryTest {

    @Autowired
    private UserProblemStatusRepository userProblemStatusRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("총 푼 문제 개수와 정답 문제 개수 확인")
    void countUserSolvedProblems() {
        //given
        User user = new User();
        user.setUsername("yejin");
        userRepository.save(user);

        License license = new License(null, "SQLD");
        em.persist(license);

        Problem problem1 = new Problem();
        problem1.setQuestionText("문제1");
        problem1.setLicense(license);
        problemRepository.save(problem1);

        Problem problem2 = new Problem();
        problem2.setQuestionText("문제2");
        problem2.setLicense(license);
        problemRepository.save(problem2);

        UserProblemStatus status1 = UserProblemStatus.builder()
                .user(user)
                .problem(problem1)
                .isCorrect(true)
                .isScraped(false)
                .isRetried(false)
                .answeredAt(LocalDateTime.now())   // ★ 추가
                .build();
        userProblemStatusRepository.save(status1);


        UserProblemStatus status2 = UserProblemStatus.builder()
                .user(user)
                .problem(problem2)
                .isCorrect(true)
                .isScraped(false)
                .isRetried(false)
                .answeredAt(LocalDateTime.now())   // ★ 추가
                .build();
        userProblemStatusRepository.save(status2);


        em.flush();
        em.clear();

        //when
        int total = userProblemStatusRepository.countTotalProblemsByUserId(user.getId());
        int correct = userProblemStatusRepository.countSolvedProblemsByUserId(user.getId());

        //then
        assertThat(total).isEqualTo(2);
        assertThat(correct).isEqualTo(2);
    }
}
