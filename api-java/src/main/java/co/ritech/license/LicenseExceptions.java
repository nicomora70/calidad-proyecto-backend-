package co.ritech.license;

import org.springframework.http.HttpStatus;

import co.ritech.common.BusinessException;
import co.ritech.license.LicenseModel.Module;

public final class LicenseExceptions {

    private LicenseExceptions() {}

    public static class InvalidLicenseException extends BusinessException {
        public InvalidLicenseException(String message) {
            super(HttpStatus.BAD_REQUEST, "LICENSE_INVALID", message);
        }

        public InvalidLicenseException(String message, Throwable cause) {
            super(HttpStatus.BAD_REQUEST, "LICENSE_INVALID", message, cause);
        }
    }

    /** Read-only mode: the license expired or none is installed. */
    public static class LicenseExpiredException extends BusinessException {
        public LicenseExpiredException() {
            super(
                    HttpStatus.PAYMENT_REQUIRED,
                    "LICENSE_EXPIRED",
                    "The license is expired or missing: the system is in read-only mode");
        }
    }

    public static class ModuleNotLicensedException extends BusinessException {
        public ModuleNotLicensedException(Module module) {
            super(HttpStatus.FORBIDDEN, "MODULE_NOT_LICENSED", "Module " + module + " is not included in the license");
        }
    }

    public static class LicenseUserLimitException extends BusinessException {
        public LicenseUserLimitException(int maxUsers) {
            super(
                    HttpStatus.CONFLICT,
                    "LICENSE_USER_LIMIT",
                    "The license allows at most " + maxUsers + " active users");
        }
    }
}
