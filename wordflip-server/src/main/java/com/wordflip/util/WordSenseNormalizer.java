package com.wordflip.util;

import com.wordflip.dto.word.WordSummary;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 词条字段清洗与测验可用性判定。
 * <p>
 * 一词多字段职责（须分清，禁止混用）：
 * <ul>
 *   <li>{@code wordKey} — 归一化键 {@code en.trim().toLowerCase()}，进度/选项 key</li>
 *   <li>{@code en} — 展示与默写标准答案（保留大小写）</li>
 *   <li>{@code cn} — 中文释义正文；不应再夹带词性标记或英文短语碎片</li>
 *   <li>{@code pos} — 词性（n./v./adj. 等），单独字段展示</li>
 *   <li>{@code ph} — 音标</li>
 * </ul>
 * 词书 ETL 常把词性写进 cn（如 {@code 突然地 (adv.)}），或把短语拆坏
 * （如 {@code in} → {@code favour (prep.)；为收款人}）。本工具在出题/展示前统一清洗。
 */
public final class WordSenseNormalizer {

    /** 尾部半角括号标注：词性 (n.) 或脏尾巴 (of赞成，以.)；全角（…）释义保留 */
    private static final Pattern TRAILING_POS = Pattern.compile(
            "\\s*\\([^)]*\\)\\s*$"
    );

    /** 前导英文词性缩写：n. / vt. / vi / aux / a. 等 */
    private static final Pattern LEADING_POS_ABBR = Pattern.compile(
            "^(?i)(?:n|v|vt|vi|adj|adv|prep|pron|conj|art|num|int|aux|a|ad)\\.?\\s*"
    );

    /** 前导「英文词 +(pos.)；」短语碎片 */
    private static final Pattern LEADING_ENGLISH_CHUNK = Pattern.compile(
            "^[A-Za-z][A-Za-z'\\-]*(?:\\s*\\([^)]*\\))?\\s*[；;，,]\\s*"
    );

    private WordSenseNormalizer() {
    }

    /**
     * 清洗展示用中文释义：去掉尾部/前导词性、英文短语头，从首个汉字起截取。
     */
    public static String cleanDisplayCn(String rawCn) {
        if (rawCn == null) {
            return "";
        }
        String text = rawCn.trim();
        if (text.isEmpty()) {
            return "";
        }
        // 反复剥尾部 (pos.)，兼容「… (n.)；… (v.)」多段尾标
        String prev;
        do {
            prev = text;
            text = TRAILING_POS.matcher(text).replaceFirst("").trim();
        } while (!text.equals(prev));

        // 剥前导 n. / vi 等
        text = LEADING_POS_ABBR.matcher(text).replaceFirst("").trim();
        // 再剥一次可能残留的尾部 pos（如「n. (n.)；估计」清洗后）
        text = TRAILING_POS.matcher(text).replaceFirst("").trim();

        // 剥「favour (prep.)；」类英文头
        while (true) {
            Matcher m = LEADING_ENGLISH_CHUNK.matcher(text);
            if (!m.find()) {
                break;
            }
            text = text.substring(m.end()).trim();
            text = TRAILING_POS.matcher(text).replaceFirst("").trim();
        }

        int han = indexOfFirstHan(text);
        if (han > 0) {
            text = text.substring(han).trim();
        }
        text = TRAILING_POS.matcher(text).replaceFirst("").trim();
        // 压缩空白
        text = text.replaceAll("\\s{2,}", " ").trim();
        return text;
    }

    /** 是否含汉字（测验中文题干/选项最低要求） */
    public static boolean hasHan(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return indexOfFirstHan(text) >= 0;
    }

    /**
     * 词是否适合进入测验池：须有英文与可读中文释义，且释义不是 wordKey 本身。
     */
    public static boolean isQuizEligible(WordSummary summary) {
        if (summary == null) {
            return false;
        }
        String en = summary.en();
        if (en == null || en.isBlank()) {
            return false;
        }
        String cn = cleanDisplayCn(summary.cn());
        if (!hasHan(cn)) {
            return false;
        }
        String key = summary.wordKey() != null
                ? summary.wordKey()
                : WordKeyUtil.normalize(en);
        if (cn.equalsIgnoreCase(key) || cn.equalsIgnoreCase(en.trim())) {
            return false;
        }
        // 虚词头 + 仍像英文碎片的释义，排除出题（短语拆坏）
        if (isLikelyFunctionWord(key) && looksLikeEnglishDebris(summary.cn())) {
            return false;
        }
        return true;
    }

    /** 返回清洗后的 WordSummary（cn 已规范化；其余字段原样） */
    public static WordSummary normalizeSummary(WordSummary summary) {
        if (summary == null) {
            return null;
        }
        String cleaned = cleanDisplayCn(summary.cn());
        if (cleaned.equals(summary.cn() == null ? "" : summary.cn().trim())) {
            return summary;
        }
        return new WordSummary(
                summary.wordKey(),
                summary.en(),
                cleaned,
                summary.pos(),
                summary.ph()
        );
    }

    /**
     * 词性族：用于干扰项优先同词性（教育测量学：选项语法类别一致）。
     * 归一到 n / v / adj / adv / prep / other。
     */
    public static String posFamily(String pos) {
        if (pos == null || pos.isBlank()) {
            return "other";
        }
        String p = pos.toLowerCase(Locale.ROOT).replace(" ", "");
        if (p.contains("prep")) {
            return "prep";
        }
        if (p.contains("adv")) {
            return "adv";
        }
        if (p.contains("adj") || p.equals("a.") || p.startsWith("a&") || p.contains("a.")) {
            return "adj";
        }
        if (p.contains("pron") || p.contains("conj") || p.contains("art") || p.contains("aux")) {
            return "other";
        }
        if (p.contains("v") || p.contains("vi") || p.contains("vt")) {
            return "v";
        }
        if (p.contains("n")) {
            return "n";
        }
        return "other";
    }

    /** 选项 label 归一（去空白、小写）便于去重 */
    public static String labelKey(String label) {
        if (label == null) {
            return "";
        }
        return label.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static int indexOfFirstHan(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                return i;
            }
            i += Character.charCount(cp);
        }
        return -1;
    }

    private static boolean isLikelyFunctionWord(String wordKey) {
        if (wordKey == null) {
            return false;
        }
        return switch (wordKey) {
            case "a", "an", "the", "of", "to", "in", "on", "at", "for", "by",
                 "with", "as", "or", "and", "is", "be", "are", "was", "were",
                 "do", "does", "did", "have", "has", "had", "will", "would",
                 "can", "could", "may", "might", "must", "shall", "should",
                 "out", "up", "off", "go", "get" -> true;
            default -> wordKey.length() <= 2;
        };
    }

    /** 原始 cn 仍以拉丁字母开头 → 多为短语拆坏残留 */
    private static boolean looksLikeEnglishDebris(String rawCn) {
        if (rawCn == null || rawCn.isBlank()) {
            return true;
        }
        String t = rawCn.trim();
        char c = t.charAt(0);
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }
}
