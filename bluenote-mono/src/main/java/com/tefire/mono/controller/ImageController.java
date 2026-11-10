package com.tefire.mono.controller;

import com.tefire.mono.store.DataStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 现场生成占位图，避免演示依赖外网图床或本地图片素材。
 * 同一 seed 颜色稳定，封面、相册、头像各自成套。
 */
@RestController
public class ImageController {

    private static final String FONT = "Microsoft YaHei, PingFang SC, Noto Sans SC, sans-serif";

    private final DataStore store;

    public ImageController(DataStore store) {
        this.store = store;
    }

    @GetMapping(value = "/img/{kind}/{name}.svg", produces = "image/svg+xml;charset=UTF-8")
    public String svg(@PathVariable String kind, @PathVariable String name) {
        // 连续 id 的 hashCode 只差 1，乘质数拉开色相，避免整页一个颜色
        int h1 = Math.floorMod(name.hashCode() * 137, 360);
        int h2 = (h1 + 32) % 360;
        return switch (kind) {
            case "avatar" -> avatar(name, h1, h2);
            case "photo" -> photo(h1, h2);
            default -> cover(h1, h2);
        };
    }

    private String cover(int h1, int h2) {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="600" height="800" viewBox="0 0 600 800">
                  <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" stop-color="hsl(%d,44%%,78%%)"/>
                    <stop offset="1" stop-color="hsl(%d,48%%,52%%)"/>
                  </linearGradient></defs>
                  <rect width="600" height="800" fill="url(#g)"/>
                  <circle cx="470" cy="150" r="140" fill="#ffffff" fill-opacity="0.14"/>
                  <circle cx="110" cy="680" r="200" fill="#ffffff" fill-opacity="0.10"/>
                </svg>
                """.formatted(h1, h2);
    }

    private String photo(int h1, int h2) {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="800" height="1000" viewBox="0 0 800 1000">
                  <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" stop-color="hsl(%d,38%%,82%%)"/>
                    <stop offset="1" stop-color="hsl(%d,44%%,56%%)"/>
                  </linearGradient></defs>
                  <rect width="800" height="1000" fill="url(#g)"/>
                  <rect x="90" y="120" width="620" height="760" fill="#ffffff" fill-opacity="0.10" rx="16"/>
                  <circle cx="650" cy="220" r="90" fill="#ffffff" fill-opacity="0.16"/>
                </svg>
                """.formatted(h1, h2);
    }

    private String avatar(String name, int h1, int h2) {
        String ch = "青";
        try {
            Map<String, Object> user = store.users.get(Long.parseLong(name));
            if (user != null) {
                String nickname = String.valueOf(user.get("nickname"));
                ch = nickname.substring(0, 1);
            }
        } catch (RuntimeException ignored) {
            // 非用户 id 时回落到默认字
        }
        ch = ch.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 200 200">
                  <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" stop-color="hsl(%d,42%%,72%%)"/>
                    <stop offset="1" stop-color="hsl(%d,46%%,48%%)"/>
                  </linearGradient></defs>
                  <rect width="200" height="200" fill="url(#g)"/>
                  <text x="100" y="136" text-anchor="middle" font-size="92" fill="#ffffff" fill-opacity="0.92"
                        font-family="%s">%s</text>
                </svg>
                """.formatted(h1, h2, FONT, ch);
    }
}
