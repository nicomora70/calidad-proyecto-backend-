package co.ritech.user;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import co.ritech.auth.AuthDtos.ChangePasswordRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** Boundary values of the password length (29119-4): valid partition is 8..72 (BCrypt input limit). */
class PasswordPolicyTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @ParameterizedTest(name = "{0} characters -> valid={1}")
    @CsvSource({"7, false", "8, true", "72, true", "73, false"})
    void temporaryPasswordLength(int length, boolean valid) {
        var request = new UserDtos.CreateUserRequest("Ana", "ana@ritech.co", Role.INVESTOR, "a".repeat(length));
        assertThat(VALIDATOR.validate(request).isEmpty()).isEqualTo(valid);
    }

    @ParameterizedTest(name = "{0} characters -> valid={1}")
    @CsvSource({"7, false", "8, true", "72, true", "73, false"})
    void newPasswordLength(int length, boolean valid) {
        var request = new ChangePasswordRequest("current-pass-1", "b".repeat(length));
        assertThat(VALIDATOR.validate(request).isEmpty()).isEqualTo(valid);
    }
}
