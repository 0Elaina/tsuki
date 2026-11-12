package com.nep.tsuki.auth.tools;

import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class PasswordHashGenerator {

    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

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
            if (passwordChars.length == 0) {
                System.err.println("密码不能为空");
                return;
            }

            String password = new String(passwordChars);
            if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
                System.err.println("密码 UTF-8 编码长度不能超过 72 字节");
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

