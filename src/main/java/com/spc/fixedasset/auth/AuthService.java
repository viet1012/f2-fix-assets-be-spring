package com.spc.fixedasset.auth;

import com.spc.fixedasset.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * Login against dbo.HSE_Patrol_Account (Pass stored as plain text). The password is compared exactly (case-sensitive,
 * never trimmed) in constant time, and is never logged, returned or put into an exception message.
 */
@Service
public class AuthService {

    public static final String INVALID_MESSAGE = "Sai tài khoản hoặc mật khẩu";
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthRepository repository;
    private final HrRepository hr;
    private final boolean updateLastLogin;
    private final boolean blockResigned;
    /** Compared against when the account does not exist, so both paths do the same work. */
    private final byte[] dummy = new byte[32];

    public AuthService(AuthRepository repository, HrRepository hr,
                       @Value("${ENABLE_LAST_LOGIN_UPDATE:false}") boolean updateLastLogin,
                       @Value("${BLOCK_RESIGNED:true}") boolean blockResigned) {
        this.repository = repository;
        this.hr = hr;
        this.updateLastLogin = updateLastLogin;
        this.blockResigned = blockResigned;
        new SecureRandom().nextBytes(dummy);
    }

    /**
     * @param account already trimmed, not blank
     * @return the user, with name/dept/section from F2_HR_Data (null when not found or the lookup fails)
     * @throws UnauthorizedException wrong account or password, or (BLOCK_RESIGNED) only a resigned HR row; same message
     */
    public CurrentUser login(String account, String password) {
        Optional<String> stored = repository.findPass(account);
        byte[] given = password.getBytes(StandardCharsets.UTF_8);
        byte[] expected = stored.map(p -> p.getBytes(StandardCharsets.UTF_8)).orElse(dummy);
        boolean ok = MessageDigest.isEqual(given, expected) && stored.isPresent();
        if (!ok) throw new UnauthorizedException(INVALID_MESSAGE);
        HrProfile profile = profile(account);
        if (blockResigned && profile != null && profile.resigned()) throw new UnauthorizedException(INVALID_MESSAGE);
        if (updateLastLogin) {
            try {
                repository.updateLastLogin(account);
            } catch (DataAccessException e) {
                log.warn("Không cập nhật được Last_Login ({})", e.getClass().getSimpleName());
            }
        }
        return profile == null
                ? new CurrentUser(account, null, null, null)
                : new CurrentUser(account, profile.name(), profile.dept(), profile.section());
    }

    /** HR profile, or null (logged once, without the account) when missing or the lookup fails: login still succeeds. */
    private HrProfile profile(String account) {
        try {
            HrProfile p = hr.findProfile(account).orElse(null);
            if (p == null) log.warn("Không tìm thấy hồ sơ F2_HR_Data cho tài khoản vừa đăng nhập");
            return p;
        } catch (DataAccessException e) {
            log.warn("Không tra được F2_HR_Data ({})", e.getClass().getSimpleName());
            return null;
        }
    }
}
