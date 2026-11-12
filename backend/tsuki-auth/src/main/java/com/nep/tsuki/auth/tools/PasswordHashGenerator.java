package com.nep.tsuki.auth.tools;

import java.io.Console;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nep.tsuki.auth.constants.AuthValidationConstants;

public final class PasswordHashGenerator {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(AuthValidationConstants.PASSWORD_REGEX);

    private PasswordHashGenerator() {
    }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("请在交互式终端中运行 PasswordHashGenerator");
            return;
        }

        char[] passwordChars = null;
        try {
            passwordChars = console.readPassword("请输入待加密的密码: ");
            if (passwordChars == null) {
                System.err.println("未读取到输入，已取消");
                return;
            }

            CharSequence password = CharBuffer.wrap(passwordChars);
            if (!PASSWORD_PATTERN.matcher(password).matches()) {
                System.err.println("密码需为 8 到 72 位可见 ASCII 字符，且不能包含空格");
                return;
            }

            PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
            System.out.println(passwordEncoder.encode(password));
        } finally {
            if (passwordChars != null) {
                Arrays.fill(passwordChars, '\0');
            }
        }
    }
}
