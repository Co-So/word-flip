package com.wordflip.util;

import com.wordflip.dto.word.WordSummary;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 词条字段清洗与测验可用性判定（<strong>已降级</strong>）。
 * <p>
 * <b>释义真相源</b>是词书专属学习卡的已发布义项。
 * 本类不再充当内容来源：仅用于
 * <ul>
 *   <li>用户导入内容生成候选学习卡前的规则清洗</li>
 *   <li>测验池 eligibility / 干扰项词性族等辅助判定</li>
 * </ul>
 * 已带合格义项的 {@link WordSummary}：{@link #normalizeSummary} / {@link #displayCn}
 * <strong>不再</strong>做正则二次清洗，避免误伤 ECDICT 释义。
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
     * 是否已有 dict 合格 primary（英汉 cn 含汉字，或英英 enGloss 非空）。
     */
    public static boolean hasDictPrimaryOk(WordSummary summary) {
        if (summary == null || summary.senses() == null || summary.senses().isEmpty()) {
            return false;
        }
        return summary.senses().stream().anyMatch(s ->
                s.primary()
                        && "ok".equalsIgnoreCase(s.quality())
                        && (
                        (s.cn() != null && hasHan(s.cn().trim()))
                                || (s.enGloss() != null && !s.enGloss().isBlank())
                )
        );
    }

    /**
     * 展示/出题用释义：优先中文；无中文则用 enGloss（WordNet）。
     */
    public static String displayPrompt(WordSummary summary) {
        if (summary == null) {
            return "";
        }
        String cn = displayCn(summary);
        if (!cn.isBlank()) {
            return cn;
        }
        if (summary.enGloss() != null && !summary.enGloss().isBlank()) {
            return summary.enGloss().trim();
        }
        if (summary.senses() != null) {
            return summary.senses().stream()
                    .filter(s -> s.primary())
                    .map(s -> s.enGloss() != null ? s.enGloss().trim() : "")
                    .filter(g -> !g.isBlank())
                    .findFirst()
                    .orElse("");
        }
        return "";
    }

    /**
     * 展示/出题用中文：dict primary 直接用顶层 cn；legacy 才走 {@link #cleanDisplayCn}。
     */
    public static String displayCn(WordSummary summary) {
        if (summary == null) {
            return "";
        }
        if (summary.senses() != null && !summary.senses().isEmpty()
                && summary.senses().stream().anyMatch(s ->
                s.primary() && "ok".equalsIgnoreCase(s.quality()))) {
            return summary.cn() != null ? summary.cn().trim() : "";
        }
        return cleanDisplayCn(summary.cn());
    }

    /**
     * 清洗展示用中文释义（仅 legacy / 导入）。
     * 去掉尾部/前导词性、英文短语头，从首个汉字起截取。
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
     * 词是否适合进入测验池（REQ-LEX-4）。
     * <p>
     * dict：须 primary + quality=ok + 含汉字；legacy：扁平 cn 清洗后判定。
     */
    public static boolean isQuizEligible(WordSummary summary) {
        if (summary == null) {
            return false;
        }
        String en = summary.en();
        if (en == null || en.isBlank()) {
            return false;
        }
        if (summary.senses() != null && !summary.senses().isEmpty()) {
            return hasDictPrimaryOk(summary);
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

    /**
     * legacy 路径规范化 cn；dict 合格 primary 原样返回（不再二次清洗）。
     */
    public static WordSummary normalizeSummary(WordSummary summary) {
        if (summary == null) {
            return null;
        }
        if (hasDictPrimaryOk(summary)) {
            return summary;
        }
        String cleaned = cleanDisplayCn(summary.cn());
        if (cleaned.equals(summary.cn() == null ? "" : summary.cn().trim())) {
            return summary;
        }
        return summary.withCn(cleaned);
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
